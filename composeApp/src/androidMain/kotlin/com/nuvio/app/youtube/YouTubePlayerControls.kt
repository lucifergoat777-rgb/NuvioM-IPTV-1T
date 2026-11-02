package com.nuvio.app.youtube

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubePlayerAction

/**
 * What the YouTube buttons in the player's controls show and do: the channel's picture, whether you're
 * subscribed, looping, and the video's chapters. Shares its view model with the panel, so they agree.
 */
@Composable
fun rememberYouTubePlayerAction(videoId: String, openPanel: () -> Unit): YouTubePlayerAction {
    val context = LocalContext.current.applicationContext
    val vm: YouTubePlayerViewModel = viewModel(key = "yt-player-$videoId") { YouTubePlayerViewModel(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    var looping by remember(videoId) { mutableStateOf(false) }
    // The chat stops and hides when you leave this video.
    DisposableEffect(videoId) {
        onDispose {
            YouTubeSession.chatOn = false
            vm.stopChat()
        }
    }

    LaunchedEffect(videoId) { vm.load(videoId) }
    // Say what a button did, whether or not the panel is open.
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    val info = state.info
    return YouTubePlayerAction(
        onClick = openPanel,
        channelImage = info?.authorImage,
        subscribed = info?.subscribed == true,
        onSubscribe = vm::toggleSubscribe,
        onChannel = info?.channelId?.let { channel ->
            {
                YouTubeSession.feed?.openChannel(channel, info.author.orEmpty())
                YouTubeHost.closePlayer?.invoke()
            }
        },
        looping = looping,
        onLoop = {
            looping = !looping
            YouTubePlayerHandle.player?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        },
        onCaptions = { YouTubeSession.showCaptions = true },
        onChat = if (info?.liveChatKey != null) ({
            YouTubeSession.chatOn = !YouTubeSession.chatOn
            if (YouTubeSession.chatOn) vm.startChat() else vm.stopChat()
        }) else null,
        chatOn = YouTubeSession.chatOn,
        chapters = info?.chapters.orEmpty(),
    )
}
