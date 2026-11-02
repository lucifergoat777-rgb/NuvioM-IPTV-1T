package com.nuvio.app.features.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Drawn over the picture while casting: where it's playing, and the basic controls. */
@Composable
fun CastOverlay(ui: CastUi, title: String, live: Boolean, onChangeSource: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    Box(modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Cast, null, tint = Color.White, modifier = Modifier.size(44.dp))
            Text("Casting to ${ui.deviceName}", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(title, color = Color(0xFFB0B0B8), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            ui.message?.let { Text(it, color = Color(0xFFFFB74D), textAlign = TextAlign.Center) }
            ui.note?.let { Text(it, color = Color(0xFFFFB74D), textAlign = TextAlign.Center, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            if (!live && ui.durationMs > 0) {
                Slider(
                    value = scrub ?: (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f),
                    onValueChange = { scrub = it },
                    onValueChangeFinished = { scrub?.let { CastHost.seekTo?.invoke((it * ui.durationMs).toLong()) }; scrub = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(clock(ui.positionMs) + "  /  " + clock(ui.durationMs), color = Color(0xFFB0B0B8))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!live) RoundButton(Icons.Rounded.Replay10) { CastHost.seekTo?.invoke((ui.positionMs - 10_000L).coerceAtLeast(0L)) }
                RoundButton(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, big = true) { CastHost.togglePlayback?.invoke() }
                if (!live) RoundButton(Icons.Rounded.Forward10) { CastHost.seekTo?.invoke(ui.positionMs + 10_000L) }
            }
            // Sound: the device's volume, mute, and its sound tracks (a stream's other languages or formats).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { CastHost.setMuted?.invoke(!ui.muted) }) { Text(if (ui.muted) "Unmute" else "Mute", color = Color.White) }
                Slider(
                    value = ui.volume.coerceIn(0f, 1f), onValueChange = { CastHost.setVolume?.invoke(it) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (ui.audioTracks.size > 1) {
                Text("Sound", color = Color(0xFFB0B0B8), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ui.audioTracks.take(4).forEach { (id, name) ->
                        TextButton(onClick = { CastHost.selectAudio?.invoke(id) }) {
                            Text(name, color = if (id == ui.activeAudio) Color(0xFFE53935) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (onChangeSource != null) TextButton(onClick = onChangeSource) { Text("No sound? Choose another source", color = Color(0xFFFFB74D)) }
            TextButton(onClick = { CastHost.stop?.invoke() }) { Text("Stop casting") }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 64.dp else 48.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (big) 36.dp else 26.dp)) }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
