package com.nuvio.app.livetv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.livetv.LiveTv

/** Pick another channel without leaving the video: the same list as Live TV, with what's on now. */
@Composable
fun LiveTvPlayerChannelsPanel(currentKey: String, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val all by live.store.displayChannels.collectAsStateWithLifecycle()
    val user by live.userState.collectAsStateWithLifecycle()
    val programs by live.repository.programs.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var recentOnly by remember { mutableStateOf(false) }
    val settings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = com.nuvio.app.livetv.model.LiveTvSettings())
    val now = System.currentTimeMillis()
    val channels = remember(all, user, query, favoritesOnly, recentOnly) {
        // A quick switcher: what you watched lately comes first, and adult-named channels are left out.
        val visible = all.filter {
            it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups && it.groupId !in user.lockedGroups && !(settings.parentalPin.length >= 4 && settings.lockAdultContent && com.nuvio.app.livetv.parental.ParentalControls.isAdult(it.group)) &&
                !com.nuvio.app.livetv.parental.ParentalControls.isAdult(it.name) &&
                !com.nuvio.app.livetv.parental.ParentalControls.isAdult(it.group)
        }
        val byKey = visible.associateBy { it.key }
        val recent = user.recent.mapNotNull { byKey[it] }
        val recentKeys = recent.mapTo(HashSet()) { it.key }
        val base = recent + visible.filter { it.key !in recentKeys }
        val scoped = when {
            favoritesOnly -> base.filter { it.key in user.favorites }
            recentOnly -> recent
            else -> base
        }
        val q = query.trim()
        (if (q.isEmpty()) scoped else scoped.filter { it.name.contains(q, ignoreCase = true) }).take(400)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentKey, channels.size) {
        val at = channels.indexOfFirst { it.key == currentKey }
        if (at > 2 && query.isBlank()) listState.scrollToItem(at - 2)
    }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (wide) Modifier.fillMaxHeight().widthIn(max = 420.dp) else Modifier.fillMaxWidth().height(maxHeight * 0.75f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            color = Color(0xF2141414),
            shape = if (wide) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().then(if (wide) Modifier.statusBarsPadding() else Modifier).navigationBarsPadding()) {
                    Row(Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (recentOnly) "\u25CF Recent" else "\u25CB Recent",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { recentOnly = !recentOnly; favoritesOnly = false }.padding(8.dp),
                        )
                        if (user.favorites.isNotEmpty()) {
                            Text(
                                if (favoritesOnly) "★ Favorites" else "☆ Favorites",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { favoritesOnly = !favoritesOnly; recentOnly = false }.padding(8.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Search channels") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        items(channels, key = { it.key }) { c ->
                            val current = programs[c.key]?.firstOrNull { it.isLive(now) }
                            val playing = c.key == currentKey
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (playing) Color(0x22FFFFFF) else Color.Transparent)
                                    .clickable {
                                        if (!playing) {
                                            com.nuvio.app.livetv.player.LiveTvPauseBuffer.dropIfOtherChannel(c.key)
                                            com.nuvio.app.livetv.player.LiveTvReconnect.arm(c.url, c.key)
                                            LiveTvHost.openChannel?.invoke(liveTvRequest(c, programs[c.key], now))
                                            scope.launch { live.prefs.recordWatched(c.key) }
                                        }
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)), contentAlignment = Alignment.Center) {
                                    if (!c.logo.isNullOrBlank()) AsyncImage(c.logo, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                                    else Text(c.name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = Color(0xFFB0B0B0))
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            (if (c.number > 0) "${c.number}  " else "") + c.name,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (playing) Color(0xFFFF6B6B) else Color.White,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }?.let { QualityBadge(it) }
                                    }
                                    current?.let {
                                        Text(
                                            it.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall, color = Color(0xB3FFFFFF),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
