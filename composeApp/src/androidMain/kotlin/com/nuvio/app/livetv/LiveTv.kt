package com.nuvio.app.livetv

import android.content.Context
import com.nuvio.app.livetv.data.EpgDatabase
import com.nuvio.app.livetv.data.LiveTvChannelStore
import com.nuvio.app.livetv.data.LiveTvPreferences
import com.nuvio.app.livetv.data.LiveTvProfile
import com.nuvio.app.livetv.data.LiveTvRepository
import com.nuvio.app.livetv.model.LiveUserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The shared Live TV objects, made once from the application context. */
class LiveTv private constructor(context: Context) {
    private val appContext = context.applicationContext
    val profile = LiveTvProfile()
    val prefs = LiveTvPreferences(context, profile)
    val guideDb = EpgDatabase(context)
    val repository = LiveTvRepository(context, prefs, guideDb)
    val store = LiveTvChannelStore(repository, prefs, context)
    val onDemandDb by lazy { com.nuvio.app.livetv.ondemand.OnDemandDatabase(appContext) }
    val onDemand by lazy { com.nuvio.app.livetv.ondemand.OnDemandRepository(onDemandDb, prefs).also { it.start() } }
    val sports by lazy { com.nuvio.app.livetv.sports.SportsRepository(appContext, prefs, repository, store, guideDb).also { it.start() } }
    val calendar by lazy { com.nuvio.app.livetv.calendar.MobileCalendar(appContext) }
    private val driveSyncLazy = lazy { com.nuvio.app.livetv.sync.LiveTvDriveSync(appContext, prefs, repository).also { it.start() } }
    val driveSync by driveSyncLazy

    /**
     * Forgets everything Live TV was set up with on this device: the playlists and guides (and the channels,
     * programmes and On Demand titles made from them), groups, edits, favorites, PIN, reminders and the
     * Google Drive link. Recordings you made are left alone. Used when the Nuvio account is signed out.
     */
    suspend fun clearAll() {
        runCatching { driveSyncIfStarted()?.disconnect() }
        runCatching { com.nuvio.app.livetv.sync.forgetDriveSyncConnection(appContext) }
        runCatching { prefs.playlists.first().forEach { repository.removePlaylist(it.id) } }
        runCatching { prefs.epgSources.first().forEach { repository.removeEpg(it.id) } }
        runCatching { prefs.clearAll() }
        runCatching { guideDb.clear() }
        // With no playlists left, an On Demand refresh drops every title it held.
        runCatching { onDemand.refresh(force = true) }
        runCatching {
            java.io.File(appContext.filesDir, "livetv").listFiles()?.filter { it.name != "recording_schedules.json" }?.forEach { it.deleteRecursively() }
            java.io.File(appContext.filesDir, "sports").deleteRecursively()
            java.io.File(appContext.filesDir, "calendar").deleteRecursively()
            java.io.File(appContext.cacheDir, "logo_lists").deleteRecursively()
        }
        runCatching {
            java.io.File(appContext.applicationInfo.dataDir, "shared_prefs").listFiles()
                ?.filter { it.name.startsWith("livetv_") && !it.name.startsWith("livetv_drive") }
                ?.forEach { appContext.getSharedPreferences(it.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().apply() }
        }
    }

    /** Drive sync if it has been started this run (it isn't started just to be switched off). */
    fun driveSyncIfStarted() = if (driveSyncLazy.isInitialized()) driveSyncLazy.value else null
    val recorder by lazy { com.nuvio.app.livetv.record.LiveTvRecorder(appContext, prefs) }
    val scheduler by lazy { com.nuvio.app.livetv.record.LiveTvRecordingScheduler(appContext, recorder, repository, prefs) }

    /** Your hidden channels and groups, favourites and names, always at hand. */
    val userState: StateFlow<LiveUserState> =
        prefs.userState.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, LiveUserState())

    /** True when an Xtream provider with movies is set up and "On Demand in streams" is on. */
    val onDemandReady: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(prefs.playlists, prefs.settings) { playlists, s ->
            s.onDemandInStreams && playlists.any { it.enabled && it.importVod && it.xtreamServer.isNotBlank() }
        }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, false)

    init {
        // A Google account that's connected: check Drive for a newer setup, and back up changes.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { kotlinx.coroutines.delay(5_000); runCatching { driveSync } }
        // Scheduled recordings get their alarms set as soon as Live TV is touched.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { scheduler.start() } }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            userState.collect { state ->
                prefs.pruneReminders(System.currentTimeMillis())
                com.nuvio.app.livetv.reminders.LiveTvReminders.scheduleAll(context.applicationContext, state.reminders)
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            prefs.pictureShapes.collect { com.nuvio.app.livetv.player.LivePictureShape.shapes = it }
        }
        // Stalker portals' addresses and MACs, for turning a saved channel link into a playable one.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            prefs.playlists.collect { com.nuvio.app.livetv.data.StalkerLinks.configure(it) }
        }
        // Teams you follow: a reminder before each of their games, even with the app closed.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            com.nuvio.app.livetv.sports.SportsAuto.run(appContext, this@LiveTv)
        }
        // A team you follow scored: tell you, wherever you are in the app.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            com.nuvio.app.livetv.sports.SportsLive.alerts.collect { a ->
                if (a != null) {
                    val sides = a.event.sides
                    com.nuvio.app.livetv.reminders.LiveTvReminders.alert(
                        appContext, "${a.team.name} scored",
                        sides.joinToString("  \u2013  ") { "${it.team.abbr.ifBlank { it.team.name }} ${it.score.orEmpty()}" } +
                            "  \u00B7  ${a.league.name}",
                    )
                }
            }
        }
        // The few settings that live outside the screens follow the stored ones.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            prefs.settings.collect {
                com.nuvio.app.livetv.player.LiveTvReconnect.enabled = it.autoReconnect
                com.nuvio.app.livetv.player.LivePlayback.follow(it)
                com.nuvio.app.livetv.ui.LiveTvDisplay.use24Hour = it.use24HourClock
                com.nuvio.app.livetv.ui.LiveTvDisplay.guideSize = it.guideSize
                com.nuvio.app.livetv.ui.LiveTvDisplay.showTimes = it.showProgramTimes
                com.nuvio.app.livetv.ui.LiveTvDisplay.showEpisodes = it.showEpisodeNumbers
                com.nuvio.app.livetv.ui.LiveTvDisplay.showNowLine = it.showNowLine
            }
        }
    }

    companion object {
        @Volatile private var instance: LiveTv? = null

        /** The shared Live TV objects if they exist yet, without making them. */
        fun peek(): LiveTv? = instance

        fun get(context: Context): LiveTv =
            instance ?: synchronized(this) {
                instance ?: LiveTv(context.applicationContext).also { instance = it }
            }
    }
}
