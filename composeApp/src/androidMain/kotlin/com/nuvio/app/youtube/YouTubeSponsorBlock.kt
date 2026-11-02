package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.nuvio.app.features.player.skip.SkipInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SponsorBlock's community-marked parts of a YouTube video (sponsors, intros, self-promotion and so on), handed
 * to Nuvio's player as skip intervals. The player shows its Skip button for them, or skips on its own when
 * the YouTube setting says to skip rather than ask.
 */
object YouTubeSponsorBlock {
    private const val TAG = "SponsorBlock"

    suspend fun segments(context: Context, videoId: String): List<SkipInterval> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!YouTubeSettings.sponsorBlockEnabled(app)) return@withContext emptyList()
        val categories = YouTubeSettings.sponsorCategoriesOn(app)
        if (categories.isEmpty()) return@withContext emptyList()
        runCatching {
            GlobalPreferences.instance(app)
            YouTubeServiceManager.instance().mediaItemService.getSponsorSegments(videoId, categories).orEmpty()
                .filter { it.action == null || it.action == SponsorSegment.ACTION_SKIP }
                .sortedBy { it.startMs }
                .map { SkipInterval(it.startMs / 1000.0, it.endMs / 1000.0, "sponsorblock:" + it.category, "sponsorblock") }
                .also { list -> Log.i(TAG, "$videoId: ${list.size} segment(s) " + list.joinToString { "[%.0f-%.0f %s]".format(it.startTime, it.endTime, it.type) }) }
        }.getOrElse {
            Log.w(TAG, "couldn't load segments for $videoId", it)
            emptyList()
        }
    }
}
