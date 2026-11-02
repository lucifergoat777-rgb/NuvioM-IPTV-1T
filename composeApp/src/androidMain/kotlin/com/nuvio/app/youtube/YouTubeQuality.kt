package com.nuvio.app.youtube

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/** The player Nuvio is playing with, so the YouTube panel can list and pick video sizes. Set by the player engine. */
object YouTubePlayerHandle {
    var player by mutableStateOf<ExoPlayer?>(null)
}

/** Every video height the player can play right now, tallest first. */
fun ExoPlayer.availableHeights(): List<Int> =
    currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).height } }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()

/** The height you pinned, or 0 while the player chooses by itself. */
fun ExoPlayer.pinnedHeight(): Int {
    val override = trackSelectionParameters.overrides.values.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return 0
    return override.mediaTrackGroup.getFormat(override.trackIndices.firstOrNull() ?: return 0).height
}

/** Tells the player which video format to prefer when a size comes in several. */
fun ExoPlayer.applyCodecPreference(context: Context) {
    val order = YouTubeSettings.codecMimeOrder(context)
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setPreferredVideoMimeTypes(*order.toTypedArray())
        .build()
}

/** Pins playback to [height] (in your chosen codec when the size has several), or back to automatic when 0. */
fun ExoPlayer.selectQuality(context: Context, height: Int) {
    val params = trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (height > 0) {
        val order = YouTubeSettings.codecMimeOrder(context)
        var bestGroup: androidx.media3.common.TrackGroup? = null
        var bestIndex = -1
        var bestBitrate = -1
        var bestRank = Int.MAX_VALUE
        for (g in currentTracks.groups) {
            if (g.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                if (f.height != height) continue
                val rank = order.indexOf(f.sampleMimeType).let { if (it < 0) order.size else it }
                if (rank < bestRank || (rank == bestRank && f.bitrate > bestBitrate)) {
                    bestGroup = g.mediaTrackGroup
                    bestIndex = i
                    bestRank = rank
                    bestBitrate = f.bitrate
                }
            }
        }
        if (bestGroup != null) params.setOverrideForType(TrackSelectionOverride(bestGroup, bestIndex))
    }
    trackSelectionParameters = params.build()
}

/** Once the sizes of a video are known, starts it at the codec and quality chosen in the settings. */
@Composable
fun YouTubeQualityEffect(videoId: String) {
    val context = LocalContext.current.applicationContext
    val player = YouTubePlayerHandle.player ?: return
    DisposableEffect(player, videoId) {
        var applied = false
        fun apply() {
            if (applied) return
            val heights = player.availableHeights()
            if (heights.isEmpty()) return
            applied = true
            player.applyCodecPreference(context)
            val preferred = YouTubeSettings.preferredHeight(context)
            if (preferred > 0) {
                player.selectQuality(context, heights.firstOrNull { it <= preferred } ?: heights.last())
            }
        }
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = apply()
        }
        player.addListener(listener)
        apply()
        onDispose { player.removeListener(listener) }
    }
}

/** Starts a video at the speed you last picked, and remembers any change you make. */
@Composable
fun YouTubeSpeedEffect(videoId: String) {
    val context = LocalContext.current.applicationContext
    val player = YouTubePlayerHandle.player ?: return
    DisposableEffect(player, videoId) {
        var applied = false
        fun apply() {
            if (applied) return
            applied = true
            val saved = YouTubeSettings.playbackSpeed(context)
            if (saved > 0f && saved != 1f) player.setPlaybackSpeed(saved)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) apply()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (applied) YouTubeSettings.setPlaybackSpeed(context, playbackParameters.speed)
            }
        }
        player.addListener(listener)
        if (player.playbackState == Player.STATE_READY) apply()
        onDispose { player.removeListener(listener) }
    }
}
