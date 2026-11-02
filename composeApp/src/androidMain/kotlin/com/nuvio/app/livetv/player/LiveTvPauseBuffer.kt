package com.nuvio.app.livetv.player

import android.content.Context
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.ui.liveTvRequest

/**
 * Pause and rewind live TV. A channel is recorded to the device: from when you pause it, or, with
 * "Rewind anytime", all the time while you watch (the last few minutes are kept). Playing again
 * carries on from where you paused, behind live, with the usual seek bar; Rewind jumps back a
 * little. "Go live" drops the recording. Nothing is kept once you go live, change channel or the
 * app starts again.
 */
object LiveTvPauseBuffer {
    private var appContext: Context? = null
    private var timeshift: LiveTvTimeshift? = null
    private var bufferKey: String? = null
    private var sessionUri: String? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    @Volatile private var rolling = false

    const val PREFIX = "ts:"
    private const val REWIND_MS = 15_000L

    private fun shift(context: Context): LiveTvTimeshift =
        timeshift ?: LiveTvTimeshift(context.applicationContext).also {
            timeshift = it
            it.cleanLeftovers(listOf(java.io.File(context.cacheDir, "timeshift")))
        }

    /** Wired from the app's start-up. */
    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        LiveTvHost.timeshiftToggle = { key, playing -> toggle(app, key, playing) }
        LiveTvHost.goLive = { bufferKey -> goLive(app, bufferKey) }
        LiveTvHost.rewind = { key -> rewind(app, key) }
        LiveTvHost.rewindAvailable = { rolling }
    }

    private fun settings(context: Context) =
        kotlinx.coroutines.runBlocking { LiveTv.get(context).prefs.currentSettings() }

    private fun startFor(context: Context, key: String, keepMinutes: Int): Boolean {
        val live = LiveTv.get(context)
        val channel = live.store.displayChannels.value.firstOrNull { it.key == key } ?: return false
        if (channel.drm != null) return false
        val ts = shift(context)
        if (!ts.hasRoomFor(keepMinutes)) return false
        val session = ts.start(channel.url, channel.headers, keepMinutes = keepMinutes, label = "Pause buffer") ?: return false
        bufferKey = key
        startedAt = session.startedAtMs
        sessionUri = session.playUri.toString()
        return true
    }

    private fun stopBuffer() {
        timeshift?.stop()
        bufferKey = null
        sessionUri = null
        rolling = false
    }

    /**
     * A live channel has just opened. With "Rewind anytime" on, it starts being kept from now,
     * so there's something to rewind in (and a pause is free: it's already recording).
     */
    fun onLiveOpened(channelKey: String) {
        val context = appContext ?: return
        val s = settings(context)
        if (!s.timeshiftEnabled || !s.rewindAlways) { rolling = false; return }
        if (bufferKey == channelKey && timeshift?.isRecording == true) { rolling = true; return }
        stopBuffer()
        rolling = startFor(context, channelKey, s.timeshiftMinutes.coerceAtLeast(5))
    }

    private fun toggle(context: Context, key: String, playing: Boolean): Boolean {
        val s = settings(context)
        if (!s.timeshiftEnabled) return false
        if (playing) {
            // About to pause: keep recording what's coming, from now (a rolling buffer already is).
            pausedAt = System.currentTimeMillis()
            if (bufferKey == key && timeshift?.isRecording == true) return false
            stopBuffer()
            if (!startFor(context, key, s.timeshiftMinutes.coerceAtLeast(5))) return false
            pausedAt = System.currentTimeMillis()
            return false // the player pauses as usual
        }
        // About to play again.
        val uri = sessionUri
        val ts = timeshift
        if (bufferKey != key || uri == null || ts == null) return false
        val behind = System.currentTimeMillis() - pausedAt
        if (behind < 3_000L) {
            // A moment's pause: no need for the buffer, unless it's rolling anyway.
            if (!rolling) stopBuffer()
            return false
        }
        return openBuffer(context, key, (pausedAt - startedAt - ts.trimmedMs).coerceAtLeast(0L))
    }

    private fun openBuffer(context: Context, key: String, positionMs: Long): Boolean {
        val uri = sessionUri ?: return false
        val channel = LiveTv.get(context).store.displayChannels.value.firstOrNull { it.key == key } ?: return false
        LiveTvHost.openChannel?.invoke(
            LiveTvPlayRequest(
                url = uri,
                title = channel.name,
                poster = channel.logo,
                channelKey = PREFIX + key,
                subtitle = "Behind live",
                catchup = true,
                startPositionMs = positionMs,
            )
        )
        return true
    }

    private fun rewind(context: Context, key: String): Boolean {
        val ts = timeshift ?: return false
        if (bufferKey != key || sessionUri == null) return false
        val position = (System.currentTimeMillis() - startedAt - ts.trimmedMs - REWIND_MS).coerceAtLeast(0L)
        return openBuffer(context, key, position)
    }

    private fun goLive(context: Context, bufferKey: String) {
        val key = bufferKey.removePrefix(PREFIX)
        val live = LiveTv.get(context)
        stopBuffer()
        val channel = live.store.displayChannels.value.firstOrNull { it.key == key } ?: return
        LiveTvReconnect.arm(channel.url, channel.key)
        LiveTvHost.openChannel?.invoke(liveTvRequest(channel, live.repository.programs.value[channel.key], System.currentTimeMillis()))
    }

    /** Drops any buffer that belongs to a channel other than [channelKey] (called when a channel opens). */
    fun dropIfOtherChannel(channelKey: String) {
        val key = channelKey.removePrefix(PREFIX)
        if (bufferKey != null && bufferKey != key) stopBuffer()
    }
}
