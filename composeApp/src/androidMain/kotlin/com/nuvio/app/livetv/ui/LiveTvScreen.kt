package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.model.ChannelGroup
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.LiveChannel

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

/** The Live TV page: your channels with what's on now, grouped, searchable, ready to play. */
@Composable
fun LiveTvScreen(modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: LiveTvViewModel = viewModel { LiveTvViewModel(context) }
    val allChannels by vm.channels.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val epgs by vm.epgSources.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shapes by vm.pictureShapes.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var group by remember { mutableStateOf(ChannelGroup.ALL) }
    var restoredGroup by remember { mutableStateOf(false) }
    var actionChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var actionGroup by remember { mutableStateOf<LiveGroup?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var pickGroupFor by remember { mutableStateOf<LiveChannel?>(null) }
    var copyGroupFor by remember { mutableStateOf<LiveChannel?>(null) }
    var guideFor by remember { mutableStateOf<LiveChannel?>(null) }
    var logoFor by remember { mutableStateOf<LiveChannel?>(null) }
    var newGroup by remember { mutableStateOf<String?>(null) }
    var askNewGroup by remember { mutableStateOf(false) }
    // Groups you've opened with the PIN since this page opened.
    var unlockTick by remember { mutableStateOf(0) }
    val unlocked = remember(unlockTick, settings.relockMinutes) { com.nuvio.app.livetv.parental.LiveTvUnlocks.active(settings.relockMinutes) }
    // Groups that were opened with the PIN lock again when their time is up.
    LaunchedEffect(settings.relockMinutes) {
        while (true) { kotlinx.coroutines.delay(15_000); unlockTick++ }
    }
    val relockSetting by androidx.compose.runtime.rememberUpdatedState(settings.relockMinutes)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (relockSetting == com.nuvio.app.livetv.parental.LiveTvUnlocks.LEAVE) com.nuvio.app.livetv.parental.LiveTvUnlocks.lockAll() }
    }
    var askPinFor by remember { mutableStateOf<String?>(null) }
    var setPinFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showPlaylists by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showAddEpg by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var showOnDemand by remember { mutableStateOf(false) }
    var showSports by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var recordFor by remember { mutableStateOf<LiveChannel?>(null) }
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val seriesRules by vm.seriesRules.collectAsStateWithLifecycle()
    // Messages from recordings (a drive that isn't plugged in, a recording that failed).
    val notice by com.nuvio.app.livetv.record.LiveTvNotice.current.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        notice?.let {
            android.widget.Toast.makeText(context, it.text, android.widget.Toast.LENGTH_LONG).show()
            com.nuvio.app.livetv.record.LiveTvNotice.clear(it)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotifications() {
        if (!com.nuvio.app.livetv.reminders.LiveTvReminders.canNotify(context)) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Locked groups stay out of every list until you've opened them with the PIN.
    // With a PIN set, groups you locked and groups with an adult name (when that's on) need it.
    val isLockedGroup: (String, String) -> Boolean = remember(user.lockedGroups, settings.parentalPin, settings.lockAdultContent) {
        { id, title ->
            id in user.lockedGroups ||
                (settings.parentalPin.length >= 4 && settings.lockAdultContent && com.nuvio.app.livetv.parental.ParentalControls.isAdult(title))
        }
    }
    val channels = remember(allChannels, user, unlocked, isLockedGroup) {
        allChannels.filter {
            it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups &&
                (!isLockedGroup(it.groupId, it.group) || it.groupId in unlocked)
        }
    }
    val groups = remember(channels, user) {
        val seen = LinkedHashMap<String, LiveGroup>()
        channels.forEach { c ->
            val title = user.groupNames[c.groupId] ?: c.group
            val existing = seen[c.groupId]
            seen[c.groupId] = if (existing == null) LiveGroup(c.groupId, title, 1) else existing.copy(count = existing.count + 1)
        }
        seen.values.toList()
    }
    // Locked groups with no visible channels still need a chip, so they can be opened.
    val lockedChips = remember(allChannels, user, unlocked, isLockedGroup) {
        allChannels.filter { isLockedGroup(it.groupId, it.group) && it.groupId !in unlocked && it.groupId !in user.hiddenGroups }
            .groupBy { it.groupId }
            .map { (id, list) -> LiveGroup(id, user.groupNames[id] ?: list.first().group, list.size) }
    }
    val byKey = remember(channels) { channels.associateBy { it.key } }
    // Your own groups, then the playlists' groups.
    val ownGroups = remember(user.customGroups, byKey) {
        user.customGroups.map { g -> LiveGroup(g.id, g.name, g.channelKeys.count { it in byKey }) }
    }
    val chips = remember(groups, lockedChips, ownGroups, settings.groupSort, user.groupOrder) {
        val all = ownGroups + groups.map { g ->
            // A channel copied into a group counts there too.
            val extra = user.channelCopies[g.id].orEmpty().count { it in byKey }
            if (extra > 0) g.copy(count = g.count + extra) else g
        } + lockedChips.filter { l -> groups.none { it.id == l.id } }
        when (settings.groupSort) {
            "playlist" -> all
            "name" -> all.sortedBy { it.title.lowercase() }
            else -> all.sortedBy { g -> user.groupOrder.indexOf(g.id).let { if (it < 0) Int.MAX_VALUE else it } }
        }
    }
    // A group that is locked (the PIN was set or removed while on it) is left for All.
    LaunchedEffect(group, isLockedGroup, unlocked, chips) {
        val special = group == ChannelGroup.ALL || group == ChannelGroup.FAVORITES || group == ChannelGroup.RECENT
        if (!special && group !in unlocked && isLockedGroup(group, chips.firstOrNull { it.id == group }?.title.orEmpty())) {
            group = ChannelGroup.ALL
        }
    }
    val favorites = remember(user) { user.favorites.toSet() }
    val shown = remember(channels, group, query, favorites, user) {
        val base = when (group) {
            ChannelGroup.ALL -> channels
            ChannelGroup.FAVORITES -> user.favorites.mapNotNull { k -> channels.firstOrNull { it.key == k } }
            ChannelGroup.RECENT -> user.recent.mapNotNull { k -> channels.firstOrNull { it.key == k } }
            else -> if (group.startsWith(ChannelGroup.CUSTOM_PREFIX)) {
                val keys = user.customGroups.firstOrNull { it.id == group }?.channelKeys.orEmpty()
                keys.mapNotNull { byKey[it] }
            } else {
                val inGroup = channels.filter { it.groupId == group } +
                    user.channelCopies[group].orEmpty().mapNotNull { byKey[it] }.filter { c -> channels.none { it.key == c.key && it.groupId == group } }
                // Your own order for this group wins over the sort.
                val mine = user.channelOrder[group]
                if (mine.isNullOrEmpty()) inGroup
                else inGroup.sortedBy { c -> mine.indexOf(c.key).let { if (it < 0) Int.MAX_VALUE else it } }
            }
        }
        val sorted = when {
            group == ChannelGroup.FAVORITES || group == ChannelGroup.RECENT -> base
            group != ChannelGroup.ALL && group.startsWith(ChannelGroup.CUSTOM_PREFIX) -> base
            group != ChannelGroup.ALL && !user.channelOrder[group].isNullOrEmpty() -> base
            settings.channelSort == com.nuvio.app.livetv.model.ChannelSort.NUMBER ->
                base.sortedWith(compareBy({ if (it.number > 0) it.number else Int.MAX_VALUE }, { it.name.lowercase() }))
            settings.channelSort == com.nuvio.app.livetv.model.ChannelSort.NAME -> base.sortedBy { it.name.lowercase() }
            else -> base
        }
        val q = query.trim()
        val found = if (q.isEmpty()) sorted else sorted.filter { it.name.contains(q, ignoreCase = true) }
        // "Number channels in order": 1, 2, 3 down the list shown.
        if (settings.sequentialNumbers) found.mapIndexed { i, c -> c.copy(number = i + 1) } else found
    }

    // "Play the last channel": when Live TV first opens in this run of the app, it starts the one you watched last.
    LaunchedEffect(user.lastChannelKey, allChannels.size, settings.autoPlayLastChannel) {
        val key = user.lastChannelKey
        if (settings.autoPlayLastChannel && !LiveTvStart.autoPlayedThisRun && key != null && allChannels.isNotEmpty()) {
            allChannels.firstOrNull { it.key == key }?.let { c ->
                LiveTvStart.autoPlayedThisRun = true
                playLive(onPlay, c.toRequest(programs[c.key], now))
            }
        }
    }

    // Open on the group you were last in.
    LaunchedEffect(user.lastGroupId, settings.rememberLastGroup) {
        val last = user.lastGroupId
        if (!restoredGroup && last != null && settings.rememberLastGroup) {
            restoredGroup = true
            if (last in setOf(ChannelGroup.ALL, ChannelGroup.FAVORITES, ChannelGroup.RECENT) || chips.any { it.id == last }) group = last
        }
    }

    LaunchedEffect(showGuide, shown) {
        if (showGuide) vm.loadGuide(shown.map { it.key })
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
            val tablet = maxWidth >= 768.dp
            val compact = maxWidth < 600.dp
            Column(Modifier.fillMaxSize().padding(top = if (tablet) 72.dp else 0.dp)) {
                val lastChannel = remember(allChannels, user.lastChannelKey) { allChannels.firstOrNull { it.key == user.lastChannelKey } }
                Header(
                    compact = compact,
                    query = query,
                    onQuery = { query = it },
                    onPlaylists = { showPlaylists = true },
                    hasHidden = user.hiddenChannels.isNotEmpty() || user.hiddenGroups.isNotEmpty(),
                    onHidden = { showHidden = true },
                    showViewControls = playlists.isNotEmpty() && !showRecordings && !showOnDemand && !showSports && !showCalendar,
                    grid = settings.channelGrid,
                    onToggleGrid = { vm.updateSettings { it.copy(channelGrid = !it.channelGrid) } },
                    onAddGroup = { newGroup = null; askNewGroup = true },
                    lastChannel = lastChannel?.name,
                    onLast = {
                        lastChannel?.let { c -> vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) }
                    },
                )
                if (playlists.isEmpty()) {
                    EmptyState(onAdd = { showAdd = true })
                } else {
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TabChip("Channels", !showGuide && !showRecordings && !showOnDemand && !showSports && !showCalendar) { showGuide = false; showRecordings = false; showOnDemand = false; showSports = false; showCalendar = false }
                        TabChip("Guide", showGuide) { showGuide = true; showRecordings = false; showOnDemand = false; showSports = false; showCalendar = false }
                        TabChip("Calendar", showCalendar) { showCalendar = true; showGuide = false; showRecordings = false; showOnDemand = false; showSports = false }
                        TabChip("Sports", showSports) { showSports = true; showGuide = false; showRecordings = false; showOnDemand = false; showCalendar = false }
                        TabChip("On Demand", showOnDemand) { showOnDemand = true; showGuide = false; showRecordings = false; showSports = false; showCalendar = false }
                        TabChip(
                            if (recordings.any { it.isRecording }) "\u25CF Recordings" else "Recordings",
                            showRecordings,
                        ) { showRecordings = true; showGuide = false; showOnDemand = false; showSports = false; showCalendar = false }
                    }
                    if (!showRecordings && !showOnDemand && !showSports && !showCalendar) LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        fun pick(id: String) { group = id; vm.setLastGroup(id) }
                        if (settings.showAllChannelsGroup) item { GroupChip("All", group == ChannelGroup.ALL) { pick(ChannelGroup.ALL) } }
                        if (favorites.isNotEmpty() && settings.showFavoritesGroup) item { GroupChip("Favorites", group == ChannelGroup.FAVORITES) { pick(ChannelGroup.FAVORITES) } }
                        if (user.recent.isNotEmpty() && settings.showRecentGroup) item { GroupChip("Recent", group == ChannelGroup.RECENT) { pick(ChannelGroup.RECENT) } }
                        items(chips, key = { it.id }) { g ->
                            val locked = isLockedGroup(g.id, g.title) && g.id !in unlocked
                            GroupChip(
                                (if (locked) "\uD83D\uDD12 " else "") + g.title + if (settings.showGroupCounts && !locked) "  ${g.count}" else "",
                                group == g.id,
                                onLongClick = { actionGroup = g },
                            ) {
                                if (locked) askPinFor = g.id else pick(g.id)
                            }
                        }
                    }
                    StatusLine(status)
                    if (showCalendar) {
                        CalendarPage(calendar = vm.calendar, use24Hour = settings.use24HourClock, onPlay = { r -> onPlay(r) })
                    } else if (showSports) {
                        SportsPage(
                            repo = vm.sports,
                            hasChannels = allChannels.isNotEmpty(),
                            use24Hour = settings.use24HourClock,
                            onWatch = { c -> vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) },
                            onRecord = { ev, m ->
                                android.widget.Toast.makeText(context, vm.recordSportsEvent(ev, m.channel), android.widget.Toast.LENGTH_LONG).show()
                            },
                        )
                    } else if (showOnDemand) {
                        OnDemandPage(
                            repo = vm.onDemand,
                            relockMinutes = settings.relockMinutes,
                            groupOrder = user.vodGroupOrder,
                            groupNames = user.vodGroupNames,
                            onSaveGroups = { hidden, order, names -> vm.saveVodGroups(hidden, order, names) },
                            hiddenCategories = user.vodHiddenCategories,
                            onHideCategory = { vm.hideVodCategory(it.uid) },
                            onShowAllCategories = vm::showAllVodCategories,
                            onPlay = onPlay,
                            sorts = user.vodSorts,
                            defaultSort = settings.vodDefaultSort,
                            onSortPicked = vm::setVodSort,
                            lockedCategories = user.lockedGroups.filter { it.startsWith("vod:") }.map { it.removePrefix("vod:") }.toSet(),
                            pin = settings.parentalPin,
                            onLockCategory = vm::lockVodCategory,
                            onSetPin = vm::setPin,
                        )
                    } else if (showRecordings) {
                        RecordingsList(
                            recordings = recordings,
                            schedules = schedules,
                            freeBytes = vm.freeBytes(),
                            onPlay = { r -> onPlay(vm.playRequest(r)) },
                            onStop = { vm.stopRecording(it.id) },
                            onDelete = { vm.deleteRecording(it.id) },
                            onCancelSchedule = { vm.cancelSchedule(it.id) },
                            series = seriesRules,
                            onRemoveSeries = { vm.removeSeries(it.id) },
                            onKeepLast = { r, n -> vm.setKeepLast(r.id, n) },
                            resumeOf = vm::resumeOf,
                        )
                    } else if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (status.loading && channels.isEmpty()) CircularProgressIndicator()
                            else Text(
                                if (query.isNotBlank()) "No channels match." else "No channels yet.",
                                color = LocalContentColor.current.copy(alpha = 0.65f),
                            )
                        }
                    } else if (showGuide) {
                        if (programs.isEmpty() && !status.loading) {
                            Text(
                                "No guide yet. Open Sources and add a guide link, or use a playlist that includes one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        LiveTvGuideGrid(
                            channels = shown,
                            programs = programs,
                            now = now,
                            tablet = tablet,
                            reminders = user.reminders,
                            onRemind = { c, p, on -> askNotifications(); vm.setReminder(c, p, on) },
                            isRecordSet = { c, p -> vm.isScheduled(c.key, p) },
                            onRecordSeries = { c, p ->
                                android.widget.Toast.makeText(context, vm.addSeries(c, p), android.widget.Toast.LENGTH_LONG).show()
                            },
                            onRecord = { c, p, on ->
                                if (on) android.widget.Toast.makeText(context, vm.scheduleProgram(c, p), android.widget.Toast.LENGTH_LONG).show()
                                else vm.cancelProgram(c.key, p.startMs)
                            },
                            onPlay = { c -> vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) },
                            onCatchup = { c, p ->
                                scope.launch {
                                    val request = vm.catchupRequest(c, p)
                                    if (request != null) { com.nuvio.app.livetv.player.LiveTvReconnect.arm(request.url, request.channelKey); onPlay(request) }
                                    else android.widget.Toast.makeText(context, "That programme can't be replayed.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else if (settings.channelGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (tablet) 150.dp else 112.dp),
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            gridItems(shown, key = { it.key }) { c ->
                                ChannelTile(c, programs[c.key], now, c.key in favorites, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    } else if (tablet) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            gridItems(shown, key = { it.key }) { c ->
                                ChannelRow(c, programs[c.key], now, c.key in favorites, vm::toggleFavorite, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(shown, key = { it.key }) { c ->
                                ChannelRow(c, programs[c.key], now, c.key in favorites, vm::toggleFavorite, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionChannel?.let { c ->
        ChannelActionsDialog(
            channel = c,
            favorite = c.key in favorites,
            shape = com.nuvio.app.livetv.player.PictureShape.of(shapes[c.key]),
            onFavorite = { vm.toggleFavorite(c.key) },
            onRename = { vm.renameChannel(c.key, it) },
            onNumber = { vm.setChannelNumber(c.key, it) },
            onHide = { vm.setChannelHidden(c.key, true) },
            onShape = { vm.setPictureShape(c.key, it) },
            onRemind = programs[c.key]?.firstOrNull { it.startMs > now }?.let { next ->
                { askNotifications(); vm.setReminder(c, next, true) }
            },
            onRecord = { recordFor = c },
            onMove = if (group != ChannelGroup.ALL && group != ChannelGroup.FAVORITES && group != ChannelGroup.RECENT && query.isBlank()) { delta ->
                vm.moveChannel(group, c.key, delta, shown.map { it.key })
            } else null,
            onAddToGroup = { pickGroupFor = c },
            onCopyToGroup = { copyGroupFor = c },
            onRemoveFromGroup = if (group.startsWith(ChannelGroup.CUSTOM_PREFIX) || c.key in user.channelCopies[group].orEmpty()) {
                { vm.removeFromGroup(group, c.key) }
            } else null,
            onGuide = { guideFor = c },
            onLogo = { logoFor = c },
            onDismiss = { actionChannel = null },
        )
    }
    logoFor?.let { c ->
        ChangeLogoDialog(
            channel = c,
            hasPicked = c.key in user.channelLogos,
            onPick = { url -> vm.setChannelLogo(c.key, url); logoFor = null },
            onDismiss = { logoFor = null },
        )
    }
    pickGroupFor?.let { c ->
        PickDialog(
            title = "Add \"${c.name}\" to a group",
            options = user.customGroups.map { it.id to it.name },
            extra = "New group\u2026" to { newGroup = c.key; askNewGroup = true; pickGroupFor = null },
            onPick = { id -> vm.addToGroup(id, c.key); pickGroupFor = null },
            onDismiss = { pickGroupFor = null },
        )
    }
    copyGroupFor?.let { c ->
        PickDialog(
            title = "Copy \"${c.name}\" to",
            options = groups.filter { it.id != c.groupId }.map { it.id to it.title },
            onPick = { id -> vm.copyToGroup(id, c.key); copyGroupFor = null },
            onDismiss = { copyGroupFor = null },
        )
    }
    guideFor?.let { c ->
        val sources by vm.epgChannels.collectAsStateWithLifecycle()
        GuidePickerDialog(
            channelName = c.name,
            sources = sources,
            onPick = { a -> vm.assignGuide(c.key, a); guideFor = null },
            onDismiss = { guideFor = null },
        )
    }
    if (askNewGroup) {
        TextPromptDialog(
            title = "New group",
            initial = "",
            onOk = { name -> if (name.isNotBlank()) vm.createGroup(name, newGroup); newGroup = null; askNewGroup = false },
            onDismiss = { newGroup = null; askNewGroup = false },
        )
    }
    actionGroup?.let { g ->
        GroupActionsDialog(
            title = g.title,
            locked = g.id in user.lockedGroups,
            onRename = { vm.renameGroup(g.id, it) },
            onHide = { vm.setGroupHidden(g.id, true); if (group == g.id) group = ChannelGroup.ALL },
            onLock = {
                if (g.id in user.lockedGroups) vm.setGroupLocked(g.id, false)
                else if (settings.parentalPin.isBlank()) setPinFor = g.id
                else { vm.setGroupLocked(g.id, true); if (group == g.id) group = ChannelGroup.ALL }
            },
            onMove = { delta -> vm.moveGroup(g.id, delta, chips.map { it.id }) },
            onDelete = if (g.id.startsWith(ChannelGroup.CUSTOM_PREFIX)) ({ vm.deleteGroup(g.id); if (group == g.id) group = ChannelGroup.ALL }) else null,
            onDismiss = { actionGroup = null },
        )
    }
    recordFor?.let { c ->
        val current = programs[c.key]?.firstOrNull { it.isLive(now) }
        RecordChannelDialog(
            channel = c,
            currentShow = current?.title,
            minutesLeftInShow = current?.let { ((it.stopMs - now) / 60_000L).toInt() },
            onRecordNow = { minutes, title ->
                vm.recordNow(c, title, minutes)
                android.widget.Toast.makeText(context, "Recording \"$title\"", android.widget.Toast.LENGTH_SHORT).show()
            },
            onSchedule = { title, start, stop ->
                android.widget.Toast.makeText(context, vm.scheduleManual(c, title, start, stop), android.widget.Toast.LENGTH_LONG).show()
            },
            onDismiss = { recordFor = null },
        )
    }
    askPinFor?.let { id ->
        PinDialog(
            title = "Enter your PIN",
            expected = settings.parentalPin,
            onPin = { com.nuvio.app.livetv.parental.LiveTvUnlocks.unlock(id); unlockTick++; group = id; vm.setLastGroup(id); askPinFor = null },
            onDismiss = { askPinFor = null },
        )
    }
    setPinFor?.let { id ->
        PinDialog(
            title = "Set a PIN",
            setting = true,
            onPin = { pin -> vm.setPin(pin); vm.setGroupLocked(id, true); if (group == id) group = ChannelGroup.ALL; setPinFor = null },
            onDismiss = { setPinFor = null },
        )
    }
    if (showHidden) {
        HiddenDialog(
            groups = user.hiddenGroups.map { id -> id to (user.groupNames[id] ?: allChannels.firstOrNull { it.groupId == id }?.group ?: id) },
            channels = allChannels.filter { it.key in user.hiddenChannels },
            onShowGroup = { vm.setGroupHidden(it, false) },
            onShowChannel = { vm.setChannelHidden(it, false) },
            onShowAll = { vm.showAllHidden(); showHidden = false },
            onDismiss = { showHidden = false },
        )
    }

    if (showPlaylists) {
        PlaylistsDialog(
            playlists = playlists,
            epgs = epgs,
            onDismiss = { showPlaylists = false },
            onAdd = { showAdd = true },
            onAddEpg = { showAddEpg = true },
            onRefresh = vm::refresh,
            onRemove = vm::removePlaylist,
            onEnabled = vm::setPlaylistEnabled,
            onRemoveEpg = vm::removeEpg,
            onEpgEnabled = vm::setEpgEnabled,
            accounts = accounts,
            onEdit = vm::updatePlaylist,
        )
    }
    if (showAddEpg) {
        AddEpgDialog(
            onDismiss = { showAddEpg = false },
            onAdd = { name, url -> vm.addEpg(name, url); showAddEpg = false },
        )
    }
    if (showAdd) {
        AddPlaylistDialog(
            onDismiss = { showAdd = false },
            onM3u = { name, url -> vm.addM3u(name, url); showAdd = false; showPlaylists = false },
            onXtream = { name, server, user, pass -> vm.addXtream(name, server, user, pass); showAdd = false; showPlaylists = false },
        )
    }
}

private fun LiveChannel.toRequest(programs: List<EpgProgram>?, now: Long) = liveTvRequest(this, programs, now)

@Composable
private fun Header(
    compact: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onPlaylists: () -> Unit,
    hasHidden: Boolean,
    onHidden: () -> Unit,
    lastChannel: String?,
    onLast: () -> Unit,
    showViewControls: Boolean = false,
    grid: Boolean = false,
    onToggleGrid: () -> Unit = {},
    onAddGroup: () -> Unit = {},
) {
    val viewControls: @Composable () -> Unit = {
        if (showViewControls) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupChip(if (grid) (if (compact) "\u2630" else "\u2630 List") else (if (compact) "\u25A6" else "\u25A6 Grid"), false, onClick = onToggleGrid)
                GroupChip(if (compact) "+" else "+ Group", false, onClick = onAddGroup)
            }
        }
    }
    val search: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = m, placeholder = { Text("Search channels") },
            shape = RoundedCornerShape(12.dp), colors = fieldColors())
    }
    if (compact) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live TV", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                viewControls()
                Spacer(Modifier.weight(1f))
                if (lastChannel != null) TextButton(onClick = onLast) { Text("Last") }
                if (hasHidden) TextButton(onClick = onHidden) { Text("Hidden") }
                TextButton(onClick = onPlaylists) { Text("Sources") }
            }
            search(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Live TV", style = MaterialTheme.typography.headlineSmall)
            viewControls()
            search(Modifier.weight(1f))
            if (lastChannel != null) TextButton(onClick = onLast) { Text("Last: $lastChannel", maxLines = 1) }
            if (hasHidden) TextButton(onClick = onHidden) { Text("Hidden") }
            TextButton(onClick = onPlaylists) { Text("Sources") }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

/** The page tabs (Channels, Guide, Calendar...): red, filled when it's the page you're on. */
@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Accent else Color.Transparent)
            .border(1.dp, Accent, shape)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Accent)
    }
}

@Composable
private fun StatusLine(status: com.nuvio.app.livetv.data.LiveTvStatus) {
    val text = status.error ?: status.message.takeIf { status.loading && !status.quiet } ?: return
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status.loading && status.error == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        if (status.loading && status.error == null) Spacer(Modifier.width(8.dp))
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            color = if (status.error != null) Accent else LocalContentColor.current.copy(alpha = 0.7f),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No channels yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add a playlist link (M3U) or an Xtream login to get your channels and guide.",
                color = LocalContentColor.current.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAdd) { Text("Add a playlist") }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    programs: List<EpgProgram>?,
    now: Long,
    favorite: Boolean,
    onFavorite: (String) -> Unit,
    onLongClick: () -> Unit,
    settings: com.nuvio.app.livetv.model.LiveTvSettings,
    quality: String? = null,
    onClick: () -> Unit,
) {
    val current = programs?.firstOrNull { it.isLive(now) }
    val next = current?.let { c -> programs.firstOrNull { it.startMs >= c.stopMs } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(if (settings.compactRows) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showChannelLogos) Box(
            Modifier.size(width = if (settings.compactRows) 56.dp else 72.dp, height = if (settings.compactRows) 42.dp else 54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            } else {
                Text(channel.name.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color(0xFFB0B0B0))
            }
        }
        if (settings.showChannelLogos) Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (channel.number > 0 && settings.showChannelNumbers) "${channel.number}  " else "") + channel.name,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (quality != null) QualityBadge(quality)
            }
            if (current != null) {
                Text(
                    current.title, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(current.progress(now)).height(3.dp).background(Accent))
                }
                next?.let {
                    Text(
                        "Next: ${it.title}", style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(channel.group, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.55f), maxLines = 1)
            }
        }
        IconButton(onClick = { onFavorite(channel.key) }) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) Color(0xFFFFC107) else LocalContentColor.current.copy(alpha = 0.6f),
            )
        }
    }
}

/** Opens a channel, keeping it going if the stream drops. */
private fun playLive(onPlay: (LiveTvPlayRequest) -> Unit, request: LiveTvPlayRequest) {
    com.nuvio.app.livetv.player.LiveTvReconnect.arm(request.url, request.channelKey)
    onPlay(request)
}

/** "4K", "FHD", "HD" or "SD", small, next to a channel's name. */
@Composable
internal fun QualityBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 8.dp)
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** A channel as a tile for the grid view: its logo, name, and what's on now. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(
    channel: LiveChannel,
    programs: List<EpgProgram>?,
    now: Long,
    favorite: Boolean,
    onLongClick: () -> Unit,
    settings: com.nuvio.app.livetv.model.LiveTvSettings,
    quality: String? = null,
    onClick: () -> Unit,
) {
    val current = programs?.firstOrNull { it.isLive(now) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            if (favorite) Text("\u2605", Modifier.align(Alignment.TopEnd).padding(4.dp), color = Color(0xFFFFC107))
            if (quality != null) Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) { QualityBadge(quality) }
        }
        Text(
            (if (settings.showChannelNumbers) "${channel.number}  " else "") + channel.name,
            Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
        )
        Text(
            current?.title ?: channel.group,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}
