package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.ondemand.OnDemandRepository
import com.nuvio.app.livetv.ondemand.VodCategory
import com.nuvio.app.livetv.ondemand.VodEpisode
import com.nuvio.app.livetv.ondemand.VodInfo
import com.nuvio.app.livetv.ondemand.VodItem
import com.nuvio.app.livetv.ondemand.VodKind
import com.nuvio.app.livetv.ondemand.VodSort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE = 60

/**
 * On Demand: the movies and series your Xtream logins offer, by category, with search and sort.
 * Tap a title for its details; a movie plays, a series opens its seasons and episodes.
 */
@Composable
fun OnDemandPage(
    repo: OnDemandRepository,
    groupOrder: List<String> = emptyList(),
    groupNames: Map<String, String> = emptyMap(),
    onSaveGroups: (hidden: Set<String>, order: List<String>, names: Map<String, String>) -> Unit = { _, _, _ -> },
    hiddenCategories: Set<String>,
    onHideCategory: (VodCategory) -> Unit,
    onShowAllCategories: () -> Unit,
    onPlay: (LiveTvPlayRequest) -> Unit,
    sorts: Map<String, String> = emptyMap(),
    defaultSort: String = "DEFAULT",
    onSortPicked: (uid: String, sort: String?) -> Unit = { _, _ -> },
    lockedCategories: Set<String> = emptySet(),
    pin: String = "",
    relockMinutes: Int = -1,
    onLockCategory: (uid: String, locked: Boolean) -> Unit = { _, _ -> },
    onSetPin: (String) -> Unit = {},
) {
    val hasContent by repo.hasContent.collectAsStateWithLifecycle()
    val version by repo.version.collectAsStateWithLifecycle()
    val status by repo.status.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(VodKind.MOVIE) }
    var category by remember { mutableStateOf<VodCategory?>(null) }
    var sort by remember { mutableStateOf(VodSort.DEFAULT) }
    var sortSeed by remember { mutableStateOf(1L) }
    var query by remember { mutableStateOf("") }
    var pickSort by remember { mutableStateOf(false) }
    var categoryActions by remember { mutableStateOf<VodCategory?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var pickGenre by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    // Categories you've opened with the PIN since this page opened.
    var openedTick by remember { mutableStateOf(0) }
    val opened = remember(openedTick, relockMinutes) { com.nuvio.app.livetv.parental.LiveTvUnlocks.active(relockMinutes) }
    LaunchedEffect(relockMinutes) { while (true) { kotlinx.coroutines.delay(15_000); openedTick++ } }
    var askPinFor by remember { mutableStateOf<VodCategory?>(null) }
    var setPinFor by remember { mutableStateOf<VodCategory?>(null) }
    // What's hidden from every list: categories you hid, and locked ones not opened yet.
    val hiddenNow = remember(hiddenCategories, lockedCategories, opened) { hiddenCategories + (lockedCategories - opened) }
    var openedItem by remember { mutableStateOf<VodItem?>(null) }

    var allCategories by remember { mutableStateOf<List<VodCategory>>(emptyList()) }
    var manageGroups by remember { mutableStateOf(false) }
    // Your names and order for the groups (Manage groups), over the provider's.
    fun nameOf(c: VodCategory) = groupNames[c.uid] ?: c.name
    var categories by remember { mutableStateOf<List<VodCategory>>(emptyList()) }
    var items by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }

    LaunchedEffect(kind, version) { genres = repo.genres(kind).filter { it.first.isNotBlank() }.take(60); genre = null }
    // Each category remembers the sort you chose there.
    LaunchedEffect(category?.uid) {
        val saved = VodSort.parse(sorts[category?.uid ?: "all:" + kind.key] ?: defaultSort.takeIf { it != "DEFAULT" })
        if (saved != null) { sort = saved.first; sortSeed = saved.second } else sort = VodSort.DEFAULT
    }
    LaunchedEffect(kind, version, hiddenNow, groupOrder, groupNames) {
        allCategories = repo.categories(kind)
        val rank = groupOrder.withIndex().associate { it.value to it.index }
        categories = allCategories.filter { it.uid !in hiddenCategories }
            .sortedBy { rank[it.uid] ?: Int.MAX_VALUE }
            .map { c -> groupNames[c.uid]?.let { c.copy(name = it) } ?: c }
        if (category != null && categories.none { it.uid == category?.uid }) category = null
    }
    // First page, again whenever what's asked for changes. A short wait while typing.
    LaunchedEffect(kind, category, sort, sortSeed, query, version, hiddenNow, genre) {
        if (query.isNotBlank()) delay(350)
        loading = true
        val first = if (query.isNotBlank()) {
            runCatching { repo.search(kind, query.trim(), hiddenNow, sort) }.getOrDefault(emptyList())
        } else {
            repo.items(kind, category, hiddenNow, false, PAGE, 0, sort, genre, true, sortSeed)
        }
        items = first
        more = query.isBlank() && first.size >= PAGE
        loading = false
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, items.size, more) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 12
        }.collect { near ->
            if (near && more && !loading) {
                loading = true
                val next = repo.items(kind, category, hiddenNow, false, PAGE, items.size, sort, genre, true, sortSeed)
                items = items + next
                more = next.size >= PAGE
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasContent) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status.loading) CircularProgressIndicator()
                    Text(
                        status.message ?: "No movies or series yet.",
                        style = textAlignmentCentre(),
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                    )
                    Text(
                        "Add an Xtream login in Sources. Its movies and series are imported automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.55f),
                    )
                }
            }
            return@Column
        }
        Row(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Movies", kind == VodKind.MOVIE) { kind = VodKind.MOVIE; category = null }
            Chip("Series", kind == VodKind.SERIES) { kind = VodKind.SERIES; category = null }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp), colors = fieldColors())
            if (genres.isNotEmpty()) TextButton(onClick = { pickGenre = true }) { Text(genre ?: "Genre", maxLines = 1) }
            TextButton(onClick = { pickSort = true }) { Text(sort.label, maxLines = 1) }
        }
        if (query.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item { Chip("All", category == null) { category = null } }
                items(categories, key = { it.uid }) { c ->
                    val locked = c.uid in lockedCategories && c.uid !in opened
                    Chip(
                        (if (locked) "\uD83D\uDD12 " else "") + c.name + (if (locked) "" else "  ${c.count}"),
                        category?.uid == c.uid,
                        onLongClick = { categoryActions = c },
                    ) { if (locked) askPinFor = c else category = c }
                }
                if (hiddenCategories.isNotEmpty()) item { TextButton(onClick = onShowAllCategories) { Text("Show hidden") } }
                item { TextButton(onClick = { manageGroups = true }) { Text("Manage") } }
            }
        }
        // "All" with nothing searched or sorted: a row for each category, like Nuvio's Home. A row's "See all" opens that category.
        val rowsMode = category == null && query.isBlank() && genre == null && sort == VodSort.DEFAULT
        if (rowsMode) {
            OnDemandRows(
                repo = repo, kind = kind, categories = categories.filter { it.uid !in lockedCategories },
                hidden = hiddenNow, version = version,
                onOpen = { openedItem = it }, onSeeAll = { category = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(items, key = { it.uid }) { item -> Poster(item) { openedItem = item } }
            }
            if (loading && items.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (!loading && items.isEmpty()) {
                Text(
                    if (query.isNotBlank()) "Nothing matches." else "Nothing in this category.",
                    modifier = Modifier.align(Alignment.Center),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
            }
        }
    }

    if (pickSort) {
        AlertDialog(
            onDismissRequest = { pickSort = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    VodSort.entries.forEach { s ->
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (s == sort) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.fillMaxWidth().clickable {
                                sort = s
                                if (s == VodSort.SHUFFLE) sortSeed = System.nanoTime() % 1_999_999_999L + 1
                                onSortPicked(category?.uid ?: "all:" + kind.key, VodSort.encode(s).let { e -> if (s == VodSort.SHUFFLE) "SHUFFLE:$sortSeed" else e })
                                pickSort = false
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickSort = false }) { Text("Cancel") } },
        )
    }
    categoryActions?.let { c ->
        val isLocked = c.uid in lockedCategories
        AlertDialog(
            onDismissRequest = { categoryActions = null },
            title = { Text(c.name) },
            text = { Text("Hide this category, or lock it so it needs your PIN. \"Show hidden\" brings hidden ones back.") },
            confirmButton = { TextButton(onClick = { onHideCategory(c); categoryActions = null }) { Text("Hide") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (isLocked) onLockCategory(c.uid, false)
                        else if (pin.length < 4) setPinFor = c else onLockCategory(c.uid, true)
                        categoryActions = null
                    }) { Text(if (isLocked) "Remove lock" else "Lock") }
                    TextButton(onClick = { categoryActions = null }) { Text("Cancel") }
                }
            },
        )
    }
    if (pickGenre) {
        PickDialog(
            title = "Genre",
            options = genres.map { it.first to "${it.first}  ${it.second}" },
            extra = "All genres" to { genre = null; pickGenre = false },
            onPick = { g -> genre = g; pickGenre = false },
            onDismiss = { pickGenre = false },
        )
    }
    askPinFor?.let { c ->
        PinDialog(
            title = "Enter your PIN",
            expected = pin,
            onPin = { com.nuvio.app.livetv.parental.LiveTvUnlocks.unlock(c.uid); openedTick++; category = c; askPinFor = null },
            onDismiss = { askPinFor = null },
        )
    }
    setPinFor?.let { c ->
        PinDialog(
            title = "Set a PIN",
            setting = true,
            onPin = { newPin -> onSetPin(newPin); onLockCategory(c.uid, true); setPinFor = null },
            onDismiss = { setPinFor = null },
        )
    }
    openedItem?.let { item -> OnDemandDetails(repo, item, onPlay) { openedItem = null } }
    if (manageGroups) {
        ManageGroupsDialog(
            categories = allCategories, order = groupOrder, names = groupNames, hidden = hiddenCategories,
            onSave = { h, o, n -> onSaveGroups(h, o, n); manageGroups = false },
            onDismiss = { manageGroups = false },
        )
    }
}

