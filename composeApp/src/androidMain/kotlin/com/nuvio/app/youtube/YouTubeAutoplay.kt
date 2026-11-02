package com.nuvio.app.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.youtube.YouTubePlayRequest

/** The YouTube page's own view model, kept so the player can ask it what comes after the video that's playing. */
object YouTubeSession {
    @Volatile var feed: YouTubeViewModel? = null

    /** Counts settings changes, so the page knows to reload its feed when it's next shown. */
    var settingsVersion by androidx.compose.runtime.mutableIntStateOf(0)
    var appliedSettingsVersion = 0

    /** You've chosen a caption (or Off) for the video playing, so it isn't switched off for you any more. */
    @Volatile var captionPicked = false

    /** The captions picker is open over the player. */
    var showCaptions by mutableStateOf(false)

    /** The live chat is showing over the player. */
    var chatOn by mutableStateOf(false)
}

/** When the up-next card appears, and when the next video is picked so it's ready in time. */
private const val SHOW_WITHIN_MS = 12_000L
private const val PICK_WITHIN_MS = 45_000L

/**
 * Sits over the player while a YouTube video plays. Near the end it shows a small card with what comes next
 * and how long until it starts; when the video ends, the next one plays. Only while autoplay is on.
 */
@Composable
fun YouTubePlayerOverlay(
    videoId: String,
    positionMs: Long,
    durationMs: Long,
    ended: Boolean,
    playNext: (YouTubePlayRequest) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    YouTubeQualityEffect(videoId)
    YouTubeCaptionsOffByDefault(videoId)
    if (YouTubeSession.showCaptions) YouTubeCaptionsDialog(onDismiss = { YouTubeSession.showCaptions = false })
    YouTubeSpeedEffect(videoId)
    if (YouTubeSession.chatOn) YouTubeChatOverlay(videoId)
    val vm = YouTubeSession.feed ?: return
    val next by vm.upNext.collectAsStateWithLifecycle()
    val autoplay = YouTubeSettings.autoplayNext(context)
    val remaining = durationMs - positionMs
    // A live stream has no end to run up to.
    val live = YouTubePlayerHandle.player?.isCurrentMediaItemLive == true
    val inRange = !live && durationMs > 0 && remaining in 1..PICK_WITHIN_MS
    var started by remember(videoId) { mutableStateOf(false) }

    fun start() {
        if (started) return
        started = true
        vm.playNext(videoId, { p -> playNext(YouTubePlayRequest(p.url, p.title, p.poster, p.videoId, 0L)) }, {})
    }

    // Pick the next video early enough that it's ready, and its stream is looked up.
    LaunchedEffect(videoId, inRange, autoplay) { if (inRange && autoplay) vm.prepareNext(videoId) }
    LaunchedEffect(ended, autoplay) { if (ended && autoplay) start() }

    val item = next
    if (!autoplay || item == null || !inRange || remaining > SHOW_WITHIN_MS || started) return

    val seconds = ((remaining + 999) / 1000).toInt().coerceAtLeast(1)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                // Above the seek bar and its buttons.
                .padding(end = 24.dp, bottom = 120.dp)
                .width(170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6111114))
                .clickable { start() }
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                Text("Up next in $seconds", color = Color(0xFFBBBBBB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
