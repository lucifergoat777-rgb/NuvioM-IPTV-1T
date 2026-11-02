package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.calendar.CalendarItem
import com.nuvio.app.livetv.calendar.MobileCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarTab(val label: String) { UPCOMING("Upcoming"), RECENT("Recently aired"), TODAY("Airing today"), FOLLOWING("Following") }

/** The Calendar: when the next episodes of your shows come out, what just aired, and what's on today. */
@Composable
fun CalendarPage(calendar: MobileCalendar, use24Hour: Boolean, onPlay: (com.nuvio.app.features.livetv.LiveTvPlayRequest) -> Unit = {}) {
    val state by calendar.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CalendarTab.UPCOMING) }
    var opened by remember { mutableStateOf<CalendarItem?>(null) }
    LaunchedEffect(Unit) { calendar.refresh() }
    LaunchedEffect(tab) { if (tab == CalendarTab.TODAY) calendar.loadToday() }
    val timeFmt = remember(use24Hour) { SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarTab.entries.forEach { t ->
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.clip(shape)
                        .background(if (tab == t) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { tab = t }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
        }
        val list = when (tab) {
            CalendarTab.UPCOMING -> state.upcoming
            CalendarTab.RECENT -> state.recent
            CalendarTab.TODAY -> state.today
            CalendarTab.FOLLOWING -> emptyList()
        }
        val busy = if (tab == CalendarTab.TODAY) state.todayLoading else state.loading
        if (tab == CalendarTab.FOLLOWING) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("Shows with a bell", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                if (state.followed.isEmpty()) item { Text("Open an episode and tap Follow to add a show here.", color = LocalContentColor.current.copy(alpha = 0.6f)) }
                items(state.followed.entries.toList(), key = { "f" + it.key }) { (imdb, name) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { calendar.toggleFollow(imdb, name) }) { Text("Unfollow") }
                    }
                }
                item { Text("Taken off the Calendar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
                if (state.hidden.isEmpty()) item { Text("Nothing hidden.", color = LocalContentColor.current.copy(alpha = 0.6f)) }
                items(state.hidden.entries.toList(), key = { "h" + it.key }) { (imdb, name) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { calendar.unhide(imdb) }) { Text("Show again") }
                    }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator()
                    else Text(
                        when {
                            tab == CalendarTab.TODAY -> "Nothing found airing today."
                            state.note != null -> state.note!!
                            tab == CalendarTab.RECENT -> "Nothing aired lately from your shows."
                            else -> "No new episodes coming up from your shows."
                        },
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                    )
                }
            } else {
                val grouped = remember(list, tab) { list.groupBy { dayKey(it.airMs) } }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (busy) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("  Looking up your shows (${state.shows})…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    grouped.forEach { (key, day) ->
                        item(key = "h$key") {
                            Text(
                                dayTitle(key, dayFmt),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(day, key = { it.showName + it.season + "x" + it.episode + it.title + it.airMs }) { e ->
                            EpisodeRow(e, timeFmt) { opened = e }
                        }
                    }
                }
            }
        }
    }
    opened?.let { e ->
        val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
        val live = remember { com.nuvio.app.livetv.LiveTv.get(context) }
        var onTv by remember(e) { mutableStateOf<List<com.nuvio.app.livetv.calendar.OnMyTv>>(emptyList()) }
        var recorded by remember(e) { mutableStateOf(setOf<String>()) }
        LaunchedEffect(e) { onTv = calendar.onMyTv(e.showName) }
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(e.showName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (e.isMovie) "Movie  —  ${e.title.orEmpty()}" else "S${e.season} · E${e.episode}" + (e.title?.let { "  —  $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                    Text(
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(e.airMs)) + "  " + timeFmt.format(Date(e.airMs)) +
                            (e.network?.let { "  ·  $it" } ?: "") + if (e.streaming) " (streaming)" else "",
                        style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    e.runtime?.let { Text("$it min", style = MaterialTheme.typography.bodySmall) }
                    e.summary?.takeIf { it.isNotBlank() && !it.equals("null", true) }?.let { Text(android.text.Html.fromHtml(it, 0).toString().trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                    if (onTv.isNotEmpty()) {
                        Text("On my TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        onTv.forEach { t ->
                            val id = t.channel.key + t.program.startMs
                            val scheduled = id in recorded || live.scheduler.isScheduled(t.channel.key, t.program)
                            val onNow = System.currentTimeMillis() in t.program.startMs until t.program.stopMs
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        (if (onNow) "Now  " else SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(t.program.startMs)) + "  ") + timeFmt.format(Date(t.program.startMs)),
                                        style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.65f),
                                    )
                                }
                                if (onNow) TextButton(onClick = {
                                    com.nuvio.app.livetv.player.LiveTvReconnect.arm(t.channel.url, t.channel.key)
                                    onPlay(liveTvRequest(t.channel, listOf(t.program), System.currentTimeMillis())); opened = null
                                }) { Text("Watch") }
                                else TextButton(enabled = !scheduled, onClick = {
                                    live.scheduler.add(t.channel, t.program); recorded = recorded + id
                                }) { Text(if (scheduled) "Set" else "Record") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Close") } },
            dismissButton = {
                val imdb = e.imdb
                if (imdb != null) Row {
                    val following = imdb in state.followed
                    TextButton(onClick = { calendar.toggleFollow(imdb, e.showName); opened = null }) { Text(if (following) "Unfollow" else "Follow") }
                    TextButton(onClick = { calendar.hide(imdb, e.showName); opened = null }) { Text("Hide show") }
                }
            },
        )
    }
}

@Composable
private fun EpisodeRow(e: CalendarItem, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B1F)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 84.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B))) {
            if (!e.image.isNullOrBlank()) AsyncImage(e.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.showName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.isMovie) "Movie  ·  ${e.title.orEmpty()}" else "S${e.season} · E${e.episode}" + (e.title?.let { "  $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = LocalContentColor.current.copy(alpha = 0.75f),
            )
            Text(
                timeFmt.format(Date(e.airMs)) + (e.network?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, color = LocalContentColor.current.copy(alpha = 0.55f),
            )
        }
    }
}

private fun dayKey(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayTitle(day: Long, fmt: SimpleDateFormat): String {
    val today = dayKey(System.currentTimeMillis())
    val diff = Math.round((day - today) / 86_400_000.0).toInt()
    return when (diff) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> fmt.format(Date(day))
    }
}
