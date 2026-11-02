package com.nuvio.app.livetv.player

/**
 * Keeps a Live TV channel going when its stream drops: the player retries a few times, a little
 * longer apart each time, before it tells you the channel can't be played. Armed for the channel
 * that was just opened; anything else (a movie, a YouTube video) fails straight away as before.
 */
object LiveTvReconnect {
    const val MAX_ATTEMPTS = 6

    @Volatile private var armedUrl: String? = null
    @Volatile private var attempts = 0

    /** Settings > Live TV > Reconnect when a channel drops. */
    @Volatile var enabled = true

    /** The Live TV channel on now, so its picture shape can be applied. */
    @Volatile var armedChannel: String? = null
        private set

    @Volatile private var liveUrl: String? = null

    fun arm(url: String, channelKey: String? = null) {
        armedUrl = if (enabled) url else null
        liveUrl = url
        armedChannel = channelKey
        attempts = 0
    }

    /** True while [sourceUrl] is the Live TV channel that was just opened (whether or not reconnecting is on). */
    fun isLive(sourceUrl: String?): Boolean = sourceUrl != null && sourceUrl == liveUrl

    fun disarm() {
        armedUrl = null
        attempts = 0
    }

    /** True while [sourceUrl] is the armed channel and it still has retries left. */
    fun canRetry(sourceUrl: String?): Boolean = sourceUrl != null && sourceUrl == armedUrl && attempts < MAX_ATTEMPTS

    /** Counts one more try and says how long to wait first, in milliseconds (1, 2, 4, 8, 8, 8 seconds). */
    fun nextDelayMs(): Long {
        attempts += 1
        return minOf(8_000L, 500L shl attempts.coerceAtMost(4))
    }

    /** The stream is playing again: the next drop starts from a fresh count. */
    fun playing() {
        attempts = 0
    }
}
