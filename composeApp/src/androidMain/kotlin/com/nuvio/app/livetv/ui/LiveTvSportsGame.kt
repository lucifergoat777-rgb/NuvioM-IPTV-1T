package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.sports.ChannelMatch
import com.nuvio.app.livetv.sports.GameExtras
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.ScoringPlay
import com.nuvio.app.livetv.sports.Side
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.sports.SportsCatalog
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsRepository
import com.nuvio.app.livetv.sports.StatLine
import com.nuvio.app.livetv.sports.TeamInfo
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

private val GameBg = Color(0xFF141419)
private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)
private val AwayBar = Color(0xFFE53935)
private val HomeBar = Color(0xFF3A6EA5)

/** A game's page: the scoreboard, your channels, the latest scoring and the team stats. */
@Composable
internal fun SportsGameScreen(
    repo: SportsRepository,
    ev: SportsEvent,
    sport: Sport,
    matches: List<ChannelMatch>,
    clock: SimpleDateFormat,
    hideScore: Boolean,
    reminded: Boolean,
    onRemind: () -> Unit,
    onWatch: (ChannelMatch) -> Unit,
    onRecord: (ChannelMatch) -> Unit,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(sport)
    val showScore = ev.hasScore && !hideScore
    var plays by remember(ev.id) { mutableStateOf<List<ScoringPlay>?>(null) }
    var extras by remember(ev.id) { mutableStateOf<GameExtras?>(null) }
    LaunchedEffect(ev.id) {
        plays = runCatching { repo.scoringPlays(ev) }.getOrDefault(emptyList())
        extras = runCatching { repo.gameExtras(ev) }.getOrNull()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = GameBg) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            league?.name ?: "Sports",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "score") {
                            Scoreboard(ev, league?.name, sides, showScore, extras)
                        }
                        item(key = "actions") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (ev.state == GameState.PRE) {
                                    GameChip(if (reminded) "Reminded" else "Remind me", reminded, onRemind)
                                }
                                GameChip("Fix channel", false, onFix)
                            }
                        }
                        item(key = "channels-h") { SectionTitle("Watch on your channels") }
                        if (matches.isEmpty()) {
                            item(key = "channels-none") {
                                Text(
                                    "None of your channels are showing this one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                        } else {
                            items(matches.take(12), key = { it.channel.key }) { m ->
                                ChannelRow(m, clock, ev.state, onWatch = { onWatch(m) }, onRecord = { onRecord(m) })
                            }
                        }
                        val p = plays
                        if (!p.isNullOrEmpty()) {
                            item(key = "scoring-h") { SectionTitle("Latest scoring") }
                            items(p.asReversed().take(20), key = { it.clock + "|" + it.text }) { play ->
                                PlayRow(play)
                            }
                        }
                        val x = extras
                        if (x != null && x.stats.isNotEmpty()) {
                            item(key = "stats-h") { SectionTitle("Team stats") }
                            items(x.stats, key = { it.label }) { stat -> StatBar(stat) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(
    ev: SportsEvent,
    leagueName: String?,
    sides: List<Side>,
    showScore: Boolean,
    extras: GameExtras?,
) {
    val live = ev.state == GameState.LIVE
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(leagueName, extras?.venue).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) LiveBadge()
                Text(
                    statusLabel(ev),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (live) Accent else Color.White.copy(alpha = 0.8f),
                )
            }
        }
        if (sides.size == 2) {
            sides.forEach { s -> TeamBand(s, showScore) }
        } else {
            Text(ev.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLine(ev),
                style = MaterialTheme.typography.bodySmall,
                color = if (live) Accent else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (ev.broadcasts.isNotEmpty()) {
                Text(
                    ev.broadcasts.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamBand(side: Side, showScore: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(teamBandBrush(side.team))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!side.team.logo.isNullOrBlank()) {
            AsyncImage(side.team.logo, contentDescription = null, modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                side.team.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = if (side.winner) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            side.record?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        if (showScore) {
            Text(
                side.score ?: "–",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    m: ChannelMatch,
    clock: SimpleDateFormat,
    state: GameState,
    onWatch: () -> Unit,
    onRecord: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.quality, if (m.sure) "good match" else "possible match").joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        if (state != GameState.POST) TextButton(onClick = onRecord) { Text("Record") }
        TextButton(onClick = onWatch) { Text("Watch") }
    }
}

@Composable
private fun PlayRow(p: ScoringPlay) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            p.clock.ifBlank { "–" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(p.teamAbbr, if (p.awayScore != null && p.homeScore != null) "${p.awayScore}–${p.homeScore}" else null).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            Text(p.text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatBar(stat: StatLine) {
    val away = stat.awayValue
    val home = stat.homeValue
    val total = (away ?: 0.0) + (home ?: 0.0)
    val awayFraction = if (total > 0.0) ((away ?: 0.0) / total).toFloat() else 0.5f
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.away, Modifier.width(56.dp), fontWeight = FontWeight.Bold)
            Text(
                stat.label.uppercase(),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stat.home, Modifier.width(56.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A30))) {
            Box(Modifier.weight(awayFraction.coerceAtLeast(0.02f)).fillMaxHeight().background(AwayBar))
            Box(Modifier.weight((1f - awayFraction).coerceAtLeast(0.02f)).fillMaxHeight().background(HomeBar))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun GameChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Accent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

private fun statusLabel(ev: SportsEvent): String = when (ev.state) {
    GameState.LIVE -> ev.status?.takeIf { it.isNotBlank() } ?: "LIVE"
    GameState.POST -> ev.status?.takeIf { it.isNotBlank() } ?: "FINAL"
    GameState.OFF -> ev.status?.takeIf { it.isNotBlank() } ?: "POSTPONED"
    GameState.PRE -> if (ev.hasTime) "UPCOMING" else "TODAY"
}

private fun statusLine(ev: SportsEvent): String = when (ev.state) {
    GameState.LIVE -> "In progress"
    GameState.POST -> "FT · " + SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(Date(ev.startMs))
    GameState.OFF -> ev.status ?: "Postponed"
    GameState.PRE -> if (ev.hasTime) SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(Date(ev.startMs)) else "Today"
}

// ---- team colours

private fun teamColor(t: TeamInfo?): Color {
    val main = t?.color?.let { Color(it) }
    val alt = t?.altColor?.let { Color(it) }
    val pick = when {
        main == null -> alt
        main.luminance() > 0.62f && alt != null && alt.luminance() < 0.62f -> alt
        else -> main
    }
    return pick ?: Color(0xFF3A4150)
}

private fun bandColor(c: Color): Color =
    lerp(c, Color(0xFF0B0E14), if (c.luminance() > 0.5f) 0.5f else if (c.luminance() > 0.25f) 0.25f else 0.08f)

private fun bandBase(t: TeamInfo?): Color {
    val main = teamColor(t)
    val alt = t?.altColor?.let { Color(it) }
    return if (main.luminance() < 0.04f && alt != null && alt.luminance() > main.luminance() + 0.05f) alt else main
}

internal fun teamBandBrush(team: TeamInfo?): Brush {
    val c = bandColor(bandBase(team))
    return Brush.horizontalGradient(
        0f to c.copy(alpha = 0.92f),
        0.7f to c.copy(alpha = 0.28f),
        1f to c.copy(alpha = 0.05f),
    )
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}
