package com.nuvio.app.livetv.calendar

import android.content.Context
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.livetv.LiveTv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One episode on the Calendar. */
data class CalendarItem(
    val showName: String,
    val image: String?,
    val season: Int,
    val episode: Int,
    val title: String?,
    val airMs: Long,
    val network: String?,
    val streaming: Boolean,
    val runtime: Int?,
    val summary: String?,
    val imdb: String? = null,
    /** A movie's release (in theaters, at home, on disc): [title] says which. */
    val isMovie: Boolean = false,
)

/** A showing of a show on one of your channels, from the guide. */
data class OnMyTv(val channel: com.nuvio.app.livetv.model.LiveChannel, val program: com.nuvio.app.livetv.model.EpgProgram)

data class CalendarUi(
    val loading: Boolean = false,
    val shows: Int = 0,
    val upcoming: List<CalendarItem> = emptyList(),
    val recent: List<CalendarItem> = emptyList(),
    val today: List<CalendarItem> = emptyList(),
    val todayLoading: Boolean = false,
    val note: String? = null,
    /** Shows you follow (a bell), and shows you took off the Calendar: imdb id to name. */
    val followed: Map<String, String> = emptyMap(),
    val hidden: Map<String, String> = emptyMap(),
)

/**
 * The Calendar on mobile: when the next episodes of the shows you watch and keep in your Library come
 * out (dates from TVmaze), and what's airing today. Looks shows up gently (TVmaze allows a couple of
 * requests a second) and keeps what it found on the phone.
 */
