package com.nuvio.app.livetv.ondemand

import android.content.Context
import org.json.JSONObject

/**
 * Where you got to in On Demand movies and episodes: "continue watching" for them, kept out of
 * Nuvio's own history. One entry per playable link; saved by the player, read by Home and the
 * details page.
 */
object VodResume {
    private const val PREFS = "livetv_vod_resume"
    private const val MAX = 40
    const val ID_PREFIX = "vod:"

    data class Entry(
        val key: String,
        val title: String,
        val poster: String?,
        val url: String,
        val headers: Map<String, String>,
        val positionMs: Long,
        val durationMs: Long,
        val updatedMs: Long,
    )

    fun keyFor(url: String) = ID_PREFIX + Math.abs(url.hashCode()).toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Notes what's being played, so it can be listed and reopened later. */
    fun remember(context: Context, title: String, poster: String?, url: String, headers: Map<String, String>) {
        val key = keyFor(url)
        runCatching {
            val p = prefs(context)
            val old = p.getString(key, null)?.let { JSONObject(it) }
            val o = JSONObject()
                .put("title", title).put("poster", poster ?: "").put("url", url)
                .put("headers", JSONObject(headers))
                .put("pos", old?.optLong("pos", 0L) ?: 0L).put("dur", old?.optLong("dur", 0L) ?: 0L)
                .put("at", System.currentTimeMillis())
            p.edit().putString(key, o.toString()).apply()
            trim(context)
        }
    }

    /** From the player: [contentId] is "livetv:vod:..." (or the bare key). */
    fun save(context: Context, contentId: String?, positionMs: Long, durationMs: Long) {
        val key = contentId?.removePrefix("livetv:")?.takeIf { it.startsWith(ID_PREFIX) } ?: return
        if (positionMs < 1000) return
        runCatching {
            val p = prefs(context)
            val old = p.getString(key, null)?.let { JSONObject(it) } ?: return
            // The end counts as watched: gone from the list, and starts over next time.
            if (durationMs > 0 && positionMs >= durationMs - 60_000L) { p.edit().remove(key).apply(); return }
            old.put("pos", positionMs).put("dur", durationMs).put("at", System.currentTimeMillis())
            p.edit().putString(key, old.toString()).apply()
        }
    }

    fun positionFor(context: Context, url: String): Long =
        runCatching { prefs(context).getString(keyFor(url), null)?.let { JSONObject(it).optLong("pos", 0L) } }.getOrNull() ?: 0L

    /** Things you've started and not finished, newest first. */
    fun unfinished(context: Context): List<Entry> = runCatching {
        prefs(context).all.mapNotNull { (key, v) ->
            val o = JSONObject(v as? String ?: return@mapNotNull null)
            val pos = o.optLong("pos", 0L)
            if (pos < 1000) return@mapNotNull null
            val h = o.optJSONObject("headers")
            Entry(
                key, o.optString("title"), o.optString("poster").ifBlank { null }, o.optString("url"),
                h?.keys()?.asSequence()?.associateWith { h.optString(it) } ?: emptyMap(),
                pos, o.optLong("dur", 0L), o.optLong("at", 0L),
            )
        }.sortedByDescending { it.updatedMs }
    }.getOrDefault(emptyList())

    fun remove(context: Context, key: String) { runCatching { prefs(context).edit().remove(key).apply() } }

    private fun trim(context: Context) {
        val p = prefs(context)
        if (p.all.size <= MAX) return
        val keep = p.all.mapNotNull { (k, v) -> (v as? String)?.let { k to (runCatching { JSONObject(it).optLong("at") }.getOrDefault(0L)) } }
            .sortedByDescending { it.second }.take(MAX).map { it.first }.toSet()
        p.edit().apply { p.all.keys.filter { it !in keep }.forEach { remove(it) } }.apply()
    }
}
