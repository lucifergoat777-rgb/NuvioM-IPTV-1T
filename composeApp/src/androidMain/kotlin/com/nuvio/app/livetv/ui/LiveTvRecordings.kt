package com.nuvio.app.livetv.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.record.LiveTvRecording
import com.nuvio.app.livetv.record.RecordingSchedule
import com.nuvio.app.livetv.record.RecordingStatus
import com.nuvio.app.livetv.record.RecordingStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

private fun whenText(ms: Long): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(ms))

private fun lengthText(sec: Double): String {
    val total = sec.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

/** The Recordings tab: what's recording now, what's set to record, and what's saved. */
@Composable
fun RecordingsList(
    recordings: List<LiveTvRecording>,
    schedules: List<RecordingSchedule>,
    freeBytes: Long,
    onPlay: (LiveTvRecording) -> Unit,
    onStop: (LiveTvRecording) -> Unit,
    onDelete: (LiveTvRecording) -> Unit,
    onCancelSchedule: (RecordingSchedule) -> Unit,
    series: List<com.nuvio.app.livetv.record.SeriesRule> = emptyList(),
    onRemoveSeries: (com.nuvio.app.livetv.record.SeriesRule) -> Unit = {},
    onKeepLast: (com.nuvio.app.livetv.record.SeriesRule, Int) -> Unit = { _, _ -> },
    resumeOf: (LiveTvRecording) -> Pair<Long, Long>? = { null },
) {
    var confirmDelete by remember { mutableStateOf<LiveTvRecording?>(null) }
    val now = System.currentTimeMillis()
    val active = recordings.filter { it.isRecording }
    val saved = recordings.filter { !it.isRecording }

    if (recordings.isEmpty() && schedules.isEmpty()) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nothing recorded yet. Long-press a channel to record it, or tap a show in the guide.",
                color = LocalContentColor.current.copy(alpha = 0.65f),
            )
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Room left: ${RecordingStorage.sizeLabel(freeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.55f),
            )
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("Recording now") }
            items(active, key = { it.id }) { r ->
                RecordingCard(
                    r, "Until ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.stopAtMs))}",
                    primary = "Watch" to { onPlay(r) }, secondary = "Stop" to { onStop(r) },
                )
            }
        }
        if (series.isNotEmpty()) {
            item { SectionTitle("Series") }
            items(series, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Every airing on ${r.channel.name}  \u00B7  " + if (r.keepLast > 0) "keeping the last ${r.keepLast}" else "keeping them all",
                            style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = { onKeepLast(r, (r.keepLast - 1).coerceAtLeast(0)) }) { Text("\u2212") }
                    TextButton(onClick = { onKeepLast(r, (r.keepLast + 1).coerceAtMost(30)) }) { Text("+") }
                    TextButton(onClick = { onRemoveSeries(r) }) { Text("Stop") }
                }
            }
        }
        if (schedules.isNotEmpty()) {
            item { SectionTitle("Scheduled") }
            items(schedules, key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${s.channel.name} · ${whenText(s.startMs)}" + if (s.useCatchup) " · saved from catch-up" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                        )
                    }
                    TextButton(onClick = { onCancelSchedule(s) }) { Text("Cancel") }
                }
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionTitle("Saved") }
            items(saved, key = { it.id }) { r ->
                val detail = when (r.status) {
                    RecordingStatus.FAILED -> r.problem ?: "It couldn't be recorded"
                    else -> "${whenText(r.startedAtMs)} · ${lengthText(r.durationSec)} · ${RecordingStorage.sizeLabel(r.sizeBytes)}" +
                        (resumeOf(r)?.let { (pos, _) -> if (pos > 5_000L) "\nContinue from ${lengthText(pos / 1000.0)}" else null } ?: "") +
                        (r.problem?.let { "\n$it" } ?: "")
                }
                RecordingCard(
                    r, detail,
                    primary = if (r.status == RecordingStatus.FAILED) null else "Play" to { onPlay(r) },
                    secondary = "Delete" to { confirmDelete = r },
                )
            }
        }
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this recording?") },
            text = { Text("\"${r.title}\" will be removed from this device.") },
            confirmButton = { TextButton(onClick = { onDelete(r); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun RecordingCard(
    r: LiveTvRecording,
    detail: String,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (r.isRecording) "● " else "") + r.title,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (r.isRecording) Accent else LocalContentColor.current,
            )
            Text(r.channelName, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
        if (primary != null) TextButton(onClick = primary.second) { Text(primary.first) }
        TextButton(onClick = secondary.second) { Text(secondary.first) }
    }
}

/** Starts a recording of [channel] now, or sets one for later. */
@Composable
fun RecordChannelDialog(
    channel: LiveChannel,
    currentShow: String?,
    minutesLeftInShow: Int?,
    onRecordNow: (minutes: Int, title: String) -> Unit,
    onSchedule: (title: String, startMs: Long, stopMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(currentShow ?: channel.name) }
    var startMs by remember { mutableStateOf(0L) }
    var minutes by remember { mutableStateOf(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record ${channel.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Name") })
                Text("How long", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(30, 60, 120, 180).forEach { m ->
                        TextButton(onClick = { minutes = m }) {
                            Text(if (m < 60) "$m min" else "${m / 60} h", color = if (minutes == m) Accent else LocalContentColor.current)
                        }
                    }
                }
                if (minutesLeftInShow != null && minutesLeftInShow > 0) {
                    TextButton(onClick = { minutes = minutesLeftInShow }) { Text("Until the show ends ($minutesLeftInShow min)") }
                }
                Text(
                    if (startMs == 0L) "Starts now" else "Starts ${whenText(startMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    TextButton(onClick = { startMs = 0L }) { Text("Now") }
                    TextButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = if (startMs > 0) startMs else System.currentTimeMillis() + 3_600_000L }
                        DatePickerDialog(context, { _, y, mo, d ->
                            TimePickerDialog(context, { _, h, mi ->
                                val picked = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }
                                startMs = picked.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("Pick a time") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val name = title.trim().ifBlank { channel.name }
                if (startMs == 0L || startMs <= System.currentTimeMillis()) onRecordNow(minutes, name)
                else onSchedule(name, startMs, startMs + minutes * 60_000L)
                onDismiss()
            }) { Text(if (startMs == 0L) "Record" else "Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
