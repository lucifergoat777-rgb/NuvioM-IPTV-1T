package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YtKind { VIDEO, CHANNEL, PLAYLIST }

/** One card. Kept separate from MediaServiceCore's types so the UI never touches them. */
data class YtItem(
    val kind: YtKind,
    val videoId: String?,
    val title: String,
    val author: String?,
    val thumbnail: String?,
    val badge: String?,
    val isLive: Boolean,
    /** A Short: rendered as a tall 9:16 card instead of a 16:9 one. */
    val isShorts: Boolean = false,
    val channelId: String?,
    /** How much YouTube says you've watched (0-100), from your account. */
    val percentWatched: Int,
    internal val source: MediaItem
)

data class YtRow(
    val title: String,
    val items: List<YtItem>,
    /** Where this row's next page comes from, so scrolling to its end can load more. */
    internal val group: MediaGroup? = null,
    val canLoadMore: Boolean = false,
)

/** The feeds along the top. Grid feeds are one long list; the others are rows of videos. */
enum class YtTab(val title: String, val needsSignIn: Boolean = false, val grid: Boolean = false) {
    // No Trending: YouTube retired that page, and its requests are now refused.
    HOME("Home"),
    SUBSCRIPTIONS("Subscriptions", needsSignIn = true, grid = true),
    HISTORY("History", needsSignIn = true, grid = true),
    PLAYLISTS("Playlists", needsSignIn = true, grid = true),
    LIVE("Live"),
    MUSIC("Music"),
    GAMING("Gaming"),
    NEWS("News"),
    SPORTS("Sports"),
    SHORTS("Shorts", grid = true),
    CHANNELS("Channels", needsSignIn = true, grid = true)
}

data class YtUiState(
    val tab: YtTab = YtTab.HOME,
    val signedIn: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val rows: List<YtRow> = emptyList(),
    val grid: List<YtItem> = emptyList(),
    val canLoadMore: Boolean = false,
    /** Set while looking at a channel, playlist or search opened from a feed; Back returns to the feed. */
    val subTitle: String? = null
) {
    val isEmpty get() = rows.isEmpty() && grid.isEmpty()
}

/** What the player needs to start a video. */
data class YtPlayable(
    val url: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val headers: Map<String, String> = emptyMap(),
    /** Where to start, in milliseconds. */
    val startPositionMs: Long = 0L,
    /** When this was looked up. YouTube's stream links stop working after a few hours. */
    val lookedUpAt: Long = android.os.SystemClock.elapsedRealtime()
) {
    /** Still safe to play from, without asking YouTube again. */
    val isFresh: Boolean get() = android.os.SystemClock.elapsedRealtime() - lookedUpAt < 20 * 60 * 1000L
}

/**
 * The YouTube section's data. Everything here is shown only inside the YouTube page; nothing is
 * handed to Home, Continue Watching or the library.
 */
class YouTubeViewModel (private val context: Context) : ViewModel() {
    private companion object {
        const val TAG = "YouTubeVM"
        const val MAX_CLIENT_SWITCHES = 8
        const val PREFETCH_DELAY_MS = 250L
        const val MAX_CACHED = 24
        /** Stops endless scrolling from growing a feed without limit; plenty to browse. */
        const val MAX_ROW_ITEMS = 300
        const val MAX_GRID_ITEMS = 800
        /** How much of a video counts as watched for hiding it. */
        const val WATCHED_PERCENT = 90
    }

    private val _state = MutableStateFlow(YtUiState())
    val state: StateFlow<YtUiState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val service by lazy {
        // MediaServiceCore reads its prefs through this singleton, so it must exist first.
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance()
    }
    private val content: ContentService get() = service.contentService

    /** The page being shown: pages opened from it go on top, and Back takes them off. */
    private class Page(val state: YtUiState, val group: MediaGroup?, val channelBlockOn: Boolean)
    private val backStack = ArrayDeque<Page>()
    private var currentGroup: MediaGroup? = null
    private var loadJob: Job? = null

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    init { selectTab(YouTubeSettings.lastTab(context)) }

