package com.nuvio.app.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

private enum class PanelMode { MAIN, PLAYLISTS, COMMENTS, REPLIES, QUALITY }

/**
 * What you can do about the video that's playing: like, subscribe, save, read comments. A side sheet on wide
 * screens and a bottom sheet on phones. Tapping outside it, or Back, closes it.
 */
@Composable
fun YouTubePlayerPanel(videoId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: YouTubePlayerViewModel = viewModel(key = "yt-player-$videoId") { YouTubePlayerViewModel(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(PanelMode.MAIN) }

    LaunchedEffect(videoId) { vm.load(videoId) }
    LaunchedEffect(mode) {
        when (mode) {
            PanelMode.PLAYLISTS -> vm.loadPlaylists()
            PanelMode.COMMENTS -> if (state.comments.isEmpty()) vm.loadComments()
            else -> Unit
        }
    }
    BackHandler {
        when (mode) {
            PanelMode.MAIN -> onDismiss()
            PanelMode.REPLIES -> { vm.clearReplies(); mode = PanelMode.COMMENTS }
            else -> mode = PanelMode.MAIN
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (wide) Modifier.fillMaxHeight().widthIn(max = 440.dp) else Modifier.fillMaxWidth().height(maxHeight * 0.75f))
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
            color = Color(0xF2141414),
            shape = if (wide) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().then(if (wide) Modifier.statusBarsPadding() else Modifier).navigationBarsPadding()) {
                    Header(state.info, mode) {
                        when (mode) {
                            PanelMode.MAIN -> onDismiss()
                            PanelMode.REPLIES -> { vm.clearReplies(); mode = PanelMode.COMMENTS }
                            else -> mode = PanelMode.MAIN
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (mode) {
                            PanelMode.MAIN -> Main(state, vm, onPlaylists = { mode = PanelMode.PLAYLISTS }, onComments = { mode = PanelMode.COMMENTS }, onQuality = { mode = PanelMode.QUALITY })
                            PanelMode.QUALITY -> Quality()
                            PanelMode.PLAYLISTS -> Playlists(state, vm)
                            PanelMode.COMMENTS -> Comments(state, vm, onReplies = { key -> vm.loadReplies(key); mode = PanelMode.REPLIES })
                            PanelMode.REPLIES -> Replies(state, vm)
                        }
                    }
                    state.message?.let {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = vm::clearMessage) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(info: YtVideoInfo?, mode: PanelMode, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mode != PanelMode.MAIN) TextButton(onClick = onBack) { Text("Back") }
        info?.authorImage?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (mode) {
                    PanelMode.MAIN -> info?.title ?: "Loading..."
                    PanelMode.PLAYLISTS -> "Save to playlist"
                    PanelMode.COMMENTS -> "Comments"
                    PanelMode.REPLIES -> "Replies"
                    PanelMode.QUALITY -> "Quality"
                },
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
            )
            if (mode == PanelMode.MAIN && info != null) {
                Text(
                    listOfNotNull(info.author, info.subscribers).joinToString("  ·  "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0),
                )
            }
        }
    }
}