class MobileCalendar(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sources = CalendarSources(context)
    private val _state = MutableStateFlow(CalendarUi())
    val state: StateFlow<CalendarUi> = _state.asStateFlow()
    private val prefs get() = LiveTv.get(context).prefs

    private fun decode(raw: String): Map<String, String> =
        raw.lines().mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] } }.toMap(LinkedHashMap())

    private fun encode(m: Map<String, String>) = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    /** Reads who you follow and what you hid, so the page shows them (and they are looked up first). */
    private suspend fun loadLists() {
        val f = decode(prefs.calendarFollows.first())
        val h = decode(prefs.calendarHiddenShows.first())
        _state.value = _state.value.copy(followed = f, hidden = h)
    }

    /** Rings or un-rings the bell on a show. Following one also puts it back if it was hidden. */
    fun toggleFollow(imdb: String, name: String) {
        scope.launch {
            val f = decode(prefs.calendarFollows.first()).toMutableMap()
            if (f.remove(imdb) == null) f[imdb] = name
            prefs.editCalendarFollows { encode(f) }
            prefs.editCalendarHiddenShows { encode(decode(it) - imdb) }
            loadLists()
            refresh(force = false)
        }
    }

    fun hide(imdb: String, name: String) {
        scope.launch {
            prefs.editCalendarHiddenShows { encode(decode(it) + (imdb to name)) }
            prefs.editCalendarFollows { encode(decode(it) - imdb) }
            loadLists()
            refresh(force = false)
        }
    }

    fun unhide(imdb: String) {
        scope.launch {
            prefs.editCalendarHiddenShows { encode(decode(it) - imdb) }
            loadLists()
            refresh(force = false)
        }
    }

    private fun plain(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Where your channels show [showName] in the next week, from the guide. Matches the whole title
     * (so "The Office" doesn't pick up "The Office Tour"), never from hidden or PIN-locked groups.
     */
    suspend fun onMyTv(showName: String): List<OnMyTv> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val live = LiveTv.get(context)
        val now = System.currentTimeMillis()
        val hits = runCatching { live.guideDb.findByWords(now - 30 * 60_000L, now + 7 * 24 * 3_600_000L, now, listOf(showName), inDescription = false) }
            .getOrDefault(emptyList())
        val want = plain(showName)
        val user = live.userState.value
        val settings = live.prefs.currentSettings()
        val byKey = live.store.displayChannels.value.associateBy { it.key }
        hits.mapNotNull { (key, p) ->
            val ch = byKey[key] ?: return@mapNotNull null
            if (plain(p.title) != want) return@mapNotNull null
            if (ch.key in user.hiddenChannels || ch.groupId in user.hiddenGroups || ch.groupId in user.lockedGroups) return@mapNotNull null
            if (settings.parentalPin.length >= 4 && settings.lockAdultContent && com.nuvio.app.livetv.parental.ParentalControls.isAdult(ch.group)) return@mapNotNull null
            OnMyTv(ch, p)
        }.sortedBy { it.program.startMs }.take(10)
    }

    private data class WantedMovie(val name: String, val imdb: String?, val tmdb: Int?)

    private fun wantedMovies(): List<WantedMovie> {
        val hiddenIds = _state.value.hidden.keys
        return LibraryRepository.uiState.value.items
            .filter { it.type.equals("movie", true) }
            .map { WantedMovie(it.name, it.imdbId ?: it.id.takeIf { id -> id.startsWith("tt") }, it.tmdbId) }
            .filter { it.imdb == null || it.imdb !in hiddenIds }
            .take(40)
    }

    private var job: Job? = null
    private var todayJob: Job? = null

    /** The shows to follow: series in your Library, and series you've watched lately. */
    private data class Wanted(val name: String, val imdb: String?, val tmdb: Int?)

    private fun wanted(): List<Wanted> {
        val out = LinkedHashMap<String, Wanted>()
        val hiddenIds = _state.value.hidden.keys
        // Shows with a bell come first.
        _state.value.followed.forEach { (imdb, name) -> out[imdb] = Wanted(name, imdb.takeIf { it.startsWith("tt") }, null) }
        WatchProgressRepository.uiState.value.entries
            .sortedByDescending { it.lastUpdatedEpochMs }
            .filter { it.parentMetaType.equals("series", true) || it.parentMetaType.equals("tv", true) }
            .forEach { e ->
                val id = e.parentMetaId
                if (id.startsWith("tt")) out.putIfAbsent(id, Wanted(e.title.substringBefore(" - ").ifBlank { e.title }, id, null))
            }
        LibraryRepository.uiState.value.items
            .filter { it.type.equals("series", true) || it.type.equals("tv", true) }
            .forEach { i ->
                val imdb = i.imdbId ?: i.id.takeIf { it.startsWith("tt") }
                val key = imdb ?: i.id
                out.putIfAbsent(key, Wanted(i.name, imdb, i.tmdbId))
            }
        return out.filterKeys { it !in hiddenIds }.values.filter { w -> w.imdb == null || w.imdb !in hiddenIds }.take(40)
    }

    fun refresh(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            loadLists()
            val list = wanted()
            _state.value = _state.value.copy(loading = true, shows = list.size, note = null)
            val now = System.currentTimeMillis()
            val upcoming = ArrayList<CalendarItem>()
            val recent = ArrayList<CalendarItem>()
            for ((index, w) in list.withIndex()) {
                val id = runCatching { sources.tvmazeId(w.imdb, w.tmdb) }.getOrNull() ?: continue
                val cached = sources.cachedShow(id)
                val fresh = cached != null && !force && now - cached.fetchedAt < 6 * CalendarSources.HOUR
                val data = if (fresh) cached else (runCatching { sources.fetchShow(id) }.getOrNull() ?: cached) ?: continue
                for (e in data.episodes) {
                    val air = e.airMs ?: e.airDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: continue
                    val item = CalendarItem(
                        showName = data.show.name, image = e.image ?: data.show.image,
                        season = e.season, episode = e.number, title = e.name, airMs = air,
                        network = data.show.network, streaming = data.show.streaming, runtime = e.runtime, summary = e.summary,
                        imdb = data.show.imdb ?: w.imdb,
                    )
                    when {
                        air >= now - 2 * CalendarSources.HOUR && air <= now + 60 * CalendarSources.DAY -> upcoming += item
                        air < now && air >= now - 21 * CalendarSources.DAY -> recent += item
                    }
                }
                // Show what's found so far, so the page fills as it goes.
                if (index % 3 == 2 || index == list.lastIndex) {
                    _state.value = _state.value.copy(
                        upcoming = upcoming.sortedBy { it.airMs }.take(300),
                        recent = recent.sortedByDescending { it.airMs }.take(300),
                    )
                }
            }
            // Movies in your Library: when they come out in theaters, at home and on disc.
            val country = runCatching { prefs.currentSettings().calendarCountry.ifBlank { "US" } }.getOrDefault("US")
            for (m in wantedMovies()) {
                val d = runCatching { sources.movieDates(m.imdb, m.tmdb, country) }.getOrNull() ?: continue
                val zone = ZoneId.systemDefault()
                listOf("In theaters" to d.theater, "At home" to d.digital, "On disc" to d.disc).forEach { (what, day) ->
                    val ms = day?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: return@forEach
                    val item = CalendarItem(
                        showName = d.title ?: m.name, image = d.backdrop ?: d.poster, season = 0, episode = 0, title = what, airMs = ms,
                        network = null, streaming = false, runtime = null, summary = d.overview, imdb = m.imdb, isMovie = true,
                    )
                    when {
                        ms >= now - 2 * CalendarSources.HOUR && ms <= now + 90 * CalendarSources.DAY -> upcoming += item
                        ms < now && ms >= now - 21 * CalendarSources.DAY -> recent += item
                    }
                }
            }
            sources.saveMovies()
            _state.value = _state.value.copy(
                loading = false,
                upcoming = upcoming.sortedBy { it.airMs }.take(300),
                recent = recent.sortedByDescending { it.airMs }.take(300),
                note = if (list.isEmpty() && wantedMovies().isEmpty()) "Watch a show or add one to your Library, and its next episodes show up here." else null,
            )
        }
    }

    /** Everything with a new episode today: TV in your country, and streaming. */
    fun loadToday(force: Boolean = false) {
        if (todayJob?.isActive == true) return
        if (!force && _state.value.today.isNotEmpty()) return
        todayJob = scope.launch {
            _state.value = _state.value.copy(todayLoading = true)
            val country = runCatching {
                kotlinx.coroutines.runBlocking { LiveTv.get(context).prefs.currentSettings().calendarCountry }
            }.getOrDefault("US")
            val list = runCatching { sources.airingOn(LocalDate.now(), country) }.getOrDefault(emptyList())
            val items = list.mapNotNull { (show, e) ->
                val air = e.airMs ?: return@mapNotNull null
                CalendarItem(
                    showName = show.name, image = e.image ?: show.image, season = e.season, episode = e.number, title = e.name,
                    airMs = air, network = show.network, streaming = show.streaming, runtime = e.runtime, summary = e.summary,
                    imdb = show.imdb,
                )
            }.sortedBy { it.airMs }
            _state.value = _state.value.copy(today = items, todayLoading = false)
        }
    }
}
