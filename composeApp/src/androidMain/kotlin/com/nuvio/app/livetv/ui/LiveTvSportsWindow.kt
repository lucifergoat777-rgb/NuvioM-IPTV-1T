package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nuvio.app.livetv.model.LiveChannel

/**
 * The video window on the Sports page: the game plays at the top while you keep scrolling the
 * games below. "Full screen" hands it over to the normal player; closing it frees the player.
 */
@Composable
fun SportsVideoWindow(channel: LiveChannel, onFullScreen: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var buffering by remember(channel.key) { mutableStateOf(true) }
    var failed by remember(channel.key) { mutableStateOf(false) }
    val player = remember(channel.key) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(channel.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(com.nuvio.app.livetv.player.StalkerPlayback.wrap(http)))
            // A small buffer: it's a window, and it starts quickly.
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 20_000, 1_500, 3_000).build())
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder().setUri(channel.url)
                        .apply { com.nuvio.app.livetv.data.StalkerLinks.mimeHint(channel.url).takeIf { com.nuvio.app.livetv.data.StalkerLinks.isStalker(channel.url) }?.let { setMimeType(it) } }
                        .build()
                )
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black).aspectRatio(16f / 9f)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) Text("This channel didn't play here. Try full screen.", Modifier.align(Alignment.Center).padding(16.dp), color = Color.White)
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x66000000)), verticalAlignment = Alignment.CenterVertically) {
            Text(channel.name, Modifier.weight(1f).padding(start = 12.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onFullScreen) { Text("Full screen", color = Color.White) }
            TextButton(onClick = onClose) { Text("Close", color = Color.White) }
        }
    }
}
