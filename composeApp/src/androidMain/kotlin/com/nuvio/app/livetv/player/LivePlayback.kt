package com.nuvio.app.livetv.player

import android.content.Context
import android.widget.Toast
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.data.LiveTvRepository
import com.nuvio.app.livetv.model.LiveTvSettings
import com.nuvio.app.livetv.ui.liveTvRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the player needs from the Live TV settings, kept current and readable from any thread. */
object LivePlayback {
    @Volatile var bufferSize = "normal"
    @Volatile var passthrough = true
    @Volatile var frameRateMode = "OFF"
    @Volatile var captionsByDefault = false
    @Volatile var autoBackup = true
    @Volatile var showQualityBadges = true

    /** Caption and audio choices are made once per channel: the generation they were made for. */
    @Volatile var captionGen = -1
    @Volatile var audioGen = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun follow(s: LiveTvSettings) {
        bufferSize = s.bufferSize
        passthrough = s.audioPassthrough
        frameRateMode = s.frameRateMode
        captionsByDefault = s.captionsByDefault
        autoBackup = s.autoBackupChannel
        showQualityBadges = s.showQualityBadges
    }

    /** How much to buffer for live TV: small changes channels fastest, bigger rides out a poor connection. */
    class Buffer(val minMs: Int, val maxMs: Int, val playMs: Int, val rebufferMs: Int)

    fun buffer(): Buffer = when (bufferSize) {
        "small" -> Buffer(2_000, 10_000, 1_000, 2_000)
        "large" -> Buffer(15_000, 40_000, 3_000, 6_000)
        "xlarge" -> Buffer(30_000, 60_000, 5_000, 10_000)
        else -> Buffer(5_000, 20_000, 1_500, 3_000)
    }

    /** "4K", "FHD", "HD" or "SD" for a picture size (by width too, since films are cropped wide). */
    fun qualityLabel(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        return when {
            height >= 1600 || width >= 3200 -> "4K"
            height >= 1000 || width >= 1800 -> "FHD"
            height >= 700 || width >= 1200 -> "HD"
            else -> "SD"
        }
    }

    /** Remembers the picture quality of the channel playing, for the badges next to channels. */
    fun recordQuality(context: Context, width: Int, height: Int) {
        val key = LiveTvReconnect.armedChannel ?: return
        val label = qualityLabel(width, height) ?: return
        val live = LiveTv.get(context)
        if (live.userState.value.channelQuality[key] == label) return
        scope.launch { runCatching { live.prefs.setChannelQuality(key, label) } }
    }

    /** What a chosen audio track is, kept per channel ("language|label|channels"). */
    fun audioKey(language: String?, label: String?, channels: Int): String =
        "${language.orEmpty()}|${label.orEmpty()}|$channels"

    fun saveAudio(context: Context, language: String?, label: String?, channels: Int) {
        val key = LiveTvReconnect.armedChannel ?: return
        val live = LiveTv.get(context)
        scope.launch { runCatching { live.prefs.setChannelAudio(key, audioKey(language, label, channels)) } }
    }

    fun savedAudio(context: Context): String? {
        val key = LiveTvReconnect.armedChannel ?: return null
        return LiveTv.get(context).userState.value.channelAudio[key]
    }
}

/** A channel that won't play: another channel with the same name (from another group or playlist) takes over. */
object LiveTvBackup {
    private val tried = HashSet<String>()

    fun reset() {
        synchronized(tried) { tried.clear() }
    }

    /** Opens the next channel with the same name. Returns false when there isn't one (or it's turned off). */
    fun tryBackup(context: Context, url: String?): Boolean {
        if (!LivePlayback.autoBackup || url == null) return false
        val live = LiveTv.get(context)
        val all = live.store.displayChannels.value
        val current = all.firstOrNull { it.url == url } ?: return false
        val name = LiveTvRepository.normalize(current.name)
        val next = synchronized(tried) {
            tried += current.key
            all.firstOrNull { it.key !in tried && it.drm == null && LiveTvRepository.normalize(it.name) == name }
                ?.also { tried += it.key }
        } ?: return false
        LiveTvReconnect.arm(next.url, next.key)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, "${current.name} won't play. Trying another copy.", Toast.LENGTH_SHORT).show()
            LiveTvHost.openChannel?.invoke(liveTvRequest(next, live.repository.programs.value[next.key], System.currentTimeMillis()))
        }
        return true
    }
}
