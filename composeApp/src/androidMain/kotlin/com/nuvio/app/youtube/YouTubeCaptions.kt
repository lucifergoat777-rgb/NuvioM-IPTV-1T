package com.nuvio.app.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.SubtitleAppearanceControls
import java.util.Locale

/** One caption track of the video playing: its own captions, or an automatic translation of them. */
class CaptionTrack(
    val group: Tracks.Group,
    val code: String,
    val name: String,
    /** True for a translation YouTube makes on the fly, false for captions the video came with. */
    val translated: Boolean,
    val selected: Boolean,
)

/** The marker the manifest rewrite puts after a translated track's language code. */
internal const val TRANSLATED_SUFFIX = "-x-tr"

/** The caption tracks the player has now, with real language names, own captions first. */
fun ExoPlayer.captionTracks(): List<CaptionTrack> {
    val enabled = !trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    return currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .map { g ->
            val raw = g.getTrackFormat(0).language.orEmpty()
            val translated = raw.endsWith(TRANSLATED_SUFFIX, ignoreCase = true) || raw.endsWith("*")
            val code = raw.removeSuffix(TRANSLATED_SUFFIX).removeSuffix("*").trim()
            CaptionTrack(g, code, languageName(code), translated, enabled && g.isSelected)
        }
}

/** A language's name in the language the phone is set to; a tidy version of the code when it has none. */
internal fun languageName(code: String): String {
    if (code.isBlank()) return "Unknown"
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(Locale.getDefault())
    if (name.isNotBlank() && !name.equals(code, ignoreCase = true)) return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return code.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

fun ExoPlayer.selectCaption(track: CaptionTrack) {
    YouTubeSession.captionPicked = true
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, listOf(0)))
        .build()
}

fun ExoPlayer.captionsOff() {
    YouTubeSession.captionPicked = true
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

/** The captions picker: Off, the video's own captions, then every language it can be translated into. */
@Composable
fun YouTubeCaptionsDialog(onDismiss: () -> Unit) {
    val player = YouTubePlayerHandle.player
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    val subtitleStyle = playerSettings.subtitleStyle
    var version by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") version
    val tracks = remember(player, version) { player?.captionTracks().orEmpty() }
    val own = tracks.filter { !it.translated }.sortedBy { it.name }
    val translated = tracks.filter { it.translated }.sortedBy { it.name }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val anySelected = tracks.any { it.selected }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xF2101012)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Captions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                    if (player == null || tracks.isEmpty()) {
                        Text(
                            if (player == null) "Nothing is playing." else "This video has no captions.",
                            modifier = Modifier.padding(24.dp), color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            item { CaptionRow("Off", null, !anySelected) { player.captionsOff(); version++ } }
                            if (own.isNotEmpty()) {
                                item { SectionTitle("Captions in the video") }
                                items(own) { t -> CaptionRow(t.name, null, t.selected) { player.selectCaption(t); version++ } }
                            }
                            item {
                                SectionTitle("Auto-translate")
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    placeholder = { Text("Search languages") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                )
                            }
                            items(translated) { t ->
                                CaptionRow(t.name, "Translated by Google", t.selected) { player.selectCaption(t); version++ }
                            }
                            item { SectionTitle("Subtitle style") }
                            item {
                                SubtitleAppearanceControls(
                                    style = subtitleStyle,
                                    spacing = 12.dp,
                                    onStyleChanged = { PlayerSettingsRepository.setSubtitleStyle(it) },
                                )
                            }
                            item { Text("", Modifier.padding(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.14f))
}

@Composable
private fun CaptionRow(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.55f)) }
        }
        if (selected) Text("✓", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF4D6A))
    }
}

/**
 * Keeps captions off until you pick some. Left alone, the player turns on the first caption track in the
 * list, which for YouTube is a translation, and asks the translator for it before anyone has chosen it.
 */
@Composable
fun YouTubeCaptionsOffByDefault(videoId: String) {
    val player = YouTubePlayerHandle.player ?: return
    DisposableEffect(player, videoId) {
        YouTubeSession.captionPicked = false
        fun off() {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(null)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        off()
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                // Until you open the picker, anything the player switched on by itself is switched off again.
                if (!YouTubeSession.captionPicked && !YouTubeSession.showCaptions &&
                    tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
                ) off()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}
