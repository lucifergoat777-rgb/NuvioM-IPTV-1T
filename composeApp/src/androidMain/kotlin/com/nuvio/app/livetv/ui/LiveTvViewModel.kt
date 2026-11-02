package com.nuvio.app.livetv.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.data.CatchupUrlBuilder
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.EpgSource
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.model.LiveTvSettings
import com.nuvio.app.livetv.model.LiveUserState
import com.nuvio.app.livetv.player.PictureShape
import com.nuvio.app.livetv.model.PlaylistSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row of group chips: its id, what to call it, and how many channels it holds. */
data class LiveGroup(val id: String, val title: String, val count: Int)

private val XTREAM_LINK = Regex("""/(?:live/)?[^/]+/[^/]+/\d+(\.[a-z0-9]+)?(\?.*)?$""", RegexOption.IGNORE_CASE)

class LiveTvViewModel(context: Context) : ViewModel() {
    private val live = LiveTv.get(context)
    private val repository = live.repository
    private val prefs = live.prefs

    val channels: StateFlow<List<LiveChannel>> = live.store.displayChannels
    val programs = repository.programs
    val status = repository.status
    val guideGeneration = repository.guideGeneration
    val user: StateFlow<LiveUserState> = prefs.userState.stateIn(viewModelScope, SharingStarted.Eagerly, LiveUserState())
    val playlists: StateFlow<List<PlaylistSource>> = prefs.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val epgSources: StateFlow<List<EpgSource>> = prefs.epgSources.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<LiveTvSettings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, LiveTvSettings())
    val pictureShapes: StateFlow<Map<String, String>> = prefs.pictureShapes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Remembers [key] as the channel you last watched, for Recent and the Last channel button. */
    fun recordWatched(key: String) {
        viewModelScope.launch { prefs.recordWatched(key) }
    }

    private val appContext = context.applicationContext

    /** Turns a reminder for [program] on [channel] on or off. */
    fun setReminder(channel: LiveChannel, program: EpgProgram, on: Boolean) {
        val reminder = com.nuvio.app.livetv.model.Reminder(channel.key, channel.name, program.title, program.startMs)
        viewModelScope.launch {
            if (on) {
                prefs.addReminder(reminder)
                com.nuvio.app.livetv.reminders.LiveTvReminders.schedule(appContext, reminder)
            } else {
                prefs.removeReminder(channel.key, program.startMs)
                com.nuvio.app.livetv.reminders.LiveTvReminders.cancel(appContext, reminder)
            }
        }
    }

    fun setLastGroup(id: String) {
        viewModelScope.launch { prefs.setLastGroup(id) }
    }

    fun saveVodGroups(hidden: Set<String>, order: List<String>, names: Map<String, String>) { viewModelScope.launch { prefs.saveVodGroups(hidden, order, names) } }

    fun setChannelLogo(key: String, url: String?) { viewModelScope.launch { prefs.setChannelLogo(key, url) } }

    fun renameChannel(key: String, name: String?) {
        viewModelScope.launch { prefs.setChannelName(key, name) }
    }

    fun setChannelNumber(key: String, number: Int?) {
        viewModelScope.launch { prefs.setChannelNumber(key, number) }
    }

    fun setChannelHidden(key: String, hidden: Boolean) {
        viewModelScope.launch { prefs.setChannelHidden(key, hidden) }
    }

    fun setGroupHidden(id: String, hidden: Boolean) {
        viewModelScope.launch { prefs.setGroupHidden(id, hidden) }
    }

    fun renameGroup(id: String, name: String?) {
        viewModelScope.launch { prefs.setGroupName(id, name) }
    }

    fun moveGroup(id: String, delta: Int, order: List<String>) {
        viewModelScope.launch { prefs.moveGroup(id, delta, order) }
    }

    fun setGroupLocked(id: String, locked: Boolean) {
        viewModelScope.launch { prefs.setGroupLocked(id, locked) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch { prefs.updateSettings { it.copy(parentalPin = pin) } }
    }

    fun setPictureShape(key: String, shape: PictureShape) {
        viewModelScope.launch { prefs.setPictureShape(key, shape) }
    }

    fun resetEdits() {
        viewModelScope.launch { prefs.resetChannelEdits(); prefs.resetGroupEdits() }
    }

    fun showAllHidden() {
        viewModelScope.launch { prefs.clearHiddenChannels(); prefs.clearHiddenGroups() }
    }

    fun updateSettings(transform: (LiveTvSettings) -> LiveTvSettings) {
        viewModelScope.launch { prefs.updateSettings(transform) }
    }

    val onDemand get() = live.onDemand
    fun setVodSort(uid: String, sort: String?) { viewModelScope.launch { prefs.setVodSort(uid, sort) } }
    fun lockVodCategory(uid: String, locked: Boolean) { viewModelScope.launch { prefs.setGroupLocked("vod:$uid", locked) } }

    val accounts: StateFlow<Map<String, com.nuvio.app.livetv.model.PlaylistAccount>> =
        prefs.playlistAccounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun updatePlaylist(p: PlaylistSource) { viewModelScope.launch { repository.updatePlaylist(p); if (p.isXtream) live.onDemand.refreshNow(p.id) } }

    /** Moves [key] within [groupId] (a playlist group or one of your own), given the order now shown. */
    fun moveChannel(groupId: String, key: String, delta: Int, current: List<String>) {
        viewModelScope.launch {
            val list = current.toMutableList()
            val i = list.indexOf(key)
            if (i < 0) return@launch
            list.removeAt(i)
            list.add((i + delta).coerceIn(0, list.size), key)
            if (groupId.startsWith(com.nuvio.app.livetv.model.ChannelGroup.CUSTOM_PREFIX)) prefs.setCustomGroupOrder(groupId, list)
            else prefs.setChannelOrder(groupId, list)
        }
    }

    fun resetOrder() { viewModelScope.launch { prefs.resetChannelOrder() } }

    fun createGroup(name: String, thenAdd: String? = null) {
        viewModelScope.launch {
            val id = prefs.createCustomGroup(name)
            if (thenAdd != null) prefs.addToCustomGroup(id, thenAdd)
        }
    }

    fun deleteGroup(id: String) { viewModelScope.launch { prefs.deleteCustomGroup(id) } }
    fun addToGroup(groupId: String, key: String) { viewModelScope.launch { prefs.addToCustomGroup(groupId, key) } }
    fun removeFromGroup(groupId: String, key: String) {
        viewModelScope.launch {
            if (groupId.startsWith(com.nuvio.app.livetv.model.ChannelGroup.CUSTOM_PREFIX)) prefs.removeFromCustomGroup(groupId, key)
            else prefs.removeChannelCopy(groupId, key)
        }
    }
    fun copyToGroup(groupId: String, key: String) { viewModelScope.launch { prefs.copyChannel(groupId, key) } }

    val epgChannels get() = repository.epgSources
    fun assignGuide(key: String, assignment: com.nuvio.app.livetv.model.EpgAssignment?) {
        viewModelScope.launch { prefs.setEpgOverride(key, assignment) }
    }
    val calendar get() = live.calendar
    val sports get() = live.sports

    /** Schedules [ev] on [channel]'s channel to record, from 5 minutes before it starts. Returns what to tell the user. */
    fun recordSportsEvent(ev: com.nuvio.app.livetv.sports.SportsEvent, channel: LiveChannel): String {
        val league = com.nuvio.app.livetv.sports.SportsCatalog.league(ev.leagueId)
        val minutes = league?.minutes ?: 180
        val program = EpgProgram(
            startMs = ev.startMs - 5 * 60_000L,
            stopMs = ev.endMs ?: (ev.startMs + minutes * 60_000L),
            title = ev.title,
            description = listOfNotNull(league?.name, ev.subtitle).joinToString(" \u00B7 ").ifBlank { null },
        )
        return live.scheduler.add(channel, program)
    }

    fun hideVodCategory(uid: String) {
        viewModelScope.launch { prefs.updateVodHiddenCategories(emptySet(), setOf(uid)) }
    }

    fun showAllVodCategories() {
        viewModelScope.launch { prefs.clearVodHiddenCategories() }
    }

    val seriesRules = live.scheduler.series
    fun addSeries(channel: LiveChannel, program: EpgProgram): String = live.scheduler.addSeries(channel, program)
    fun removeSeries(id: String) = live.scheduler.removeSeries(id)
    fun setKeepLast(id: String, n: Int) = live.scheduler.setKeep(id, n)
    fun hasSeries(channelKey: String, title: String) = live.scheduler.seriesFor(channelKey, title) != null

    /** Where you got to in a recording (position, length), or null when you haven't started it. */
    fun resumeOf(rec: com.nuvio.app.livetv.record.LiveTvRecording): Pair<Long, Long>? =
        com.nuvio.app.livetv.record.RecordingResume.load(appContext, com.nuvio.app.livetv.record.RecordingResume.contentIdFor(rec.id))

    val recordings = live.recorder.recordings
    val schedules = live.scheduler.schedules
    fun freeBytes(): Long = live.recorder.freeBytes()

    /** Records [channel] from now for [minutes]. */
    fun recordNow(channel: LiveChannel, title: String, minutes: Int) {
        val now = System.currentTimeMillis()
        val stop = now + minutes * 60_000L
        live.recorder.start(channel, channel.url, title, null, null, now, stop, stop)
    }

    /** Sets a show from the guide to record (or takes it off). Returns what to tell the user. */
    fun scheduleProgram(channel: LiveChannel, program: EpgProgram): String = live.scheduler.add(channel, program)

    fun isScheduled(channelKey: String, program: EpgProgram): Boolean = live.scheduler.isScheduled(channelKey, program)

    fun cancelProgram(channelKey: String, startMs: Long) = live.scheduler.cancel(channelKey, startMs)

    fun scheduleManual(channel: LiveChannel, title: String, startMs: Long, stopMs: Long): String =
        live.scheduler.addManual(channel, title, startMs, stopMs)

    fun cancelSchedule(id: String) = live.scheduler.cancel(id)
    fun stopRecording(id: String) = live.recorder.stop(id)
    fun deleteRecording(id: String) = live.recorder.delete(id)

    /** What the player needs to play [rec]: recordings are served to the player from this device. */
    fun playRequest(rec: com.nuvio.app.livetv.record.LiveTvRecording) = LiveTvPlayRequest(
        url = live.recorder.playUrl(rec),
        title = rec.title,
        poster = rec.channelLogo,
        channelKey = "recording:" + rec.id,
        subtitle = rec.channelName,
        startPositionMs = resumeOf(rec)?.first?.takeIf { it > 5_000L },
    )

    /** The current time, moved on every half minute so the "now" and progress bars stay right. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    init {
        repository.ensureLoaded()
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(30_000)
            }
        }
        // The guide for what's on now and the next hours; asked again every few minutes.
        viewModelScope.launch {
            while (true) {
                val t = System.currentTimeMillis()
                runCatching { repository.ensureRange(t - 30 * 60_000L, t + 6 * 3_600_000L) }
                delay(5 * 60_000L)
            }
        }
    }

    /** Reads the days of listings for the channels being shown in the guide. */
    fun loadGuide(keys: List<String>) = repository.ensureFullRange(keys)

    /**
     * The link that replays [program] from the start, or null when this channel or time has no archive.
     * Asks an Xtream server for its time zone first when it isn't known (its archive links use server time).
     */
    suspend fun catchupRequest(channel: LiveChannel, program: EpgProgram): LiveTvPlayRequest? {
        val catchup = channel.catchup ?: return null
        val now = System.currentTimeMillis()
        if (!CatchupUrlBuilder.isAvailable(catchup, program.startMs, now)) return null
        val settings = prefs.currentSettings()
        val playlist = prefs.currentPlaylists().firstOrNull { it.id == channel.sourceId }
        var zone = playlist?.serverTimezone?.takeIf { it.isNotBlank() }
        if (zone == null && XTREAM_LINK.containsMatchIn(channel.url)) {
            zone = kotlinx.coroutines.withTimeoutOrNull(4_000) { repository.learnServerZone(channel) }
        }
        // The guide's time shift is taken back off (the archive goes by the guide's own times), then
        // the catch-up shift added, for providers whose archive is on another clock.
        val shift = settings.epgOffsetMinutes * 60_000L - settings.catchupShiftMinutes * 60_000L
        // Ask for more than the show: up to 3 hours past its end, but not past what has aired.
        val windowStop = maxOf(program.stopMs, minOf(now - 30_000, program.stopMs + 3 * 3_600_000L))
        val noHlsSince = playlist?.catchupNoHlsSince ?: 0L
        val url = CatchupUrlBuilder.build(
            channel.url, catchup, program.startMs - shift, windowStop - shift, now,
            preferHls = settings.catchupPreferHls && now - noHlsSince > 7 * 24 * 3_600_000L,
            serverTimezone = zone,
        ) ?: return null
        return LiveTvPlayRequest(
            url = url,
            title = "${channel.name} · ${program.title}",
            poster = channel.logo,
            channelKey = channel.key,
            headers = channel.headers,
            subtitle = program.title,
            catchup = true,
        )
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch { prefs.toggleFavorite(key) }
    }

    fun refresh() {
        repository.refreshAll(force = true)
        // Movies and series too (they otherwise update once a day).
        live.onDemand.refreshNow()
    }

    fun addM3u(name: String, url: String) {
        viewModelScope.launch { repository.addPlaylist(name, url) }
    }

    fun addXtream(name: String, server: String, username: String, password: String) {
        viewModelScope.launch { repository.addXtream(name, server, username, password) }
    }

    fun addStalker(name: String, portal: String, mac: String) {
        viewModelScope.launch { repository.addStalker(name, portal, mac) }
    }

    fun addEpg(name: String, url: String) {
        viewModelScope.launch { repository.addEpg(name, url) }
    }

    fun removeEpg(id: String) {
        viewModelScope.launch { repository.removeEpg(id) }
    }

    fun setEpgEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEpgEnabled(id, enabled) }
    }

    fun removePlaylist(id: String) {
        viewModelScope.launch { repository.removePlaylist(id) }
    }

    fun setPlaylistEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setPlaylistEnabled(id, enabled) }
    }
}
