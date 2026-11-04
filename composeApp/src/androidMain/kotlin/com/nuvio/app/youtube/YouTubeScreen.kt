package com.nuvio.app.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.features.youtube.YouTubePlayRequest

/** The YouTube page: feeds, search and sign-in, laid out for touch. Playing hands off to Nuvio's own player. */
@Composable
fun YouTubeScreen(modifier: Modifier = Modifier, onPlay: (YouTubePlayRequest) -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: YouTubeViewModel = viewModel { YouTubeViewModel(context) }
    val accounts: YouTubeAccountViewModel = viewModel { YouTubeAccountViewModel(context) }
    YouTubeSession.feed = vm
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val account by accounts.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var tabsVersion by remember { mutableStateOf(0) }
    val settingsVersion = YouTubeSession.settingsVersion
    LaunchedEffect(settingsVersion) {
        if (settingsVersion != YouTubeSession.appliedSettingsVersion) {
            YouTubeSession.appliedSettingsVersion = settingsVersion
            tabsVersion++
            vm.selectTab(YouTubeSettings.visibleTabs(context).let { if (state.tab in it) state.tab else it.first() })
        }
    }
    val tabs = remember(tabsVersion) { YouTubeSettings.visibleTabs(context) }
    val previewsOn = remember(tabsVersion) { YouTubeSettings.feedPreview(context) }

    LaunchedEffect(Unit) { accounts.refresh() }
    LaunchedEffect(account.checked, account.signedIn) { if (account.checked && account.signedIn != state.signedIn) vm.selectTab(state.tab) }
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    fun play(item: YtItem, from: List<YtItem>) {
        vm.activate(item, from) { p ->
            onPlay(YouTubePlayRequest(p.url, p.title, p.poster, p.videoId, p.startPositionMs))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val compact = maxWidth < 600.dp
    val navBarStyle by com.nuvio.app.features.settings.ThemeSettingsRepository.navBarStyle.collectAsStateWithLifecycle()
    // Only the CLASSIC tablet top rail needs clearing; the bottom pill doesn't.
    val topRail = maxWidth >= 768.dp && navBarStyle == com.nuvio.app.features.settings.NavBarStyle.CLASSIC
    val bottomBar = LocalNuvioBottomNavigationOverlayPadding.current
    var searchOpen by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = if (topRail) 72.dp else 0.dp, bottom = bottomBar)) {
        val searchField: @Composable (Modifier) -> Unit = { m ->
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.suggest(it) },
                modifier = m.onFocusChanged {
                    searchFocused = it.isFocused
                    if (it.isFocused) vm.suggest(query)
                },
                singleLine = true,
                placeholder = { Text("Search YouTube") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query); focusManager.clearFocus() }),
            )
        }
        val topActions: @Composable () -> Unit = {
            IconButton(onClick = {
                searchOpen = !searchOpen
                if (!searchOpen) { query = ""; focusManager.clearFocus() }
            }) {
                Icon(Icons.Filled.Search, contentDescription = if (searchOpen) "Hide search" else "Search YouTube")
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
            if (account.signedIn) {
                OutlinedButton(onClick = { showAccounts = true }) {
                    Text(account.accountName ?: "Account", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (account.checked) {
                Button(onClick = { accounts.startSignIn() }) { Text("Sign in") }
            }
        }
        val tabRow: @Composable () -> Unit = {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { tab ->
                    FilterChip(
                        selected = state.tab == tab && state.subTitle == null,
                        onClick = { query = ""; vm.selectTab(tab) },
                        label = { Text(tab.title) },
                    )
                }
            }
        }
        if (compact) {
            // Phone: the title and actions on one line, the search box and tabs below.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("YouTube", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    topActions()
                }
                if (searchOpen) searchField(Modifier.fillMaxWidth())
                tabRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("YouTube", style = MaterialTheme.typography.titleLarge)
                Box(Modifier.weight(1f)) { tabRow() }
                topActions()
            }
            if (searchOpen) searchField(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (searchFocused && suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1B1F)),
            ) {
                suggestions.forEach { s ->
                    Text(
                        s,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { query = s; vm.search(s); focusManager.clearFocus() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (query.isBlank()) TextButton(onClick = { vm.clearSearchHistory() }) { Text("Clear search history") }
            }
        }

        state.subTitle?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                (state.loading || !account.checked && !state.signedIn && (state.tab.needsSignIn || state.tab == YtTab.HOME)) && state.isEmpty ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isEmpty -> Message(
                    modifier = Modifier.align(Alignment.Center),
                    text = when {
                        state.error != null -> state.error!!
                        state.tab == YtTab.HOME && !state.signedIn ->
                            "Home is picked for you. YouTube only fills it once you sign in. Try Live, Music or Gaming above, or sign in."
                        state.tab.needsSignIn && !state.signedIn -> "Sign in to see your ${state.tab.title.lowercase()}."
                        else -> "Nothing here yet."
                    },
                    action = if ((state.tab.needsSignIn || state.tab == YtTab.HOME) && !state.signedIn) "Sign in" else null,
                    onAction = { accounts.startSignIn() },
                )
                state.tab == YtTab.CHANNELS && state.subTitle == null && state.grid.isNotEmpty() ->
                    ChannelsList(state, vm, split = !compact, onItem = { item, from -> play(item, from) })
                state.grid.isNotEmpty() && !compact ->
                    FeedGridClassic(state, onItem = { play(it, state.grid) }, onMore = vm::loadMore)
                state.grid.isNotEmpty() -> FeedGrid(
                    state,
                    previewsOn = previewsOn,
                    resolvePreview = vm::previewStream,
                    onItem = { play(it, state.grid) },
                    onMore = vm::loadMore,
                )
                !compact -> FeedRowsClassic(state, onItem = { item, row -> play(item, row) }, onMoreRow = vm::loadMoreRow)
                else -> FeedRows(
                    state,
                    previewsOn = previewsOn,
                    resolvePreview = vm::previewStream,
                    onItem = { item, row -> play(item, row) },
                    onMoreRow = vm::loadMoreRow,
                )
            }
            error?.let {
                AlertDialog(
                    onDismissRequest = vm::clearError,
                    confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
                    text = { Text(it) },
                )
            }
        }
    }
    }
    }

    if (showSettings) {
        YouTubeSettingsSheet(onClose = {
            showSettings = false
            tabsVersion++
            vm.selectTab(YouTubeSettings.visibleTabs(context).let { if (state.tab in it) state.tab else it.first() })
        })
    }

    YouTubeSignInDialog(accounts, account)
    if (showAccounts) {
        YouTubeAccountsDialog(
            account = account,
            onSelect = { id -> showAccounts = false; accounts.selectAccount(id) { vm.selectTab(state.tab) } },
            onAdd = { showAccounts = false; accounts.startSignIn() },
            onSignOut = { showAccounts = false; accounts.signOut { vm.selectTab(state.tab) } },
            onDismiss = { showAccounts = false },
        )
    }
}

