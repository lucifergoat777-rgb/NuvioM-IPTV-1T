package com.nuvio.app.livetv.ondemand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** "Fix poster": the right poster for a title, looked up on TMDB by the name (and year) you type. */
object VodPosterFix {
    /** The poster address for [title] ([year] when known); null when nothing matches or there is no key. */
    suspend fun find(kind: VodKind, title: String, year: Int?): String? = withContext(Dispatchers.IO) {
        val key = com.nuvio.app.features.tmdb.TmdbConfig.API_KEY
        if (key.isBlank() || title.isBlank()) return@withContext null
        val path = if (kind == VodKind.SERIES) "tv" else "movie"
        val yearParam = year?.let { if (kind == VodKind.SERIES) "&first_air_date_year=$it" else "&year=$it" }.orEmpty()
        val url = "https://api.themoviedb.org/3/search/$path?api_key=$key&include_adult=false&query=${URLEncoder.encode(title.trim(), "UTF-8")}$yearParam"
        val json = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 12_000
            try { if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().use { it.readText() }) else null } finally { c.disconnect() }
        }.getOrNull() ?: return@withContext null
        val results = json.optJSONArray("results") ?: return@withContext null
        for (i in 0 until results.length()) {
            val p = results.optJSONObject(i)?.optString("poster_path")?.takeIf { it.startsWith("/") } ?: continue
            return@withContext "https://image.tmdb.org/t/p/w342$p"
        }
        null
    }

    /** "Title" or "Title (2015)" into a name and a year. */
    fun parse(text: String): Pair<String, Int?> {
        val m = Regex("""^(.*?)[\s]*[(\[]?((?:19|20)\d{2})[)\]]?$""").find(text.trim())
        return if (m != null && m.groupValues[1].isNotBlank()) m.groupValues[1].trim() to m.groupValues[2].toIntOrNull() else text.trim() to null
    }
}
