package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class YtVideoInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String?,
    /** The channel's profile picture. */
    val authorImage: String?,
    val subscribers: String?,
    val likeCount: String?,
    val liked: Boolean,
    val disliked: Boolean,
    val subscribed: Boolean,
    val commentsKey: String?,
    val newestCommentsKey: String?,
    val liveChatKey: String?,
    /** The video's chapters, as (title, start in ms). */
    val chapters: List<Pair<String, Long>>
)

data class YtPlaylistRow(val id: String, val title: String, val contains: Boolean)

data class YtComment(
    val author: String,
    val text: String,
    val likes: String?,
    val published: String?,
    /** What opens this comment's replies, if it has any. */
    val replyKey: String?,
    val replyCount: String?
)

data class YtChatLine(val id: String, val author: String, val message: String)

data class YtPlayerState(
    val info: YtVideoInfo? = null,
    val loading: Boolean = false,
    val signedIn: Boolean = false,
    /** A one-line result or problem to show in the panel. */
    val message: String? = null,
    val playlists: List<YtPlaylistRow>? = null,
    val comments: List<YtComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val canLoadMoreComments: Boolean = false,
    /** 0 is Top comments, 1 is Newest first. */
    val commentsSort: Int = 0,
    val replies: List<YtComment> = emptyList(),
    val repliesLoading: Boolean = false,
    val canLoadMoreReplies: Boolean = false
)

/**
 * What you can do about the YouTube video that's playing: like it, subscribe to its channel, save it to a
 * playlist, read its comments, follow its live chat. One per player screen; [load] picks up the video it's about.
 */
class YouTubePlayerViewModel (private val context: Context) : ViewModel() {
    private companion object {
        const val TAG = "YouTubePlayerVM"
        const val CHAT_LINES = 40
    }

    private val _state = MutableStateFlow(YtPlayerState())
    val state: StateFlow<YtPlayerState> = _state.asStateFlow()

    private val _chat = MutableStateFlow<List<YtChatLine>>(emptyList())
    val chat: StateFlow<List<YtChatLine>> = _chat.asStateFlow()
    private var chatSubscription: Disposable? = null

    private val service by lazy {
        GlobalPreferences.instance(context)
        YouTubeServiceManager.instance()
    }
    private val media get() = service.mediaItemService

    private var videoId: String? = null
    private var nextCommentsKey: String? = null
    private var nextRepliesKey: String? = null
    private var commentsGen = 0
    private var repliesGen = 0

