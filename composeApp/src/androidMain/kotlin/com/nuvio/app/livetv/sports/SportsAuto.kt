package com.nuvio.app.livetv.sports

import android.content.Context
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.Reminder
import com.nuvio.app.livetv.reminders.LiveTvReminders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Reminders for the teams you follow, without asking for each game: a notification before every
 * game of a followed team (or league you follow whole) in the next day and a half, even with the app
 * closed. Looks again every ten minutes; games already given an alarm are simply set again.
 */
object SportsAuto {
    /** Games of teams, whole leagues, and pinned competitions you follow. */
    fun isFollowedGame(ev: SportsEvent, f: SportsFollows): Boolean =
        ev.leagueId in f.leagues ||
            ev.sides.any { f.followsTeam(ev.leagueId, it.team.id) } ||
            (ev.leagueId in f.pins && SportsCatalog.league(ev.leagueId)?.kind == LeagueKind.EVENT)

    suspend fun run(context: Context, live: LiveTv) {
        // Let the app finish starting before anything is fetched for this.
        delay(20_000)
        while (true) {
            runCatching {
                val settings = SportsSettings.fromJson(live.prefs.sportsSettingsJson.first())
                val f = SportsFollows.fromJson(live.prefs.sportsMineJson.first())
                val any = f.teams.isNotEmpty() || f.leagues.isNotEmpty() || f.pins.isNotEmpty()
                if (settings.remindMyTeams && any) {
                    // Touching the repository starts it, which is why it waits for someone to follow a team.
                    val repo = live.sports
                    val now = System.currentTimeMillis()
                    val lead = settings.remindMinutes.coerceAtLeast(0) * 60_000L
                    val games = repo.known.value
                        .filter { it.state == GameState.PRE && it.hasTime && it.source != EventSource.GUIDE && isFollowedGame(it, f) }
                        .filter { it.startMs > now && it.startMs < now + 36 * 3_600_000L }
                    android.util.Log.d("SportsAuto", "known=${repo.known.value.size} followed games in 36h=${games.size}, all upcoming followed=${repo.known.value.count { it.state == GameState.PRE && it.startMs > now && isFollowedGame(it, f) }}")
                    games.forEach { ev ->
                            val on = repo.bestChannel(ev.id)?.channel?.name
                            val league = SportsCatalog.league(ev.leagueId)?.name ?: "Sports"
                            // The alarm helper rings two minutes before the time it's given.
                            LiveTvReminders.schedule(
                                context,
                                Reminder("sport:" + ev.id, if (on != null) "$league  ·  $on" else league, ev.title, ev.startMs + 2 * 60_000L - lead),
                            )
                        }
                }
            }
            delay(10 * 60_000L)
        }
    }
}