@Composable
private fun Message(modifier: Modifier, text: String, action: String?, onAction: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun FeedRows(
    state: YtUiState,
    previewsOn: Boolean,
    resolvePreview: suspend (YtItem) -> YtPlayable?,
    onItem: (YtItem, List<YtItem>) -> Unit,
    onMoreRow: (Int) -> Unit,
) {
    var soundOn by remember { mutableStateOf(false) }
    val previewPlayer = rememberFeedPreviewPlayer()
    val gridState = rememberLazyGridState()
    val screenActive = LocalScreenActive.current
    val activeKey by rememberActivePreviewKey(
        enabled = previewsOn && screenActive,
        isScrolling = { gridState.isScrollInProgress },
        candidates = { gridState.previewCandidates() },
    )
    // One column on a phone, more on a tablet; each row's title spans the full width.
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.rows.forEachIndexed { rowIndex, row ->
            if (row.title.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h:$rowIndex") {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp),
                    )
                }
            }
            itemsIndexed(row.items, key = { index, item -> "$VIDEO_KEY_PREFIX$rowIndex:$index:${item.videoId ?: item.title}" }) { index, item ->
                // Ask for the row's next page when you get near its end.
                if (row.canLoadMore && index >= row.items.size - 4) {
                    LaunchedEffect(row.items.size) { onMoreRow(rowIndex) }
                }
                BigVideoCard(
                    item = item,
                    active = activeKey == "$VIDEO_KEY_PREFIX$rowIndex:$index:${item.videoId ?: item.title}",
                    resolvePreview = resolvePreview,
                    previewPlayer = previewPlayer,
                    soundOn = soundOn,
                    onSoundChange = { soundOn = it },
                ) { onItem(item, row.items) }
            }
        }
    }
}

