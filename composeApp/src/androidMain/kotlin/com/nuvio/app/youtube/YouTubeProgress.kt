package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Watch progress for YouTube videos.
 *
 * Nuvio's player calls [record] as a video plays. That keeps a local copy (for the bar on each
 * card, signed in or not) and, when signed in, tells YouTube too, so the video shows up in your
 * YouTube history and carries its watched bar on your other devices.
 */
object YouTubeProgress {
    private const val TAG = "YouTubeProgress"
    private const val PREFS = "youtube_progress"
    const val ID_PREFIX = "youtube:"

    /** Each report to YouTube is a few requests; once every this often is plenty. */
    private const val REPORT_EVERY_MS = 20_000L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "yt-progress").apply { isDaemon = true } }
    private val lastReported = ConcurrentHashMap<String, Long>()

    fun isYouTubeId(contentId: String?) = contentId?.startsWith(ID_PREFIX) == true

    /** Called by the player with where it's got to. Safe to call often. */
    fun record(context: Context, contentId: String?, positionMs: Long, durationMs: Long) {
        val videoId = contentId?.removePrefix(ID_PREFIX)?.takeIf { it.isNotBlank() } ?: return
        if (positionMs < 1000) return
        val app = context.applicationContext
        runCatching {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(videoId, "$positionMs,$durationMs").apply()
        }

        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastReported[videoId] ?: 0L
        // Always report the end of a video; otherwise keep to the interval.
        val finished = durationMs > 0 && positionMs >= durationMs - 5_000L
        if (!finished && now - last < REPORT_EVERY_MS) return
        lastReported[videoId] = now

        worker.execute {
            runCatching {
                GlobalPreferences.instance(app)
                val service = YouTubeServiceManager.instance()
                if (!service.signInService.isSigned) return@execute
                service.mediaItemService.updateHistoryPosition(videoId, positionMs / 1000f)
                Log.i(TAG, "reported $videoId at ${positionMs / 1000}s")
            }.onFailure { Log.w(TAG, "couldn't report $videoId", it) }
        }
    }

    /** Where this device got to in a video, and how long the video is, in milliseconds; null if it hasn't played it. */
    fun localState(context: Context, videoId: String?): Pair<Long, Long>? {
        if (videoId == null) return null
        val v = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(videoId, null)
        }.getOrNull() ?: return null
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return if (dur > 0) pos to dur else null
    }

    /** How much of the video you've watched, 0 to 100, from this device. */
    fun localPercent(context: Context, videoId: String?): Int {
        if (videoId == null) return 0
        val v = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(videoId, null)
        }.getOrNull() ?: return 0
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return 0
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return 0
        if (dur <= 0) return 0
        return (pos * 100 / dur).toInt().coerceIn(0, 100)
    }
}
