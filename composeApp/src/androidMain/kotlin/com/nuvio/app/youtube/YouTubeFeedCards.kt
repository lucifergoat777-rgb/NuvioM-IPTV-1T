package com.nuvio.app.youtube

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How long a card must be the one in view, without scrolling, before its preview starts. */
private const val PREVIEW_DWELL_MS = 900L

/** How long a preview plays before the card goes back to its thumbnail. */
private const val PREVIEW_LENGTH_MS = 20_000L

/** A card that is on screen, for deciding which one should play. */
internal class PreviewCandidate(val key: String, val visibleFraction: Float, val fromTop: Float)

/**
 * The key of the card that should be playing a preview: the topmost card that is mostly in view, once scrolling has
 * stopped for a moment. Null while scrolling, when previews are off, or when nothing is mostly in view.
 */
@Composable
internal fun rememberActivePreviewKey(
    enabled: Boolean,
    isScrolling: () -> Boolean,
    candidates: () -> List<PreviewCandidate>,
): State<String?> = produceState<String?>(initialValue = null, enabled) {
    if (!enabled) {
        value = null
        return@produceState
    }
    snapshotFlow {
        if (isScrolling()) {
            null
        } else {
            candidates().filter { it.visibleFraction >= 0.6f }.minByOrNull { it.fromTop }?.key
        }
    }.distinctUntilChanged().collect { key ->
        value = null
        if (key != null) {
            delay(PREVIEW_DWELL_MS)
            value = key
        }
    }
}

internal fun LazyListState.previewCandidates(): List<PreviewCandidate> {
    val info = layoutInfo
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    return info.visibleItemsInfo.mapNotNull { item ->
        val key = item.key as? String ?: return@mapNotNull null
        if (!key.startsWith(VIDEO_KEY_PREFIX) || item.size <= 0) return@mapNotNull null
        val visible = (minOf(end, item.offset + item.size) - maxOf(start, item.offset)).coerceAtLeast(0)
        PreviewCandidate(key, visible / item.size.toFloat(), (item.offset - start).toFloat())
    }
}

internal fun LazyGridState.previewCandidates(): List<PreviewCandidate> {
    val info = layoutInfo
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    return info.visibleItemsInfo.mapNotNull { item ->
        val key = item.key as? String ?: return@mapNotNull null
        if (!key.startsWith(VIDEO_KEY_PREFIX) || item.size.height <= 0) return@mapNotNull null
        val visible = (minOf(end, item.offset.y + item.size.height) - maxOf(start, item.offset.y)).coerceAtLeast(0)
        PreviewCandidate(key, visible / item.size.height.toFloat(), (item.offset.y - start) + item.offset.x / 100000f)
    }
}

/** List item keys of video cards start with this, so the one in view can be told apart from headers and spinners. */
internal const val VIDEO_KEY_PREFIX = "v:"

/**
 * A full-width card like the YouTube app's: the whole thumbnail, then the channel's initial, the title and the
 * details. When [active] and [resolvePreview] finds a stream, a muted preview plays over the thumbnail.
 */
@Composable
internal fun BigVideoCard(
    item: YtItem,
    active: Boolean,
    resolvePreview: suspend (YtItem) -> YtPlayable?,
    previewPlayer: FeedPreviewPlayer,
    soundOn: Boolean,
    onSoundChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (item.kind == YtKind.CHANNEL) {
        ChannelRow(item, modifier, onClick)
        return
    }
    val details = item.author.orEmpty()
    val channelName = details.substringBefore(" • ").trim()
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(if (item.isShorts) 9f / 16f else 16f / 9f)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222)),
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (active && item.kind == YtKind.VIDEO && !item.isLive) {
                var playable by remember(item.videoId) { mutableStateOf<YtPlayable?>(null) }
                LaunchedEffect(item.videoId) { playable = resolvePreview(item) }
                playable?.let { PreviewVideo(it, previewPlayer, soundOn, onSoundChange, Modifier.fillMaxSize()) }
            }
            item.badge?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            val appContext = LocalContext.current.applicationContext
            val watched = remember(item.videoId) { maxOf(item.percentWatched, YouTubeProgress.localPercent(appContext, item.videoId)) }
            if (watched > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(watched.coerceIn(0, 100) / 100f).height(3.dp).background(Color(0xFFE53935)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp, start = 4.dp, end = 4.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF3A3A40)),
                contentAlignment = Alignment.Center,
            ) {
                Text(channelName.take(1).uppercase().ifBlank { "?" }, style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (details.isNotBlank()) {
                    Text(
                        details,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(item: YtItem, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF222222)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            item.author?.let {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The one small player that previews use. It is made once for the feed and reused for every card, and a card
 * that stops playing only stops it. Making and releasing a player on each scroll caused dropped frames.
 */
@OptIn(UnstableApi::class)
internal class FeedPreviewPlayer(context: android.content.Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 4_000, 500, 1_000)
                .build(),
        )
        .build()
        .apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(640, 360)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        }

    /** True once the current preview has drawn its first picture. */
    var showing by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            showing = true
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            android.util.Log.w("FeedPreview", "error ${error.errorCodeName} ${error.message}")
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(playable: YtPlayable) {
        showing = false
        player.setMediaItem(MediaItem.fromUri(playable.url))
        player.playWhenReady = true
        player.prepare()
    }

    /** Stops the picture and the download, but keeps the player for the next card. */
    fun stop() {
        showing = false
        player.stop()
        player.clearMediaItems()
    }

    fun setSound(on: Boolean) {
        player.volume = if (on) 1f else 0f
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !on)
            .build()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

/** The preview player for a feed, released when the feed leaves the screen. */
@Composable
internal fun rememberFeedPreviewPlayer(): FeedPreviewPlayer {
    val context = LocalContext.current
    val holder = remember { FeedPreviewPlayer(context) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    return holder
}

/**
 * A muted, low-resolution preview of [playable], faded in over the thumbnail once its first picture is ready. It
 * stops after [PREVIEW_LENGTH_MS], so a card left in view doesn't keep streaming.
 */
@Composable
private fun PreviewVideo(
    playable: YtPlayable,
    shared: FeedPreviewPlayer,
    soundOn: Boolean,
    onSoundChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var finished by remember(playable.videoId) { mutableStateOf(false) }
    LaunchedEffect(playable.videoId) {
        shared.play(playable)
        delay(PREVIEW_LENGTH_MS)
        finished = true
        shared.stop()
    }
    DisposableEffect(playable.videoId) { onDispose { shared.stop() } }
    // Sound is off, and its data is not fetched, until the speaker is tapped.
    LaunchedEffect(soundOn) { shared.setSound(soundOn) }
    if (finished) return
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> TextureView(ctx).also { view -> view.alpha = 0f; shared.player.setVideoTextureView(view) } },
            update = { view -> view.alpha = if (shared.showing) 1f else 0f },
            onRelease = { view -> shared.player.clearVideoTextureView(view) },
        )
        if (shared.showing) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { onSoundChange(!soundOn) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (soundOn) "Mute" else "Unmute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
