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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.League
import com.nuvio.app.livetv.sports.LeagueKind
import com.nuvio.app.livetv.sports.ScoringPlay
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsFollows
import com.nuvio.app.livetv.sports.SportsRepository
import com.nuvio.app.livetv.sports.SportsRows
import com.nuvio.app.livetv.sports.Standings
import com.nuvio.app.livetv.sports.TeamInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBg = Color(0xFF1B1B1F)

/** A game of a team you follow, a league you follow, or a name you follow. */
internal fun isMineEvent(ev: SportsEvent, f: SportsFollows): Boolean =
    ev.leagueId in f.leagues || ev.leagueId in f.pins ||
        ev.sides.any { f.followsTeam(ev.leagueId, it.team.id) } ||
        (ev.word != null && f.words.any { it.equals(ev.word, ignoreCase = true) })

/** My Teams: Live & Upcoming, then a row for each team and league you follow. */
@Composable
internal fun MySportsList(repo: SportsRepository, clock: SimpleDateFormat, hideScores: Boolean, onOpen: (SportsEvent) -> Unit, onPick: () -> Unit) {
    val known by repo.known.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val page = remember(known, follows, now / 60_000L) { SportsRows.build(known, follows, now, null) { isMineEvent(it, follows) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (follows.isEmpty) "Follow teams and leagues to see them here." else "${follows.teams.size} teams, ${follows.leagues.size} leagues",
                style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f), modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPick) { Text(if (follows.isEmpty) "Pick teams" else "Edit") }
        }
        if (page.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (follows.isEmpty) "Nothing followed yet." else "No games found for what you follow right now.",
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(0.dp, 4.dp, 0.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(page.rows, key = { it.key }) { row ->
                    Column {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(row.events, key = { it.id }) { ev -> GameChip(ev, clock, hideScores) { onOpen(ev) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameChip(ev: SportsEvent, clock: SimpleDateFormat, hideScore: Boolean, onClick: () -> Unit) {
    val sport = SportsCatalog.league(ev.leagueId)?.sport ?: Sport.OTHER
    val sides = ev.ordered(sport)
    Column(
        Modifier.width(190.dp).clip(RoundedCornerShape(12.dp)).background(CardBg).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (sides.size == 2) {
            sides.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!s.team.logo.isNullOrBlank()) AsyncImage(s.team.logo, null, Modifier.size(20.dp), contentScale = ContentScale.Fit)
                    else Box(Modifier.size(20.dp))
                    Text(s.team.abbr.ifBlank { s.team.name }, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1,
                        fontWeight = if (s.winner) FontWeight.Bold else FontWeight.Normal)
                    if (ev.hasScore && !hideScore) Text(s.score.orEmpty(), fontWeight = FontWeight.Bold)
                }
            }
        } else Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            when (ev.state) {
                GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: "LIVE"
                GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: "Final"
                else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ev.startMs)) + (if (ev.hasTime) "  " + clock.format(Date(ev.startMs)) else "")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (ev.state == GameState.LIVE) Color(0xFFE53935) else LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

/** Pick your teams: choose a league, then the teams (and the league itself) to follow. */
@Composable
internal fun PickTeamsDialog(repo: SportsRepository, onDismiss: () -> Unit) {
    val follows by repo.follows.collectAsStateWithLifecycle()
    var league by remember { mutableStateOf<League?>(null) }
    var teams by remember { mutableStateOf<List<TeamInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(league?.id) {
        teams = null
        league?.let { teams = runCatching { repo.teams(it) }.getOrDefault(emptyList()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(league?.name ?: "Pick a league") },
        text = {
            val l = league
            if (l == null) {
                val all = remember { repo.activeLeagues() }
                LazyColumn(Modifier.height(380.dp)) {
                    items(all, key = { it.id }) { lg ->
                        Row(Modifier.fillMaxWidth().clickable { league = lg }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(lg.sport.icon + "  " + lg.name, Modifier.weight(1f))
                            if (lg.id in follows.leagues || follows.teams.any { it.leagueId == lg.id }) Text("✓")
                        }
                    }
                }
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { league = null; query = "" }) { Text("‹ Leagues") }
                        Spacer1()
                        TextButton(onClick = { repo.toggleLeague(l.id) }) {
                            Text(if (l.id in follows.leagues) "Unfollow the league" else "Follow the whole league")
                        }
                    }
                    if (l.kind == LeagueKind.TEAMS) {
                        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search teams") })
                        val list = teams
                        when {
                            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            else -> LazyColumn(Modifier.height(300.dp)) {
                                items(list.filter { query.isBlank() || it.name.contains(query, true) || it.fullName.contains(query, true) }, key = { it.key }) { t ->
                                    val on = follows.followedTeam(t) != null
                                    Row(Modifier.fillMaxWidth().clickable { repo.toggleTeam(t) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (!t.logo.isNullOrBlank()) AsyncImage(t.logo, null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
                                        else Box(Modifier.size(28.dp))
                                        Text(t.fullName.ifBlank { t.name }, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (on) "★" else "☆", color = if (on) Color(0xFFFFC107) else LocalContentColor.current)
                                    }
                                }
                            }
                        }
                    } else Text("This is a competition with no teams. Follow the whole league to see its events.", Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun Spacer1() { Box(Modifier.width(8.dp)) }

/** Tables: the league table of the leagues you follow (or the leagues shown). */
@Composable
internal fun SportsTables(repo: SportsRepository) {
    val follows by repo.follows.collectAsStateWithLifecycle()
    val leagues = remember(follows) {
        val mine = repo.tableLeagues(follows)
        (if (mine.isNotEmpty()) mine else repo.activeLeagues().filter { repo.mayHaveTable(it) }).take(30)
    }
    var selected by remember(leagues) { mutableStateOf(leagues.firstOrNull()) }
    var table by remember { mutableStateOf<Standings?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(selected?.id) {
        table = null
        val l = selected ?: return@LaunchedEffect
        loading = true
        table = runCatching { repo.standings(l.id) }.getOrNull()
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        if (leagues.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("No tables for your leagues yet.") }
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(leagues, key = { it.id }) { l ->
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier.clip(shape).background(if (selected?.id == l.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { selected = l }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(l.short.ifBlank { l.name }, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
        }
        val t = table
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                t == null -> Text("No table for this competition.", Modifier.align(Alignment.Center), color = LocalContentColor.current.copy(alpha = 0.6f))
                else -> {
                    val cols = t.columns.take(5)
                    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp)) {
                        t.groups.forEach { g ->
                            item(key = "g" + g.name + g.parent) {
                                Text(g.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                Row {
                                    Text("", Modifier.width(200.dp))
                                    cols.forEach { c -> Text(c.label, Modifier.width(48.dp), fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.6f)) }
                                }
                            }
                            items(g.rows, key = { g.name + it.id }) { r ->
                                val mine = follows.teams.any { it.teamId == r.id }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                        .then(if (mine) Modifier.background(Color(0x22FFC107)) else Modifier),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${r.pos}", Modifier.width(26.dp), fontSize = 12.sp)
                                    if (!r.logo.isNullOrBlank()) AsyncImage(r.logo, null, Modifier.size(20.dp), contentScale = ContentScale.Fit) else Box(Modifier.size(20.dp))
                                    Text(r.shortName.ifBlank { r.name }, Modifier.width(150.dp).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    cols.forEach { c -> Text(r.values[c.key].orEmpty(), Modifier.width(48.dp), fontSize = 12.sp) }
                                }
                                HorizontalDivider(color = Color(0x14FFFFFF))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A game's scoring plays and the extra details (stats, form, venue). */
@Composable
internal fun GameDetailsDialog(repo: SportsRepository, ev: SportsEvent, onDismiss: () -> Unit) {
    var plays by remember { mutableStateOf<List<ScoringPlay>?>(null) }
    var extras by remember { mutableStateOf<com.nuvio.app.livetv.sports.GameExtras?>(null) }
    LaunchedEffect(ev.id) {
        plays = runCatching { repo.scoringPlays(ev) }.getOrDefault(emptyList())
        extras = runCatching { repo.gameExtras(ev) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                val x = extras
                if (x != null && x.hasMatchInfo) item {
                    Text(
                        listOfNotNull(x.venue, x.city, x.tempF?.let { "$it°F" }, x.conditions, x.referee?.let { "Ref: $it" }, x.attendance?.let { "%,d there".format(it) }).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (x != null && x.hasOdds) item {
                    Text(
                        "Win chance: " + listOfNotNull(x.homeWin?.let { "home ${it.toInt()}%" }, x.draw?.let { "draw ${it.toInt()}%" }, x.awayWin?.let { "away ${it.toInt()}%" }).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (x != null && x.stats.isNotEmpty()) {
                    item { Text("Team stats", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                    items(x.stats, key = { it.label }) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(s.away, Modifier.width(56.dp)); Text(s.label, Modifier.weight(1f), fontSize = 12.sp); Text(s.home, Modifier.width(56.dp))
                        }
                    }
                }
                val p = plays
                when {
                    p == null -> item { CircularProgressIndicator(Modifier.padding(24.dp)) }
                    p.isEmpty() -> item { Text("No scoring plays yet.", color = LocalContentColor.current.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)) }
                    else -> {
                        item { Text("Scoring", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                        items(p.size) { i ->
                            val s = p[i]
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(listOfNotNull(s.clock.takeIf { it.isNotBlank() }, s.teamAbbr, if (s.awayScore != null && s.homeScore != null) "${s.awayScore}–${s.homeScore}" else null).joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                                Text(s.text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Pick the right channel for a game by hand: once for the game, or for every game on that network. */
@Composable
internal fun FixChannelDialog(repo: SportsRepository, ev: SportsEvent, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<LiveChannel?>(null) }
    val all = remember { repo.channelsForPicker() }
    val results = remember(query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList() else all.filter { it.name.lowercase().contains(q) }.take(60)
    }
    val c = chosen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c == null) "Which channel is showing it?" else c.name, maxLines = 2) },
        text = {
            if (c == null) {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search your channels") })
                    LazyColumn(Modifier.height(300.dp)) {
                        items(results, key = { it.key }) { ch ->
                            Text(ch.name, Modifier.fillMaxWidth().clickable { chosen = ch }.padding(vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                Column {
                    Text("Use it for:")
                    TextButton(onClick = { repo.pickChannel(ev, c.key, forNetworks = false); onDone("Saved for this game.") }) { Text("Just this game") }
                    if (ev.broadcasts.isNotEmpty()) TextButton(onClick = { repo.pickChannel(ev, c.key, forNetworks = true); onDone("Saved for games on ${ev.broadcasts.first()}.") }) {
                        Text("Every game on ${ev.broadcasts.first()}")
                    }
                    TextButton(onClick = { repo.blockChannel(ev, c.key); onDone("Got it. It won't be offered for this game.") }) { Text("It isn't showing this game") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (chosen != null) chosen = null else onDismiss() }) { Text(if (c != null) "Back" else "Cancel") } },
    )
}