    // ---- feeds ----

    fun selectTab(tab: YtTab) {
        YouTubeContentRegion.apply(context)
        backStack.clear()
        filtersOn = tab != YtTab.HISTORY && tab != YtTab.SHORTS
        channelBlockOn = true
        YouTubeSettings.setLastTab(context, tab)
        _state.update { YtUiState(tab = tab, signedIn = it.signedIn, loading = true) }
        val rowsSource: (() -> io.reactivex.Observable<List<MediaGroup>>)? = when (tab) {
            YtTab.HOME -> { { content.homeObserve } }
            YtTab.LIVE -> { { content.liveObserve } }
            YtTab.MUSIC -> { { content.musicObserve } }
            YtTab.GAMING -> { { content.gamingObserve } }
            YtTab.NEWS -> { { content.newsObserve } }
            YtTab.SPORTS -> { { content.sportsObserve } }
            else -> null
        }
        if (rowsSource != null) {
            loadRows(tab.title, rowsSource)
            return
        }
        load(tab.title) {
            val signed = runCatching { service.signInService.isSigned }.getOrDefault(false)
            _state.update { it.copy(signedIn = signed) }
            if (tab.needsSignIn && !signed) return@load Feed.Nothing
            withRetry {
            when (tab) {
                YtTab.HOME -> rows(content.homeObserve.blockingFirst())
                YtTab.LIVE -> rows(content.liveObserve.blockingFirst())
                YtTab.MUSIC -> rows(content.musicObserve.blockingFirst())
                YtTab.GAMING -> rows(content.gamingObserve.blockingFirst())
                YtTab.NEWS -> rows(content.newsObserve.blockingFirst())
                YtTab.SPORTS -> rows(content.sportsObserve.blockingFirst())
                YtTab.SUBSCRIPTIONS -> grid(content.subscriptionsObserve.blockingFirst())
                YtTab.SHORTS -> grid(content.shortsObserve.blockingFirst())
                YtTab.CHANNELS -> grid(content.subscribedChannelsObserve.blockingFirst())
                YtTab.HISTORY -> grid(content.historyObserve.blockingFirst())
                YtTab.PLAYLISTS -> grid(content.playlistsObserve.blockingFirst())
            }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        filtersOn = true
        channelBlockOn = true
        pushPage()
        _state.update {
            it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), canLoadMore = false,
                subTitle = "Results for \"${query.trim()}\"")
        }
        loadRows(query) { content.getSearchObserve(query) }
    }