@Composable
private fun Main(state: YtPlayerState, vm: YouTubePlayerViewModel, onPlaylists: () -> Unit, onComments: () -> Unit, onQuality: () -> Unit) {
    val info = state.info
    if (state.loading && info == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(
                label = if (info?.likeCount != null) "Like  ${info.likeCount}" else "Like",
                highlighted = info?.liked == true,
                modifier = Modifier.weight(1f),
                onClick = vm::toggleLike,
            )
            Action(label = "Dislike", highlighted = info?.disliked == true, modifier = Modifier.weight(1f), onClick = vm::toggleDislike)
        }
        Action(
            label = if (info?.subscribed == true) "Subscribed" else "Subscribe",
            detail = info?.author,
            highlighted = info?.subscribed == true,
            onClick = vm::toggleSubscribe,
        )
        YouTubePlayerHandle.player?.let { player ->
            val pinned = player.pinnedHeight()
            val playing = player.videoFormat?.height?.takeIf { it > 0 }
            Action(
                label = "Quality",
                detail = if (pinned > 0) YouTubeSettings.qualityLabel(pinned) else "Auto" + (playing?.let { " (${it}p)" } ?: ""),
                onClick = onQuality,
            )
        }
        Action(label = "Save to playlist", onClick = onPlaylists)
        if (info?.commentsKey != null) Action(label = "Comments", onClick = onComments)
        Action(label = "Hide this channel", detail = "Keeps it off the YouTube page", onClick = vm::hideChannel)
        if (!state.signedIn) {
            Text(
                "Sign in on the YouTube page to like, subscribe or save.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0), modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Action(label: String, modifier: Modifier = Modifier, detail: String? = null, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = if (highlighted) Color(0xFFE53935) else Color(0xFF262626),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0D0D0), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Quality() {
    val context = LocalContext.current.applicationContext
    val player = YouTubePlayerHandle.player
    if (player == null) {
        Centered("Nothing is playing.")
        return
    }
    var version by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") version
    val heights = remember(version) { player.availableHeights() }
    val pinned = player.pinnedHeight()
    var codec by remember { mutableStateOf(YouTubeSettings.codecPreference(context)) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Codec", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YouTubeSettings.codecChoices.forEach { choice ->
                    FilterChip(
                        selected = codec == choice,
                        onClick = {
                            codec = choice
                            YouTubeSettings.setCodecPreference(context, choice)
                            player.applyCodecPreference(context)
                            // Pick the same size again so it's in the new codec.
                            if (player.pinnedHeight() > 0) player.selectQuality(context, player.pinnedHeight())
                            version++
                        },
                        label = { Text(YouTubeSettings.codecLabel(choice)) },
                    )
                }
            }
            Text("Size", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0), modifier = Modifier.padding(top = 12.dp))
        }
        item {
            Action(label = "Auto", highlighted = pinned == 0, onClick = { player.selectQuality(context, 0); version++ })
        }
        items(heights) { h ->
            Action(label = YouTubeSettings.qualityLabel(h), highlighted = pinned == h, onClick = { player.selectQuality(context, h); version++ })
        }
    }
}

@Composable
private fun Playlists(state: YtPlayerState, vm: YouTubePlayerViewModel) {
    val rows = state.playlists
    when {
        !state.signedIn -> Centered("Sign in on the YouTube page to save videos.")
        rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        rows.isEmpty() -> Centered("You don't have any playlists yet.")
        else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(rows) { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.togglePlaylist(row) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = row.contains, onCheckedChange = { vm.togglePlaylist(row) })
                    Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Comments(state: YtPlayerState, vm: YouTubePlayerViewModel, onReplies: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort by", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0))
            FilterChip(selected = state.commentsSort == 0, onClick = { vm.loadComments(newest = false) }, label = { Text("Top") })
            FilterChip(selected = state.commentsSort == 1, onClick = { vm.loadComments(newest = true) }, label = { Text("Newest") })
        }
        when {
            state.commentsLoading && state.comments.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.comments.isEmpty() -> Centered("No comments to show.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.comments) { c -> CommentRow(c, onReplies) }
                if (state.canLoadMoreComments) {
                    item {
                        LaunchedEffect(state.comments.size) { vm.loadMoreComments() }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Replies(state: YtPlayerState, vm: YouTubePlayerViewModel) {
    when {
        state.repliesLoading && state.replies.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.replies.isEmpty() -> Centered("No replies to show.")
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.replies) { c -> CommentRow(c, null) }
            if (state.canLoadMoreReplies) {
                item {
                    LaunchedEffect(state.replies.size) { vm.loadMoreReplies() }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: YtComment, onReplies: ((String) -> Unit)?) {
    Column {
        Text(c.author, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB0B0B0))
        Spacer(Modifier.height(2.dp))
        Text(c.text, style = MaterialTheme.typography.bodyMedium)
        val meta = listOfNotNull(c.published, c.likes?.let { "$it likes" }).joinToString("  ·  ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Color(0xFF909090))
        val key = c.replyKey
        if (key != null && onReplies != null) {
            val label = c.replyCount?.let { if (it.any(Char::isLetter)) it else "$it replies" } ?: "Replies"
            TextButton(onClick = { onReplies(key) }) { Text(label) }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))
    }
}
