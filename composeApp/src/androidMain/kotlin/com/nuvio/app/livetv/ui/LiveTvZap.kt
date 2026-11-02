package com.nuvio.app.livetv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.LiveChannel
import kotlinx.coroutines.delay

/** Your channels in list order, without the ones you've hidden. */
private fun visibleChannels(live: LiveTv): List<LiveChannel> {
    val user = live.userState.value
    return live.store.displayChannels.value.filter { it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups }
}

/** What the player needs to open a channel. Also remembers the channel's protection details, if it has any. */
fun liveTvRequest(channel: LiveChannel, programs: List<EpgProgram>?, now: Long): LiveTvPlayRequest {
    com.nuvio.app.livetv.player.LiveTvDrm.remember(channel)
    return LiveTvPlayRequest(
        url = channel.url,
        title = channel.name,
        poster = channel.logo,
        channelKey = channel.key,
        headers = channel.headers,
        subtitle = programs?.firstOrNull { it.isLive(now) }?.title,
    )
}

/** The channel [delta] steps from [currentKey], wrapping round the ends. */
fun zapLiveTv(context: Context, currentKey: String, delta: Int) {
    val live = LiveTv.get(context)
    val list = visibleChannels(live)
    if (list.isEmpty()) return
    val at = list.indexOfFirst { it.key == currentKey }
    val next = list[((if (at < 0) 0 else at) + delta).mod(list.size)]
    com.nuvio.app.livetv.player.LiveTvPauseBuffer.dropIfOtherChannel(next.key)
    com.nuvio.app.livetv.player.LiveTvReconnect.arm(next.url, next.key)
    LiveTvHost.openChannel?.invoke(liveTvRequest(next, live.repository.programs.value[next.key], System.currentTimeMillis()))
}

/** What's on now for a channel, kept up to date while the player is open. */
@Composable
fun liveTvNowPlaying(channelKey: String): String? {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val programs by live.repository.programs.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return programs[channelKey]?.firstOrNull { it.isLive(now) }?.title
}
