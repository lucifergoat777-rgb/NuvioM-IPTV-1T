package com.nuvio.app.features.cast

import kotlinx.coroutines.flow.MutableStateFlow

/** What the player asks the Cast device to play. */
data class CastLoad(
    val url: String,
    val headers: Map<String, String>,
    val title: String,
    val subtitle: String?,
    val poster: String?,
    val positionMs: Long,
    /** A live channel: no length, no seeking. */
    val live: Boolean,
)

/** What's on the Cast device right now. */
data class CastUi(
    val deviceName: String,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** The device is loading what was sent, or the last thing sent was refused. */
    val message: String? = null,
    /** Sound tracks the device found in what it's playing (id, name), and which one is on. */
    val audioTracks: List<Pair<Long, String>> = emptyList(),
    val activeAudio: Long? = null,
    /** The device's volume 0..1, and whether it's muted. */
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** A warning about what was sent (a sound format the device may not play). */
    val note: String? = null,
)

/**
 * Casting lives in Android-only code (Google Cast). The Android app fills these in at start-up; on
 * other platforms they stay empty and the player shows no Cast button.
 */
object CastHost {
    /** Opens the device picker (or, while casting, the disconnect dialog). Null when Cast isn't available. */
    var chooser: (() -> Unit)? = null

    /** Non-null while a Cast session is connected. */
    val ui = MutableStateFlow<CastUi?>(null)

    /** Sends [CastLoad] to the connected device; false when it can't be cast (a phone-only link). */
    var load: ((CastLoad) -> Boolean)? = null

    var togglePlayback: (() -> Unit)? = null
    var seekTo: ((Long) -> Unit)? = null
    var stop: (() -> Unit)? = null
    var selectAudio: ((Long) -> Unit)? = null
    var setVolume: ((Float) -> Unit)? = null
    var setMuted: ((Boolean) -> Unit)? = null

    /** What the player knows about the sound it's sending, for a warning. */
    var noteAudio: ((String?) -> Unit)? = null

    /** Where the Cast device had got to when the session ended, so the phone carries on from there. */
    @kotlin.concurrent.Volatile var lastPositionMs: Long = 0L
}