    /** Opens a channel or playlist on top of the current page. */
    fun open(item: YtItem) {
        filtersOn = true
        channelBlockOn = item.kind != YtKind.CHANNEL
        pushPage()
        _state.update {
            it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), canLoadMore = false,
                subTitle = item.title)
        }
        load(item.title) {
            when (item.kind) {
                // A channel from the Channels list opens its uploads by its reload key; any other by its id.
                YtKind.CHANNEL ->
                    if (item.channelId == null && item.source.reloadPageKey != null) grid(content.getGroupObserve(item.source).blockingFirst())
                    else rows(content.getChannelObserve(item.source).blockingFirst())
                else -> grid(content.getGroupObserve(item.source).blockingFirst())
            }
        }
    }

    /** Opens a channel by its id, on top of the current page. Used from the player's YouTube panel. */
    fun openChannel(channelId: String, name: String) {
        pushPage()
        filtersOn = true
        channelBlockOn = false
        _state.update {
            it.copy(loading = true, error = null, rows = emptyList(), grid = emptyList(), canLoadMore = false, subTitle = name)
        }
        loadRows(name) { content.getChannelObserve(channelId) }
    }

    private fun pushPage() {
        // A page that's still loading has nothing to come back to.
        if (!_state.value.loading) backStack.addLast(Page(_state.value, currentGroup, channelBlockOn))
    }

    /** Takes the top page off. Returns false when there's nothing to go back to. */
    fun back(): Boolean {
        val page = backStack.removeLastOrNull() ?: return false
        loadJob?.cancel()
        currentGroup = page.group
        channelBlockOn = page.channelBlockOn
        _state.value = page.state.copy(loading = false)
        return true
    }

    private val rowsLoading: MutableSet<MediaGroup> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Loads the next page of row [index], when you're getting near its end. */
    fun loadMoreRow(index: Int) {
        val row = _state.value.rows.getOrNull(index) ?: return
        val group = row.group ?: return
        if (!row.canLoadMore || !rowsLoading.add(group)) return
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { content.continueGroupObserve(group).blockingFirst() }.getOrNull()
            }
            val more = next?.let(::toItems).orEmpty()
            _state.update { s ->
                s.copy(rows = s.rows.map { r ->
                    if (r.group === group) r.copy(
                        items = r.items + more,
                        group = next ?: group,
                        canLoadMore = next?.nextPageKey != null && more.isNotEmpty() && r.items.size + more.size < MAX_ROW_ITEMS
                    ) else r
                })
            }
            rowsLoading.remove(group)
            fetchDeArrow()
        }
    }

    /** The videos of the channel highlighted in the Channels list, shown beside it. */
    data class ChannelPreview(
        val title: String = "",
        val items: List<YtItem> = emptyList(),
        val loading: Boolean = false,
        val canLoadMore: Boolean = false,
        internal val group: MediaGroup? = null
    )

    private val _preview = MutableStateFlow(ChannelPreview())
    val preview: StateFlow<ChannelPreview> = _preview.asStateFlow()
    private val previews = object : java.util.LinkedHashMap<String, ChannelPreview>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChannelPreview>?) = size > 24
    }.let { java.util.Collections.synchronizedMap(it) }
    private var previewJob: Job? = null

    /** Shows [channel]'s videos beside the list. Waits a moment, so scrolling past channels doesn't ask for each one. */
    fun previewChannel(channel: YtItem) {
        previewJob?.cancel()
        previews[channel.title]?.let { _preview.value = it; return }
        _preview.value = ChannelPreview(title = channel.title, loading = true)
        previewJob = viewModelScope.launch {
            delay(350)
            val group = withContext(Dispatchers.IO) {
                runCatching { content.getGroupObserve(channel.source).blockingFirst() }.getOrNull()
            }
            val result = ChannelPreview(
                title = channel.title,
                items = group?.let(::toItems).orEmpty(),
                canLoadMore = group?.nextPageKey != null,
                group = group
            )
            if (group != null) previews[channel.title] = result
            _preview.value = result
            fetchDeArrow()
        }
    }

    fun previewMore() {
        val cur = _preview.value
        val group = cur.group ?: return
        if (cur.loading || !cur.canLoadMore) return
        _preview.value = cur.copy(loading = true)
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { content.continueGroupObserve(group).blockingFirst() }.getOrNull()
            }
            val more = next?.let(::toItems).orEmpty()
            val merged = cur.copy(
                items = cur.items + more,
                loading = false,
                canLoadMore = next?.nextPageKey != null && more.isNotEmpty() && cur.items.size + more.size < MAX_ROW_ITEMS,
                group = next ?: group
            )
            previews[cur.title] = merged
            if (_preview.value.title == cur.title) _preview.value = merged
            fetchDeArrow()
        }
    }

    fun loadMore() {
        val group = currentGroup ?: return
        val s = _state.value
        if (s.loading || s.loadingMore || !s.canLoadMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { content.continueGroupObserve(group).blockingFirst() }.getOrNull()
            }
            val more = next?.let(::toItems).orEmpty()
            currentGroup = next ?: group
            _state.update {
                it.copy(
                    grid = it.grid + more,
                    loadingMore = false,
                    canLoadMore = next?.nextPageKey != null && more.isNotEmpty() && it.grid.size + more.size < MAX_GRID_ITEMS
                )
            }
            fetchDeArrow()
        }
    }

    /**
     * Runs a feed request, trying again if YouTube answers with nothing or an error. Right after a
     * clean install the first answers can be empty until the engine has set up its session, so the
     * first retry also asks it to refresh that.
     */
    private fun toRows(groups: List<MediaGroup>?): List<YtRow> =
        groups.orEmpty().map { YtRow(it.title.orEmpty(), toItems(it), it, it.nextPageKey != null) }.filter { it.items.isNotEmpty() }

    /**
     * Loads a page made of rows. YouTube sends it in batches of rows, and SmartTube keeps taking batches until
     * there are none left; so the first batch shows straight away, and the rest are added underneath as they come.
     */
    private fun loadRows(label: String, source: () -> io.reactivex.Observable<List<MediaGroup>>) {
        refreshHidden()
        loadJob?.cancel()
        currentGroup = null
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val signed = runCatching { service.signInService.isSigned }.getOrDefault(false)
                    _state.update { it.copy(signedIn = signed) }
                    val tab = _state.value.tab
                    if (_state.value.subTitle == null && tab.needsSignIn && !signed) return@runCatching
                    var got = false
                    var lastError: Throwable? = null
                    for (attempt in 0 until 3) {
                        try {
                            source().blockingForEach { groups ->
                                val batch = toRows(groups)
                                if (batch.isEmpty()) return@blockingForEach
                                val firstEver = !got
                                got = true
                                _state.update { s ->
                                    s.copy(loading = false, grid = emptyList(), canLoadMore = false, rows = if (firstEver) batch else s.rows + batch)
                                }
                                if (firstEver) {
                                    // The first lookup after the app starts is slow, so do it while you're still choosing.
                                    batch.firstOrNull()?.items?.firstOrNull { it.kind == YtKind.VIDEO }?.let { lookup(it) }
                                }
                            }
                            lastError = null
                            break
                        } catch (e: Throwable) {
                            lastError = e
                            if (got) break // what's already shown stays
                            if (attempt == 0) runCatching { service.refreshCacheIfNeeded() }
                            if (attempt < 2) Thread.sleep(1500L * (attempt + 1))
                        }
                    }
                    lastError?.let { if (!got) throw it }
                }
            }
            result.onFailure { e ->
                Log.e(TAG, "couldn't load $label", e)
                _state.update {
                    if (e is NoSuchElementException) it.copy(loading = false)
                    else it.copy(loading = false, error = e.message ?: "Couldn't load $label")
                }
            }
            _state.update { it.copy(loading = false) }
            fetchDeArrow()
        }
    }

    private fun <T> withRetry(block: () -> T): T {
        var last: Throwable? = null
        for (attempt in 0 until 3) {
            try {
                return block()
            } catch (e: Throwable) {
                last = e
                if (attempt == 0) runCatching { service.refreshCacheIfNeeded() }
                if (attempt < 2) Thread.sleep(1500L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("No answer from YouTube")
    }

    private sealed interface Feed {
        data object Nothing : Feed
        class Rows(val rows: List<YtRow>) : Feed
        class Grid(val group: MediaGroup) : Feed
    }

    private fun rows(groups: List<MediaGroup>?): Feed =
        Feed.Rows(groups.orEmpty().map { YtRow(it.title.orEmpty(), toItems(it), it, it.nextPageKey != null) }.filter { it.items.isNotEmpty() })

    private fun grid(group: MediaGroup?): Feed = if (group == null) Feed.Nothing else Feed.Grid(group)

    private fun load(label: String, fetch: () -> Feed) {
        refreshHidden()
        loadJob?.cancel()
        currentGroup = null
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetch() } }
            result.fold(
                onSuccess = { feed ->
                    when (feed) {
                        Feed.Nothing -> _state.update { it.copy(loading = false) }
                        is Feed.Rows -> {
                            _state.update { it.copy(loading = false, rows = feed.rows, grid = emptyList(), canLoadMore = false) }
                            // The first lookup after the app starts is slow (YouTube's player scripts
                            // load then), so do it now, while you're still choosing.
                            feed.rows.firstOrNull()?.items?.firstOrNull { it.kind == YtKind.VIDEO }?.let { lookup(it) }
                        }
                        is Feed.Grid -> {
                            currentGroup = feed.group
                            _state.update {
                                it.copy(
                                    loading = false,
                                    rows = emptyList(),
                                    grid = toItems(feed.group),
                                    canLoadMore = feed.group.nextPageKey != null
                                )
                            }
                        }
                    }
                    fetchDeArrow()
                },
                onFailure = { e ->
                    Log.e(TAG, "couldn't load $label", e)
                    _state.update {
                        // "Nothing came back" is an empty page, not a broken connection.
                        if (e is NoSuchElementException) it.copy(loading = false)
                        else it.copy(loading = false, error = e.message ?: "Couldn't load $label")
                    }
                }
            )
        }
    }

    // ---- DeArrow: community titles and thumbnails ----

    private val deArrow = object : java.util.LinkedHashMap<String, Pair<String?, String?>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String?, String?>>?) = size > 1500
    }.let { java.util.Collections.synchronizedMap(it) }
    private val deArrowAsked: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** [item] with its DeArrow title and thumbnail, if they've been looked up and you've left them on. */
    private fun deArrowCached(item: YtItem): YtItem {
        val id = item.videoId ?: return item
        val (title, thumb) = deArrow[id] ?: return item
        return item.copy(
            title = title?.takeIf { it.isNotBlank() && YouTubeSettings.deArrowTitles(context) } ?: item.title,
            thumbnail = thumb?.takeIf { it.isNotBlank() && YouTubeSettings.deArrowThumbnails(context) } ?: item.thumbnail
        )
    }

    /** Looks up DeArrow data for what's on screen, and swaps it in as each answer arrives. */
    private fun fetchDeArrow() {
        if (!YouTubeSettings.deArrowTitles(context) && !YouTubeSettings.deArrowThumbnails(context)) return
        val s = _state.value
        val ids = (s.rows.flatMap { it.items } + s.grid).mapNotNull { it.videoId }.filter { deArrowAsked.add(it) }.take(60)
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                service.mediaItemService.getDeArrowDataObserve(ids).blockingForEach { d ->
                    val id = d.videoId ?: return@blockingForEach
                    deArrow[id] = d.title to d.thumbnailUrl
                    _state.update { st ->
                        st.copy(
                            rows = st.rows.map { r -> r.copy(items = r.items.map { if (it.videoId == id) deArrowCached(it) else it }) },
                            grid = st.grid.map { if (it.videoId == id) deArrowCached(it) else it }
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "DeArrow lookup failed", it) }
        }
    }

    /** Feed filters apply everywhere except History, which is meant to show what you've watched. */
    private var filtersOn = true
    /** Hidden channels are left out of feeds, but not out of that channel's own page. */
    private var channelBlockOn = true
    private var hiddenWords: List<String> = emptyList()
    private var hiddenChannelIds: Set<String> = emptySet()

    private fun refreshHidden() {
        hiddenWords = YouTubeSettings.hiddenKeywords(context)
        hiddenChannelIds = YouTubeSettings.hiddenChannels(context).map { it.first }.toSet()
    }

    private fun toItems(g: MediaGroup): List<YtItem> = g.mediaItems.orEmpty().filter(::allowed).mapNotNull(::toItem)

    /** Whether your filters in Settings let [i] through. Channels and playlists always do. */
    private fun allowed(i: MediaItem): Boolean {
        if (!filtersOn) return true
        if (i.type == MediaItem.TYPE_CHANNEL || i.type == MediaItem.TYPE_PLAYLIST) return true
        if (i.isShorts && YouTubeSettings.hideShorts(context)) return false
        if (i.isUpcoming && YouTubeSettings.hideUpcoming(context)) return false
        if (hiddenWords.isNotEmpty()) {
            val text = "${i.title.orEmpty()} ${i.author.orEmpty()}".lowercase()
            if (hiddenWords.any { it in text }) return false
        }
        if (channelBlockOn && i.channelId != null && i.channelId in hiddenChannelIds) return false
        if (YouTubeSettings.hideWatched(context)) {
            val watched = maxOf(i.percentWatched, YouTubeProgress.localPercent(context, i.videoId))
            if (watched >= WATCHED_PERCENT) return false
        }
        return true
    }

    private fun toItem(i: MediaItem): YtItem? {
        val kind = when {
            i.type == MediaItem.TYPE_CHANNEL -> YtKind.CHANNEL
            i.type == MediaItem.TYPE_PLAYLIST -> YtKind.PLAYLIST
            // A subscribed channel in the Channels list: no video, opened through its reload key.
            i.videoId == null && i.reloadPageKey != null -> YtKind.CHANNEL
            else -> YtKind.VIDEO
        }
        if (kind == YtKind.VIDEO && i.videoId == null) return null
        val base = YtItem(
            kind = kind,
            videoId = i.videoId,
            title = i.title.orEmpty(),
            author = i.author ?: i.secondTitle?.toString(),
            thumbnail = i.cardImageUrl,
            badge = i.badgeText,
            isLive = i.isLive,
            isShorts = i.isShorts,
            channelId = i.channelId,
            percentWatched = i.percentWatched.coerceIn(0, 100),
            source = i
        )
        return deArrowCached(base)
    }

    // ---- playback ----

    private val resolved = java.util.concurrent.ConcurrentHashMap<String, YtPlayable>()
    private var prefetchJob: Job? = null
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, Deferred<Result<YtPlayable>>>()

    /** Looks up the stream details for [video]; slow (several requests), so call off the main thread. */
    private fun resolveBlocking(video: YtItem): YtPlayable {
        val videoId = video.videoId ?: error("Not a video")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        fun since() = android.os.SystemClock.elapsedRealtime() - startedAt
        var info = service.mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
        Log.i(TAG, "first lookup $videoId client=${info.clientInfo?.clientName} at ${since()}ms")
        // YouTube answers some client types with SABR only (no plain stream URLs), which the stock
        // player can't read. Step through the engine's other clients until one returns streams we can play.
        var attempts = 0
        while (!playable(info) && attempts++ < MAX_CLIENT_SWITCHES) {
            Log.i(TAG, "client ${info.clientInfo?.clientName} gave no plain streams " +
                "(sabr=${info.containsSabrFormats()} unplayable=${info.isUnplayable}); switching")
            service.switchNextClientNow()
            info = service.mediaItemService.getFormatInfoObserve(videoId).blockingFirst()
            Log.i(TAG, "lookup with client=${info.clientInfo?.clientName} at ${since()}ms")
        }
        if (info.isUnplayable) error(friendlyReason(info.playabilityReason))
        Log.i(TAG, "formats video=$videoId dash=${info.containsDashFormats()} " +
            "sabr=${info.containsSabrFormats()} hls=${info.containsHlsUrl()} " +
            "adaptive=${info.adaptiveFormats?.size} live=${info.isLive} client=${info.clientInfo?.clientName}")
        // Live streams come as HLS. Everything else is separate video and audio streams, which we
        // describe in a DASH manifest so the player can offer every quality. A single combined
        // stream (one low quality) is the last resort.
        val url = info.hlsManifestUrl?.takeIf { info.isLive }
            ?: dashUrl(videoId, info)
            ?: info.hlsManifestUrl
            ?: info.dashManifestUrl
            ?: info.urlFormats?.firstOrNull()?.url
            ?: error("YouTube didn't return a stream for this video. Try again in a moment.")
        // For casting: a Cast device can't open the phone's local manifest, so remember the video's own
        // single stream (picture and sound together, the best of them) to send it instead.
        info.urlFormats?.filter { !it.url.isNullOrBlank() && (it.mimeType ?: "").startsWith("video/mp4") }
            ?.maxByOrNull { it.height }?.url?.let { YouTubeMpdServer.registerCast(url, it) }
        Log.i(TAG, "ready $videoId after ${since()}ms")
        // Stream URLs are tied to the client that asked for them, so the player has to present
        // the same user-agent.
        val headers = info.clientInfo?.userAgent?.takeIf { it.isNotBlank() }
            ?.let { mapOf("User-Agent" to it) }.orEmpty()
        return YtPlayable(url, video.title, video.thumbnail, videoId, headers)
    }

    /** One lookup per video at a time: hovering a card and then pressing OK share the same request. */
    private fun lookup(video: YtItem): Deferred<Result<YtPlayable>> {
        val id = video.videoId ?: return CompletableDeferred(Result.failure(IllegalStateException("Not a video")))
        resolved[id]?.takeIf { it.isFresh }?.let { return CompletableDeferred(Result.success(it)) }
        return inflight.getOrPut(id) {
            viewModelScope.async(Dispatchers.IO) {
                runCatching { resolveBlocking(video) }.also { r ->
                    r.getOrNull()?.let {
                        if (resolved.size >= MAX_CACHED) resolved.clear()
                        resolved[id] = it
                    }
                    inflight.remove(id)
                }
            }
        }
    }

    /** The stream for a feed preview, or null when it can't be had (live streams are never previewed). */
    suspend fun previewStream(item: YtItem): YtPlayable? {
        if (item.kind != YtKind.VIDEO || item.isLive || item.videoId == null) return null
        return lookup(item).await().getOrNull()
    }

    /**
     * Starts looking up [item]'s stream while its card is highlighted, so pressing OK is instant.
     * Waits a moment first so scrolling past cards doesn't fire a request for each one.
     */
    fun prefetch(item: YtItem) {
        val id = item.videoId ?: return
        if (item.kind != YtKind.VIDEO || resolved[id]?.isFresh == true) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(PREFETCH_DELAY_MS)
            lookup(item)
        }
    }

    /** What plays after the video that's on now: the rest of the list it was picked from, then YouTube's pick. */
    private var queue: List<YtItem> = emptyList()
    private val played = HashSet<String>()

    /** What will play after the video on now, once it's been picked. Shown as the "up next" card. */
    private val _upNext = MutableStateFlow<YtItem?>(null)
    val upNext: StateFlow<YtItem?> = _upNext.asStateFlow()
    private var upNextFor: String? = null

    /**
     * Picks what plays after [currentVideoId] ahead of time, and starts looking up its stream, so the
     * card can show it and the switch is instant when the video ends.
     */
    fun prepareNext(currentVideoId: String) {
        if (upNextFor == currentVideoId) return
        upNextFor = currentVideoId
        _upNext.value = null
        viewModelScope.launch {
            val next = pickNext(currentVideoId)
            if (upNextFor == currentVideoId) _upNext.value = next
            next?.let { lookup(it) }
        }
    }

    /** The video after [currentVideoId]: the rest of the list it came from, then YouTube's pick. */
    private suspend fun pickNext(currentVideoId: String): YtItem? {
        played += currentVideoId
        val fromList = queue.dropWhile { it.videoId != currentVideoId }.drop(1)
            .firstOrNull { it.kind == YtKind.VIDEO && !it.isLive && it.videoId !in played }
        return fromList ?: withContext(Dispatchers.IO) { runCatching { upNext(currentVideoId) }.getOrNull() }
    }

    /**
     * Plays the video after [currentVideoId], and calls [onReady] with it on the main thread, or
     * [onNone] if there isn't one. Used when a video ends and autoplay is on.
     */
    fun playNext(currentVideoId: String, onReady: (YtPlayable) -> Unit, onNone: () -> Unit) {
        viewModelScope.launch {
            // The one the card showed, if it was picked in time.
            val next = _upNext.value?.takeIf { upNextFor == currentVideoId } ?: pickNext(currentVideoId)
            if (next == null) {
                onNone()
                return@launch
            }
            lookup(next).await().fold(
                onSuccess = { onReady(it) },
                onFailure = { onNone() }
            )
        }
    }

    /** YouTube's own "up next", or the first suggestion that hasn't played yet. */
    private fun upNext(currentVideoId: String): YtItem? {
        val meta = service.mediaItemService.getMetadataObserve(currentVideoId).blockingFirst()
        val candidates = listOfNotNull(meta.nextVideo) + meta.suggestions.orEmpty().flatMap { it.mediaItems.orEmpty() }
        val items = candidates.filter(::allowed).mapNotNull(::toItem).filter { it.kind == YtKind.VIDEO && !it.isLive && it.videoId !in played }
        // Whatever follows keeps playing on from this list.
        queue = listOfNotNull(toItem(candidates.firstOrNull() ?: return null)) + items
        return items.firstOrNull()
    }

    /** Where to start: if your account says you stopped part-way through, resume from there. */
    private fun resumeMs(item: YtItem): Long {
        val durationMs = item.source.durationMs
        if (item.isLive || durationMs <= 0) return 0L
        // This device's own place wins (nearly finished or barely started means from the top); then the account's.
        YouTubeProgress.localState(context, item.videoId)?.let { (pos, dur) ->
            return if ((pos * 100 / dur).toInt() in 3..94) pos else 0L
        }
        if (item.percentWatched !in 3..94) return 0L
        return durationMs * item.percentWatched / 100
    }

    /** Plays a video, or opens a channel or playlist. [onReady] runs on the main thread. */
    fun activate(item: YtItem, from: List<YtItem>, onReady: (YtPlayable) -> Unit) {
        if (item.kind != YtKind.VIDEO) {
            open(item)
            return
        }
        val startMs = resumeMs(item)
        // What was on screen with it becomes the list that plays on from here.
        queue = from.filter { it.kind == YtKind.VIDEO }
        played.clear()
        prefetchJob?.cancel()
        viewModelScope.launch {
            lookup(item).await().fold(
                onSuccess = { onReady(it.copy(startPositionMs = startMs)) },
                onFailure = { _error.value = it.message ?: "Couldn't play this video" }
            )
        }
    }

    /** Turns YouTube's reason for refusing a video into something that says what to do. */
    private fun friendlyReason(reason: String?): String {
        val r = reason.orEmpty().lowercase()
        val signedIn = runCatching { service.signInService.isSigned }.getOrDefault(false)
        return when {
            "age" in r || "confirm your age" in r || "inappropriate" in r ->
                if (signedIn) "This video is age-restricted, and your account can't play it."
                else "This video is age-restricted. Sign in to YouTube to watch it."
            "country" in r || "region" in r || "location" in r -> "This video isn't available in your country."
            "private" in r -> "This video is private."
            "member" in r || "join this channel" in r -> "This video is for channel members only."
            "premiere" in r || "will begin" in r || "live event" in r -> "This video hasn't started yet."
            "copyright" in r -> "This video was removed over a copyright claim."
            "removed" in r || "terminated" in r || "no longer available" in r || "unavailable" in r ->
                "This video has been removed or isn't available."
            "sign in" in r -> "YouTube wants you to sign in to watch this one."
            reason.isNullOrBlank() -> "This video can't be played."
            else -> reason
        }
    }

    private fun playable(info: MediaItemFormatInfo): Boolean =
        !info.isUnplayable && (info.containsDashFormats() || (info.isLive && info.containsHlsUrl()))

    private fun dashUrl(videoId: String, info: MediaItemFormatInfo): String? {
        if (!info.containsDashFormats()) return null
        val mpd = runCatching { info.createMpdStream()?.use { it.readBytes() } }.getOrNull()
        Log.i(TAG, "mpd bytes=${mpd?.size}")
        return mpd?.takeIf { it.isNotEmpty() }?.let { YouTubeMpdServer.publish(videoId, it) }
    }

    // ---- search suggestions and recent searches ----

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private var suggestJob: Job? = null

    /** What to offer under the search box for [text]; with nothing typed, your recent searches. */
    fun suggest(text: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            val found = withContext(Dispatchers.IO) {
                runCatching { content.getSearchTagsObserve(text.trim()).blockingFirst() }.getOrNull().orEmpty()
            }
            _suggestions.value = found.filter { it.isNotBlank() }.take(8)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { content.clearSearchHistory() }
            _suggestions.value = emptyList()
        }
    }

    fun clearError() { _error.value = null }
}