    fun load(id: String) {
        if (videoId == id) return
        videoId = id
        nextCommentsKey = null
        nextRepliesKey = null
        stopChat()
        _chat.value = emptyList()
        _state.value = YtPlayerState(loading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val signed = service.signInService.isSigned
                    media.getMetadataObserve(id).blockingFirst() to signed
                }
            }
            result.fold(
                onSuccess = { (meta, signed) -> _state.update { it.copy(info = toInfo(id, meta), loading = false, signedIn = signed) } },
                onFailure = {
                    Log.e(TAG, "couldn't load details for $id", it)
                    _state.update { s -> s.copy(loading = false, message = "Couldn't load this video's details.") }
                }
            )
        }
    }

    private fun toInfo(id: String, m: MediaItemMetadata) = YtVideoInfo(
        videoId = id,
        title = m.title.orEmpty(),
        author = m.author.orEmpty(),
        channelId = m.channelId,
        authorImage = m.authorImageUrl?.takeIf { it.isNotBlank() },
        subscribers = m.subscriberCount,
        likeCount = m.likeCount,
        liked = m.likeStatus == MediaItemMetadata.LIKE_STATUS_LIKE,
        disliked = m.likeStatus == MediaItemMetadata.LIKE_STATUS_DISLIKE,
        subscribed = m.isSubscribed,
        commentsKey = m.commentsKey,
        newestCommentsKey = m.newestCommentsKey,
        liveChatKey = m.liveChatKey?.takeIf { it.isNotBlank() },
        chapters = m.chapters.orEmpty().mapNotNull { c -> c.title?.takeIf { it.isNotBlank() }?.let { it to c.startTimeMs } }
    )

    private fun item(info: YtVideoInfo) = YouTubeMediaItem.fromIds(info.videoId, info.channelId)

    /** Runs a change on YouTube, then [apply]s its effect to what's shown. Signed-out users are told why not. */
    private fun act(okMessage: String, call: (YtVideoInfo) -> Observable<Void>, apply: (YtVideoInfo) -> YtVideoInfo) {
        val info = _state.value.info ?: return
        if (!_state.value.signedIn) {
            _state.update { it.copy(message = "Sign in to YouTube first, with the account button on the YouTube page.") }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { call(info).ignoreElements().blockingAwait() } }
            result.fold(
                onSuccess = { _state.update { s -> s.copy(info = s.info?.let(apply), message = okMessage) } },
                onFailure = {
                    Log.e(TAG, "action failed", it)
                    _state.update { s -> s.copy(message = "That didn't go through. Try again.") }
                }
            )
        }
    }

    fun toggleLike() {
        val liked = _state.value.info?.liked == true
        act(if (liked) "Like removed" else "Liked",
            { if (liked) media.removeLikeObserve(item(it)) else media.setLikeObserve(item(it)) },
            { it.copy(liked = !liked, disliked = false) })
    }

    fun toggleDislike() {
        val disliked = _state.value.info?.disliked == true
        act(if (disliked) "Dislike removed" else "Disliked",
            { if (disliked) media.removeDislikeObserve(item(it)) else media.setDislikeObserve(item(it)) },
            { it.copy(disliked = !disliked, liked = false) })
    }

    fun toggleSubscribe() {
        val subscribed = _state.value.info?.subscribed == true
        val channel = _state.value.info?.channelId
        if (channel == null) {
            _state.update { it.copy(message = "This video has no channel to subscribe to.") }
            return
        }
        act(if (subscribed) "Unsubscribed" else "Subscribed",
            { if (subscribed) media.unsubscribeObserve(channel) else media.subscribeObserve(channel) },
            { it.copy(subscribed = !subscribed) })
    }

    /** Keeps this video's channel off the YouTube page, until you bring it back in Settings. */
    fun hideChannel() {
        val info = _state.value.info ?: return
        val id = info.channelId
        if (id == null) {
            _state.update { it.copy(message = "This video has no channel to hide.") }
            return
        }
        YouTubeSettings.hideChannel(context, id, info.author.ifBlank { "This channel" })
        _state.update { it.copy(message = "Hidden: ${info.author}. Bring it back in Settings, then YouTube.") }
    }

    // ---- playlists ----

    fun loadPlaylists() {
        val info = _state.value.info ?: return
        if (!_state.value.signedIn) {
            _state.update { it.copy(message = "Sign in to YouTube first, with the account button on the YouTube page.") }
            return
        }
        _state.update { it.copy(playlists = null, loading = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { media.getPlaylistsInfoObserve(info.videoId).blockingFirst() } }
            _state.update { s ->
                s.copy(
                    loading = false,
                    playlists = result.getOrNull().orEmpty().map { YtPlaylistRow(it.playlistId.orEmpty(), it.title.orEmpty(), it.isSelected) },
                    message = if (result.isFailure) "Couldn't load your playlists." else null
                )
            }
        }
    }

    fun togglePlaylist(row: YtPlaylistRow) {
        val info = _state.value.info ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val call = if (row.contains) media.removeFromPlaylistObserve(row.id, info.videoId)
                    else media.addToPlaylistObserve(row.id, info.videoId)
                    call.ignoreElements().blockingAwait()
                }
            }
            _state.update { s ->
                if (result.isSuccess) {
                    s.copy(
                        playlists = s.playlists?.map { if (it.id == row.id) it.copy(contains = !row.contains) else it },
                        message = if (row.contains) "Removed from ${row.title}" else "Saved to ${row.title}"
                    )
                } else s.copy(message = "That didn't go through. Try again.")
            }
        }
    }

    // ---- comments ----

    private fun toComment(c: CommentItem) = YtComment(
        author = c.authorName.orEmpty(),
        text = c.message.orEmpty(),
        likes = c.likeCount,
        published = c.publishedDate,
        replyKey = c.nestedCommentsKey?.takeIf { it.isNotBlank() },
        replyCount = c.replyCount?.takeIf { it.isNotBlank() }
    )

    /** Loads the comments in the chosen order. Does nothing if that order is already showing. */
    fun loadComments(newest: Boolean = _state.value.commentsSort == 1) {
        val info = _state.value.info ?: return
        val sort = if (newest) 1 else 0
        if (_state.value.commentsSort == sort && _state.value.comments.isNotEmpty()) return
        val key = if (newest) info.newestCommentsKey else info.commentsKey
        if (key.isNullOrBlank()) {
            _state.update {
                it.copy(message = if (newest) "Newest first isn't available for this video." else "Comments are off for this video.")
            }
            return
        }
        nextCommentsKey = null
        val gen = ++commentsGen
        _state.update { it.copy(comments = emptyList(), commentsSort = sort, canLoadMoreComments = false, message = null) }
        fetchComments(key, gen)
    }

    fun loadMoreComments() {
        val key = nextCommentsKey ?: return
        if (_state.value.commentsLoading) return
        fetchComments(key, commentsGen)
    }

    private fun fetchComments(key: String, gen: Int) {
        _state.update { it.copy(commentsLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { service.commentsService.getCommentsObserve(key).blockingFirst() } }
            // An answer for an order you've since left isn't wanted.
            if (gen != commentsGen) return@launch
            val group = result.getOrNull()
            nextCommentsKey = group?.nextCommentsKey
            _state.update { s ->
                s.copy(
                    commentsLoading = false,
                    comments = s.comments + group?.comments.orEmpty().filter { !it.isEmpty }.map(::toComment),
                    canLoadMoreComments = group?.nextCommentsKey != null,
                    message = if (result.isFailure) "Couldn't load comments." else null
                )
            }
        }
    }

    // ---- replies ----

    fun loadReplies(key: String) {
        nextRepliesKey = null
        val gen = ++repliesGen
        _state.update { it.copy(replies = emptyList(), canLoadMoreReplies = false, message = null) }
        fetchReplies(key, gen)
    }

    fun loadMoreReplies() {
        val key = nextRepliesKey ?: return
        if (_state.value.repliesLoading) return
        fetchReplies(key, repliesGen)
    }

    fun clearReplies() {
        repliesGen++
        nextRepliesKey = null
        _state.update { it.copy(replies = emptyList(), repliesLoading = false, canLoadMoreReplies = false) }
    }

    private fun fetchReplies(key: String, gen: Int) {
        _state.update { it.copy(repliesLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { service.commentsService.getCommentsObserve(key).blockingFirst() } }
            if (gen != repliesGen) return@launch
            val group = result.getOrNull()
            nextRepliesKey = group?.nextCommentsKey
            _state.update { s ->
                s.copy(
                    repliesLoading = false,
                    replies = s.replies + group?.comments.orEmpty().filter { !it.isEmpty }.map(::toComment),
                    canLoadMoreReplies = group?.nextCommentsKey != null,
                    message = if (result.isFailure) "Couldn't load replies." else null
                )
            }
        }
    }

    // ---- live chat ----

    /** Starts following the live chat of a live stream. Messages arrive in [chat], newest last. */
    fun startChat() {
        val key = _state.value.info?.liveChatKey ?: return
        if (chatSubscription?.isDisposed == false) return
        chatSubscription = service.liveChatService.openLiveChatObserve(key)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { item ->
                    val line = YtChatLine(item.id.orEmpty(), item.authorName.orEmpty(), item.message.orEmpty())
                    if (line.message.isNotBlank()) {
                        _chat.update { (it + line).takeLast(CHAT_LINES) }
                    }
                },
                { Log.w(TAG, "live chat ended", it) }
            )
    }

    fun stopChat() {
        chatSubscription?.dispose()
        chatSubscription = null
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        stopChat()
    }
}