/** Manage groups: show or hide, rename and reorder the provider's groups on the On Demand page. */
@Composable
private fun ManageGroupsDialog(
    categories: List<VodCategory>,
    order: List<String>,
    names: Map<String, String>,
    hidden: Set<String>,
    onSave: (Set<String>, List<String>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rank = order.withIndex().associate { it.value to it.index }
    var list by remember { mutableStateOf(categories.sortedBy { rank[it.uid] ?: Int.MAX_VALUE }.map { it.uid }) }
    var shownIds by remember { mutableStateOf(categories.map { it.uid }.toSet() - hidden) }
    var newNames by remember { mutableStateOf(names) }
    var renaming by remember { mutableStateOf<VodCategory?>(null) }
    val byId = remember(categories) { categories.associateBy { it.uid } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage groups") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(380.dp)) {
                items(list.size, key = { list[it] }) { i ->
                    val c = byId[list[i]] ?: return@items
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = c.uid in shownIds,
                            onCheckedChange = { on -> shownIds = if (on) shownIds + c.uid else shownIds - c.uid },
                        )
                        Text(
                            (newNames[c.uid] ?: c.name), Modifier.weight(1f).padding(horizontal = 8.dp).clickable { renaming = c },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(enabled = i > 0, onClick = { list = list.toMutableList().also { java.util.Collections.swap(it, i, i - 1) } }) { Text("\u25B2") }
                        TextButton(enabled = i < list.size - 1, onClick = { list = list.toMutableList().also { java.util.Collections.swap(it, i, i + 1) } }) { Text("\u25BC") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(list.toSet() - shownIds, list, newNames) }) { Text("Done") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(emptySet(), emptyList(), emptyMap()) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    renaming?.let { c ->
        TextPromptDialog(
            title = "Rename group", initial = newNames[c.uid] ?: c.name,
            hint = "Leave it empty to go back to the provider's name.",
            onOk = { n -> newNames = if (n.isBlank()) newNames - c.uid else newNames + (c.uid to n); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

private fun textAlignmentCentre() = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Chip(label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun Poster(item: VodItem, onClick: () -> Unit) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // A poster you fixed (Fix poster) wins over the provider's own.
    val fixed by produceState<String?>(null, item.uid) {
        value = runCatching { com.nuvio.app.livetv.LiveTv.get(appContext).onDemand.cachedPoster(item)?.first?.takeIf { it.isNotBlank() } }.getOrNull()
    }
    val art = fixed ?: item.icon
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222226))) {
            if (!art.isNullOrBlank()) {
                AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    item.name.take(2).uppercase(),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF8A8A90),
                )
            }
            if (item.copies > 1) {
                Text(
                    "${item.copies}",
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Text(
            com.nuvio.app.livetv.ondemand.OnDemandDatabase.displayTitle(item.name),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        item.year?.let {
            Text("$it", style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.55f))
        }
    }
}

@Composable
internal fun OnDemandDetails(repo: OnDemandRepository, item: VodItem, onPlay: (LiveTvPlayRequest) -> Unit, onDismiss: () -> Unit) {
    var info by remember(item.uid) { mutableStateOf<VodInfo?>(null) }
    var failed by remember(item.uid) { mutableStateOf(false) }
    var season by remember(item.uid) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var versions by remember(item.uid) { mutableStateOf<List<OnDemandRepository.Version>?>(null) }
    LaunchedEffect(item.uid) {
        info = repo.info(item)
        failed = info == null
        season = info?.episodes?.firstOrNull()?.season
    }
    val title = com.nuvio.app.livetv.ondemand.OnDemandDatabase.displayTitle(item.name)
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var fixing by remember(item.uid) { mutableStateOf(false) }
    if (fixing) {
        TextPromptDialog(
            title = "Fix poster",
            initial = title + (item.year?.let { " ($it)" } ?: ""),
            hint = "The right name, and the year if it helps (Name (2015)). Leave it empty to use the provider's picture.",
            onOk = { text ->
                fixing = false
                scope.launch {
                    val ok = repo.fixPoster(item, text)
                    android.widget.Toast.makeText(appContext, if (ok) "Poster updated. It shows next time the list opens." else "No poster found for that.", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { fixing = false },
        )
    }
    fun play(url: String, name: String, poster: String?) {
        val art = poster ?: item.icon
        com.nuvio.app.livetv.ondemand.VodResume.remember(appContext, name, art, url, emptyMap())
        onPlay(LiveTvPlayRequest(
            url = url, title = name, poster = art, channelKey = com.nuvio.app.livetv.ondemand.VodResume.keyFor(url),
            startPositionMs = com.nuvio.app.livetv.ondemand.VodResume.positionFor(appContext, url).takeIf { it > 0 },
        ))
        onDismiss()
    }
    versions?.let { list ->
        PickDialog(
            title = "Choose a version",
            options = list.mapIndexed { i, v -> i.toString() to (v.label + if (v.detail.isNotBlank()) "  \u00B7  ${v.detail}" else "") },
            onPick = { idx ->
                val chosen = list[idx.toInt()].item
                versions = null
                scope.launch { repo.movieUrl(chosen)?.let { play(it, title, info?.cover) } }
            },
            onDismiss = { versions = null },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val meta = listOfNotNull(
                    item.year?.toString(), info?.genre, info?.durationText,
                    info?.tech?.quality, item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                if (info == null && !failed) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                if (failed) Text("The provider didn't send details for this one.", modifier = Modifier.padding(top = 8.dp))
                info?.plot?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
                val episodes = info?.episodes.orEmpty()
                if (item.kind == VodKind.SERIES && episodes.isNotEmpty()) {
                    val seasons = episodes.map { it.season }.distinct()
                    LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(seasons) { s -> Chip("Season $s", season == s) { season = s } }
                    }
                    LazyColumn(Modifier.height(240.dp).padding(top = 6.dp)) {
                        items(episodes.filter { it.season == season }, key = { it.id }) { ep ->
                            Row(
                                Modifier.fillMaxWidth().clickable { play(ep.url, "$title  S${ep.season}E${ep.episode}", ep.image) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${ep.episode}", Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge)
                                Text(ep.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (item.kind == VodKind.MOVIE) {
                TextButton(onClick = {
                    scope.launch {
                        val copies = if (item.copies > 1) repo.versions(item) else emptyList()
                        if (copies.size > 1) versions = copies
                        else repo.movieUrl(item)?.let { play(it, title, info?.cover) }
                    }
                }) { Text(if (item.copies > 1) "Play (${item.copies} versions)" else "Play") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { fixing = true }) { Text("Fix poster") }
                if (item.kind == VodKind.MOVIE) TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}


/** On Demand as rows: Recently added, then each category with its first titles. */
@Composable
private fun OnDemandRows(
    repo: OnDemandRepository,
    kind: VodKind,
    categories: List<VodCategory>,
    hidden: Set<String>,
    version: Int,
    onOpen: (VodItem) -> Unit,
    onSeeAll: (VodCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "recent") {
            var recent by remember(kind, version, hidden) { mutableStateOf<List<VodItem>>(emptyList()) }
            LaunchedEffect(kind, version, hidden) { recent = runCatching { repo.items(kind, null, hidden, true, ROW_SIZE, 0) }.getOrDefault(emptyList()) }
            VodRow("Recently added", recent, onOpen, null)
        }
        items(categories, key = { it.uid }) { c ->
            var row by remember(c.uid, version, hidden) { mutableStateOf<List<VodItem>>(emptyList()) }
            LaunchedEffect(c.uid, version, hidden) { row = runCatching { repo.items(kind, c, hidden, false, ROW_SIZE, 0) }.getOrDefault(emptyList()) }
            VodRow(c.name, row, onOpen) { onSeeAll(c) }
        }
    }
}

@Composable
private fun VodRow(title: String, items: List<VodItem>, onOpen: (VodItem) -> Unit, onSeeAll: (() -> Unit)?) {
    if (items.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text("See all") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.uid }) { item -> Box(Modifier.width(112.dp)) { Poster(item) { onOpen(item) } } }
        }
    }
}

private const val ROW_SIZE = 16
