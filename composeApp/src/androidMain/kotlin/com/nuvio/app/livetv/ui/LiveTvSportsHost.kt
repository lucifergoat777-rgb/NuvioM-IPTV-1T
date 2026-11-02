package com.nuvio.app.livetv.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.sports.EventSource
import com.nuvio.app.livetv.sports.GameState
import com.nuvio.app.livetv.sports.SportsAuto
import com.nuvio.app.livetv.sports.SportsEvent
import com.nuvio.app.livetv.sports.SportsFollows
import com.nuvio.app.livetv.sports.SportsSettings
import kotlinx.coroutines.delay

/**
 * Wherever you are in the app: a message when a game you asked about (or a team you follow) is about
 * to start, and, when "Switch to the game" is on, the channel comes up by itself a minute before it
 * starts, with ten seconds' warning to cancel.
 */
@Composable
fun LiveTvSportsHost() {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val followsJson by live.prefs.sportsMineJson.collectAsStateWithLifecycle(null)
    val settingsJson by live.prefs.sportsSettingsJson.collectAsStateWithLifecycle(null)
    val follows = remember(followsJson) { SportsFollows.fromJson(followsJson) }
    val settings = remember(settingsJson) { SportsSettings.fromJson(settingsJson) }
    val watching = follows.reminders.isNotEmpty() ||
        (settings.remindMyTeams && (follows.teams.isNotEmpty() || follows.leagues.isNotEmpty() || follows.pins.isNotEmpty()))
    if (!watching) return

    val repo = live.sports
    var due by remember { mutableStateOf<Pair<SportsEvent, String>?>(null) }
    var armed by remember { mutableStateOf<Pair<SportsEvent, LiveChannel>?>(null) }
    var countdown by remember { mutableStateOf<Pair<SportsEvent, LiveChannel>?>(null) }
    val shown = remember { HashSet<String>() }

    fun switchNow(ev: SportsEvent, ch: LiveChannel) {
        armed = null; countdown = null; due = null
        repo.dropReminder(ev.id)
        com.nuvio.app.livetv.player.LiveTvReconnect.arm(ch.url, ch.key)
        LiveTvHost.playFromBrowse?.invoke(liveTvRequest(ch, live.repository.programs.value[ch.key], System.currentTimeMillis()))
    }

    // A minute before the start: ten seconds' warning, then the switch.
    LaunchedEffect(armed) {
        val (ev, ch) = armed ?: return@LaunchedEffect
        val at = maxOf(ev.startMs - 60_000L, System.currentTimeMillis() + 10_000L)
        delay((at - 10_000L - System.currentTimeMillis()).coerceAtLeast(0L))
        if (armed?.first?.id != ev.id) return@LaunchedEffect
        due = null
        countdown = ev to ch
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (due != null || countdown != null) continue
            val s = repo.settings.value
            val f = repo.follows.value
            val now = System.currentTimeMillis()
            val lead = s.remindMinutes.coerceAtLeast(0) * 60_000L
            val events = repo.known.value
            val manual = f.reminders.mapNotNull { r -> events.firstOrNull { it.id == r.eventId } }
            val auto = if (s.remindMyTeams) events.filter { ev ->
                ev.state == GameState.PRE && ev.hasTime && ev.source != EventSource.GUIDE && SportsAuto.isFollowedGame(ev, f)
            } else emptyList()
            val next = (manual + auto).distinctBy { it.id }.firstOrNull { ev ->
                ev.id !in shown && ev.hasTime && now >= ev.startMs - lead - 30_000 && now < ev.startMs + 10 * 60_000L
            }
            if (next != null) {
                shown += next.id
                val best = repo.bestChannel(next.id)?.takeIf { it.sure }?.channel
                if (best != null && repo.switchesFor(next)) armed = next to best
                val mins = ((next.startMs - now) / 60_000L).toInt()
                due = next to when {
                    mins <= 0 -> "Starting now"
                    mins == 1 -> "Starting in a minute"
                    else -> "Starting in $mins minutes"
                }
            }
            // Reminders for games long over are cleared away.
            runCatching {
                if (f.reminders.any { it.startMs < now - 3 * 60 * 60_000L }) {
                    repo.updateFollows { it.copy(reminders = it.reminders.filter { r -> r.startMs >= now - 3 * 60 * 60_000L }) }
                }
            }
        }
    }

    countdown?.let { (ev, ch) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Switching to the game") },
            text = { Text("${ev.title} is about to start. Switching to ${ch.name} in a few seconds.") },
            confirmButton = { TextButton(onClick = { switchNow(ev, ch) }) { Text("Switch now") } },
            dismissButton = { TextButton(onClick = { armed = null; countdown = null; repo.dropReminder(ev.id) }) { Text("Cancel") } },
        )
        LaunchedEffect(ev.id) {
            delay(10_000)
            if (countdown?.first?.id == ev.id) switchNow(ev, ch)
        }
    }
    due?.let { (ev, heading) ->
        val best = repo.bestChannel(ev.id)?.channel
        val switching = armed?.takeIf { it.first.id == ev.id }?.second
        AlertDialog(
            onDismissRequest = { due = null; if (armed?.first?.id != ev.id) repo.dropReminder(ev.id) },
            title = { Text(heading) },
            text = {
                Text(
                    ev.title + (best?.let { "\nOn ${it.name}" } ?: "\nNo channel found for it yet") +
                        (switching?.let { "\nSwitching to ${it.name} a minute before it starts." } ?: ""),
                )
            },
            confirmButton = {
                if (best != null) TextButton(onClick = { switchNow(ev, best) }) { Text("Watch") }
                else TextButton(onClick = { due = null; repo.dropReminder(ev.id) }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { due = null; armed = null; repo.dropReminder(ev.id) }) { Text(if (switching != null) "Don't switch" else "Dismiss") }
            },
        )
    }
}