/** The tablet look: each row scrolls sideways, with small cards. */
@Composable
private fun FeedRowsClassic(state: YtUiState, onItem: (YtItem, List<YtItem>) -> Unit, onMoreRow: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(state.rows) { rowIndex, row ->
            Column {
                Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                val listState = rememberLazyListState()
                // Ask for the row's next page when you get near its end.
                LaunchedEffect(listState, row.canLoadMore, row.items.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last -> if (row.canLoadMore && last >= row.items.size - 4) onMoreRow(rowIndex) }
                }
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(row.items) { item ->
                        VideoCard(item, Modifier.width(260.dp)) { onItem(item, row.items) }
                    }
                }
            }
        }
    }
}

/** The tablet look for the long feeds: a grid of small cards. */
@Composable
private fun FeedGridClassic(state: YtUiState, onItem: (YtItem) -> Unit, onMore: () -> Unit) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 240.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.grid) { item -> VideoCard(item, Modifier.fillMaxWidth()) { onItem(item) } }
        if (state.canLoadMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(state.grid.size) { onMore() }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Your subscribed channels. Wide screens show the highlighted channel's videos beside the list; phones open a channel on tap. */
@Composable
private fun ChannelsList(state: YtUiState, vm: YouTubeViewModel, split: Boolean, onItem: (YtItem, List<YtItem>) -> Unit) {
    val preview by vm.preview.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(0) }
    LaunchedEffect(state.grid.firstOrNull()?.title, split) {
        if (split) state.grid.getOrNull(selected)?.let(vm::previewChannel)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.canLoadMore, state.grid.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.grid.size - 5) vm.loadMore() }
    }
    val channelList: @Composable (Modifier) -> Unit = { m ->
        LazyColumn(m, state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.grid) { i, channel ->
                val on = split && selected == i
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { selected = i; if (split) vm.previewChannel(channel) else vm.open(channel) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = channel.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                    )
                    Text(channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    if (split) TextButton(onClick = { vm.open(channel) }) { Text("Open") }
                }
            }
        }
    }
    if (!split) {
        channelList(Modifier.fillMaxSize())
        return
    }
    Row(Modifier.fillMaxSize()) {
        channelList(Modifier.width(300.dp).fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState, preview.canLoadMore, preview.title) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .collect { last -> if (last >= preview.items.size - 6) vm.previewMore() }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(preview.items) { item -> VideoCard(item, Modifier.fillMaxWidth()) { onItem(item, preview.items) } }
            }
            if (preview.loading && preview.items.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun FeedGrid(
    state: YtUiState,
    previewsOn: Boolean,
    resolvePreview: suspend (YtItem) -> YtPlayable?,
    onItem: (YtItem) -> Unit,
    onMore: () -> Unit,
) {
    var soundOn by remember { mutableStateOf(false) }
    val previewPlayer = rememberFeedPreviewPlayer()
    val gridState = rememberLazyGridState()
    val screenActive = LocalScreenActive.current
    val activeKey by rememberActivePreviewKey(
        enabled = previewsOn && screenActive,
        isScrolling = { gridState.isScrollInProgress },
        candidates = { gridState.previewCandidates() },
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(state.grid, key = { index, item -> "$VIDEO_KEY_PREFIX$index:${item.videoId ?: item.title}" }) { index, item ->
            BigVideoCard(
                item = item,
                active = activeKey == "$VIDEO_KEY_PREFIX$index:${item.videoId ?: item.title}",
                resolvePreview = resolvePreview,
                previewPlayer = previewPlayer,
                soundOn = soundOn,
                onSoundChange = { soundOn = it },
            ) { onItem(item) }
        }
        if (state.canLoadMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                LaunchedEffect(state.grid.size) { onMore() }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun VideoCard(item: YtItem, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (item.isShorts) 9f / 16f else 16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222))) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            item.badge?.let {
                Text(
                    it,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
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
        Spacer(Modifier.height(6.dp))
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        item.author?.let {
            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
