package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.sports.ChannelMatch
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.Side
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

/** Sports: today's games with scores, and which of your channels show each one. */
@Composable
fun SportsPage(
    repo: SportsRepository,
    hasChannels: Boolean,
    use24Hour: Boolean,
    onWatch: (LiveChannel) -> Unit,
    onRecord: (SportsEvent, ChannelMatch) -> Unit,
) {
    // Keeps the scores fresh while this page is open.
    DisposableEffect(repo) {
        repo.setViewing(true)
        onDispose { repo.setViewing(false) }
    }
    val events by repo.events.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val problem by repo.problem.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    var day by remember { mutableStateOf(0) }
    var sport by remember { mutableStateOf<Sport?>(null) }
    var opened by remember { mutableStateOf<SportsEvent?>(null) }
    var showScores by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("games") }
    var pickTeams by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<SportsEvent?>(null) }
    var fixFor by remember { mutableStateOf<SportsEvent?>(null) }
    var windowChannel by remember { mutableStateOf<LiveChannel?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val from = remember(day) { repo.dayStart(day) }
    val to = remember(day) { repo.dayStart(day + 1) }
    val ofDay = remember(events, from, to) { events.filter { it.startMs in from until to } }
    val sportsToday = remember(ofDay) {
        ofDay.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }.distinct().sortedBy { it.ordinal }
    }
    val shown = remember(ofDay, sport) {
        ofDay.filter { sport == null || SportsCatalog.league(it.leagueId)?.sport == sport }
    }
    val groups = remember(shown) {
        shown.groupBy { it.leagueId }
            .toList()
            .sortedBy { (id, _) -> SportsCatalog.all.indexOfFirst { it.id == id }.let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (id, list) ->
                id to list.sortedWith(compareBy<SportsEvent> { if (it.state == GameState.LIVE) 0 else 1 }.thenBy { it.startMs })
            }
    }
    val clock = remember(use24Hour) { SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Games", mode == "games") { mode = "games" }
            Pill("My Teams", mode == "mine") { mode = "mine" }
            Pill("Tables", mode == "tables") { mode = "tables" }
        }
        if (mode == "mine") {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MySportsList(repo, clock, settings.hideScores, onOpen = { opened = it }, onPick = { pickTeams = true })
            }
        } else if (mode == "tables") {
            Box(Modifier.weight(1f).fillMaxWidth()) { SportsTables(repo) }
        }
        if (mode == "games") LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            items((-1..6).toList()) { d ->
                val label = when (d) { -1 -> "Yesterday"; 0 -> "Today"; 1 -> "Tomorrow"; else -> dayFmt.format(Date(repo.dayStart(d))) }
                Pill(label, day == d) { day = d }
            }
        }
        if (mode == "games" && sportsToday.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item { Pill("All", sport == null) { sport = null } }
                items(sportsToday) { s -> Pill("${s.icon} ${s.label}", sport == s) { sport = s } }
            }
        }
        if (mode == "games" && !hasChannels) {
            Text(
                "Add a playlist to watch games. You can still see scores and times.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (mode == "games") problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        windowChannel?.let { c ->
            SportsVideoWindow(c, onFullScreen = { windowChannel = null; onWatch(c) }, onClose = { windowChannel = null })
        }
        if (mode == "games") Box(Modifier.weight(1f).fillMaxWidth()) {
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator()
                    else Text("No games found for this day.", color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (leagueId, games) ->
                        item(key = "h$leagueId") {
                            Text(
                                SportsCatalog.league(leagueId)?.name ?: leagueId,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        items(games, key = { it.id }) { ev ->
                            GameCard(
                                ev = ev,
                                sport = SportsCatalog.league(ev.leagueId)?.sport,
                                best = matches[ev.id]?.firstOrNull(),
                                clock = clock,
                                hideScore = settings.hideScores,
                            ) { opened = ev }
                        }
                    }
                }
            }
        }
    }

    opened?.let { ev ->
        GameDialog(
            ev = ev,
            sport = SportsCatalog.league(ev.leagueId)?.sport,
            matches = matches[ev.id].orEmpty(),
            clock = clock,
            hideScore = settings.hideScores,
            onWatch = { m -> if (settings.videoWindow) windowChannel = m.channel else onWatch(m.channel); opened = null },
            onRecord = { m -> onRecord(ev, m); opened = null },
            reminded = ev.id in repo.follows.collectAsStateWithLifecycle().value.reminders.map { it.eventId },
            onRemind = {
                val was = repo.isReminded(ev)
                repo.toggleReminder(ev)
                val lead = settings.remindMinutes * 60_000L
                val r = com.nuvio.app.livetv.model.Reminder("sport:" + ev.id, SportsCatalog.league(ev.leagueId)?.name ?: "Sports", ev.title, ev.startMs + 2 * 60_000L - lead)
                if (was) com.nuvio.app.livetv.reminders.LiveTvReminders.cancel(context, r)
                else {
                    com.nuvio.app.livetv.reminders.LiveTvReminders.schedule(context, r)
                    android.widget.Toast.makeText(context, "You'll get a reminder.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDetails = { details = ev; opened = null },
            onFix = { fixFor = ev; opened = null },
            onDismiss = { opened = null },
        )
    }
    if (pickTeams) PickTeamsDialog(repo) { pickTeams = false }
    details?.let { GameDetailsDialog(repo, it) { details = null } }
    fixFor?.let { ev ->
        FixChannelDialog(
            repo = repo, ev = ev,
            onDone = { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show(); fixFor = null },
            onDismiss = { fixFor = null },
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
}

private fun statusOf(ev: SportsEvent, clock: SimpleDateFormat): String = when (ev.state) {
    GameState.PRE -> if (ev.hasTime) clock.format(Date(ev.startMs)) else "Today"
    GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: "LIVE"
    GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: "Final"
    GameState.OFF -> ev.status?.takeIf { it.isNotBlank() } ?: clock.format(Date(ev.startMs))
}

@Composable
private fun GameCard(ev: SportsEvent, sport: Sport?, best: ChannelMatch?, clock: SimpleDateFormat, hideScore: Boolean, onClick: () -> Unit) {
    val live = ev.state == GameState.LIVE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (ev.away != null && ev.home != null) {
                val sides = ev.ordered(sport ?: Sport.OTHER)
                sides.forEach { s -> SideRow(s, showScore = ev.hasScore && !hideScore) }
            } else {
                Text(ev.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1) }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                statusOf(ev, clock),
                style = MaterialTheme.typography.labelLarge,
                color = if (live) Accent else LocalContentColor.current,
                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
            )
            if (best != null) {
                Text(
                    "On ${best.channel.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

@Composable
private fun SideRow(s: Side, showScore: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!s.team.logo.isNullOrBlank()) {
            AsyncImage(s.team.logo, null, Modifier.size(24.dp), contentScale = ContentScale.Fit)
        } else Spacer(Modifier.size(24.dp))
        Text(
            s.team.name,
            Modifier.padding(start = 10.dp).weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (s.winner) FontWeight.Bold else FontWeight.Normal,
        )
        if (showScore) Text(s.score.orEmpty(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun GameDialog(
    ev: SportsEvent,
    sport: Sport?,
    matches: List<ChannelMatch>,
    clock: SimpleDateFormat,
    hideScore: Boolean,
    onWatch: (ChannelMatch) -> Unit,
    onRecord: (ChannelMatch) -> Unit,
    reminded: Boolean = false,
    onRemind: () -> Unit = {},
    onDetails: () -> Unit = {},
    onFix: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(
                        SportsCatalog.league(ev.leagueId)?.name,
                        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ev.startMs)) + "  " + statusOf(ev, clock),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
                if (ev.away != null && ev.home != null) {
                    ev.ordered(sport ?: Sport.OTHER).forEach { SideRow(it, showScore = ev.hasScore && !hideScore) }
                }
                ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                ev.situation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Accent) }
                if (ev.broadcasts.isNotEmpty()) {
                    Text("TV: " + ev.broadcasts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(4.dp))
                if (matches.isEmpty()) {
                    Text("None of your channels are showing this one.", color = LocalContentColor.current.copy(alpha = 0.6f))
                } else {
                    Text("Your channels", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    LazyColumn(Modifier.height(220.dp)) {
                        items(matches.take(12), key = { it.channel.key }) { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(m.quality, if (m.sure) "good match" else "possible match").joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalContentColor.current.copy(alpha = 0.6f),
                                    )
                                }
                                if (ev.state != GameState.POST) TextButton(onClick = { onRecord(m) }) { Text("Record") }
                                TextButton(onClick = { onWatch(m) }) { Text("Watch") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDetails) { Text("Details") }
                if (ev.state == GameState.PRE) TextButton(onClick = onRemind) { Text(if (reminded) "Remove reminder" else "Remind me") }
                TextButton(onClick = onFix) { Text("Wrong channel?") }
            }
        },
    )
}
