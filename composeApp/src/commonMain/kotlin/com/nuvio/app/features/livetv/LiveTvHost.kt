package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the Live TV page hands back when you pick a channel. */
data class LiveTvPlayRequest(
    val url: String,
    val title: String,
    val poster: String?,
    val channelKey: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitle: String? = null,
    /** A programme replayed from the provider's archive: it has a length, unlike a live channel. */
    val catchup: Boolean = false,
    /** Start this far in (a buffer being rewound), in milliseconds. */
    val startPositionMs: Long? = null,
)

/**
 * Live TV lives in Android-only code (playlists, the guide database). The Android app sets [screen]
 * at start-up; on other platforms it stays empty and the tab is not shown.
 */
/** Set by the player while a channel is on: the channel buttons, and what's on now. */
class LiveTvPlayerAction(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val nowPlaying: String?,
    val onChannels: (() -> Unit)? = null,
    val onSleep: (() -> Unit)? = null,
    val onRewind: (() -> Unit)? = null,
)

/** "Go live" while watching behind live (a paused channel's buffer); null otherwise. */
val LocalLiveTvGoLive = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

val LocalLiveTvPlayerAction = androidx.compose.runtime.compositionLocalOf<LiveTvPlayerAction?> { null }

object LiveTvHost {
    /** Replaces the player with another channel. Set by the app shell, which owns navigation. */
    var openChannel: ((LiveTvPlayRequest) -> Unit)? = null

    /** Goes to the channel [delta] steps from [channelKey] in your list (1 next, -1 previous). */
    var zap: ((channelKey: String, delta: Int) -> Unit)? = null

    /** The programme on now for a channel, from the guide; null when there is none. */
    var nowPlaying: (@Composable (channelKey: String) -> String?)? = null

    /** The Live TV settings page, drawn inside Nuvio's settings. */
    var settingsContent: (@Composable () -> Unit)? = null

    /** The channel list shown over the video, to switch channels without leaving it. */
    var channelPanel: (@Composable (currentKey: String, onDismiss: () -> Unit) -> Unit)? = null

    /**
     * Pause pressed (playing = true) or play pressed (false) on a live channel. Returns true when it
     * dealt with it itself (play switched to the pause buffer), so the player shouldn't toggle.
     */
    var timeshiftToggle: ((channelKey: String, playing: Boolean) -> Boolean)? = null

    /** Closes the player (the sleep timer, at the end). */
    var closePlayer: (() -> Unit)? = null

    /** Opens the sleep timer's question. */
    var askSleep: (() -> Unit)? = null

    /** Rewinds the live channel by a little, from a running buffer. Returns false when there's none. */
    var rewind: ((channelKey: String) -> Boolean)? = null

    /** True when a running buffer is there to rewind in ("Rewind anytime" is on). */
    var rewindAvailable: (() -> Boolean)? = null

    /** What sits over the live picture: the clock, the sleep timer. */
    var playerExtras: (@Composable (channelKey: String) -> Unit)? = null

    /** Leaves the pause buffer for the live channel again. */
    var goLive: ((bufferKey: String) -> Unit)? = null

    /** Your On Demand providers' copies of a movie or episode, for Nuvio's stream list. Set on Android. */
    var onDemandStreams: (suspend (type: String, tmdbId: String?, title: String, year: Int?, season: Int?, episode: Int?) -> List<OnDemandStream>)? = null

    /** Opens a channel or movie from Search or Home (pushes the player). Set by the app shell. */
    var playFromBrowse: ((LiveTvPlayRequest) -> Unit)? = null

    /** Live TV and On Demand matches under the search box. */
    var searchSection: (@Composable (query: String, onPlay: (LiveTvPlayRequest) -> Unit) -> Unit)? = null

    /** The row of recent and favorite channels on Home. */
    var homeRow: (@Composable (onPlay: (LiveTvPlayRequest) -> Unit) -> Unit)? = null

    /** New episodes of your shows on Home; picking one opens the show. */
    var homeEpisodes: (@Composable (onOpenShow: (imdb: String, name: String, image: String?) -> Unit) -> Unit)? = null

    /** Drawn over the whole app: game reminders and the switch to a game. */
    var appOverlay: (@Composable () -> Unit)? = null

    /** Whether On Demand can offer streams (a provider is set up), so Play isn't shown as unavailable. */
    var onDemandReady: (() -> Boolean)? = null

    /** The bell on a show's page: on = it's on your Calendar (with new-episode cards on Home). */
    var calendarBell: (@Composable (imdb: String, name: String) -> LiveTvBell)? = null

    var screen: (@Composable (modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit) -> Unit)? = null
    val available: Boolean get() = screen != null
}

/** Content Nuvio keeps out of Continue Watching, the library, history and Trakt: it has its own place elsewhere. */
fun isUntrackedContent(type: String?): Boolean =
    type.equals("youtube", ignoreCase = true) || type?.startsWith("livetv", ignoreCase = true) == true

/** One playable copy of a title from an On Demand provider. */
data class OnDemandStream(
    val label: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val detail: String? = null,
)

/** The Calendar bell for one show: whether it's on, and a way to flip it. */
class LiveTvBell(val on: Boolean, val toggle: () -> Unit)
