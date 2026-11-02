package com.nuvio.app.livetv.data

import com.nuvio.app.livetv.model.PlaylistSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One live channel from a Stalker (Ministra) portal. */
class StalkerChannel(
    val id: String,
    val name: String,
    val number: Int,
    /** What the portal wants back to give out a stream link (for example "ffrt http://localhost/ch/12"). */
    val cmd: String,
    val logo: String?,
    val genreId: String,
    /** The id its guide uses, when it has one. */
    val epgId: String?,
    val archiveDays: Int,
)

class StalkerProgram(val startSec: Long, val stopSec: Long, val title: String, val description: String?)

class StalkerException(message: String) : Exception(message)

/**
 * Talks to a Stalker / Ministra IPTV portal the way a MAG set-top box does: a handshake gives a token, the
 * token goes with every request, and each channel's stream link is asked for just before it plays (the links
 * are temporary).
 */
class StalkerClient(
    portal: String,
    mac: String,
    private val http: OkHttpClient,
    private val userAgent: String = STB_USER_AGENT,
) {
    private val origin: String
    private val prefix: String
    private val candidates: List<String>
    val mac: String = normalizeMac(mac)

    @Volatile private var api: String? = null
    @Volatile private var token: String = ""

    init {
        val parts = splitPortal(portal)
        origin = parts.first
        prefix = parts.second
        candidates = endpointCandidates(portal)
    }

    private val serial: String = sha256(this.mac).take(13).uppercase()
    private val deviceId: String = sha256(this.mac + "id")

    /** Where logos with just a file name live. */
    private val logoBase: String get() = "$origin$prefix/misc/logos/320/"

    private fun builder(url: String): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", userAgent)
        .header("X-User-Agent", "Model: MAG250; Link: WiFi")
        .header("Accept", "*/*")
        .header("Referer", "$origin$prefix/c/")
        .header("Cookie", "mac=$mac; stb_lang=en; timezone=GMT")
        .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }

    private fun query(endpoint: String, vararg params: Pair<String, String>): String =
        endpoint + (if (endpoint.contains('?')) "&" else "?") +
            params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" } + "&JsHttpRequest=1-xml"

    private fun call(url: String): JSONObject {
        http.newCall(builder(url).build()).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw StalkerException("The portal refused this device (HTTP ${response.code}). Check the MAC address.")
            if (!response.isSuccessful) throw StalkerException("The portal answered HTTP ${response.code}")
            val text = (response.body?.string() ?: "").trim().removePrefix("﻿")
            return parseJson(text) ?: throw StalkerException("The portal didn't answer like a Stalker portal")
        }
    }

    /** Finds the portal's address for requests, and signs in. Done again when the token stops working. */
    @Synchronized
    fun login(force: Boolean = false) {
        if (!force && api != null && token.isNotEmpty()) return
        var failure: Exception? = null
        var refused: Exception? = null
        for (endpoint in (api?.let { listOf(it) } ?: candidates)) {
            try {
                token = ""
                val hello = call(query(endpoint, "type" to "stb", "action" to "handshake", "token" to "", "prehash" to ""))
                val fresh = hello.optJSONObject("js")?.optString("token").orEmpty()
                if (fresh.isBlank()) throw StalkerException("No token from the portal")
                token = fresh
                api = endpoint
                // Introduces this "box"; many portals refuse channel requests until it has been done.
                runCatching {
                    call(query(endpoint,
                        "type" to "stb", "action" to "get_profile", "hd" to "1",
                        "ver" to "ImageDescription: 0.2.18-r14-pub-250; ImageDate: Fri Jan 15 15:20:44 EET 2016; PORTAL version: 5.5.0; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
                        "num_banks" to "2", "sn" to serial, "stb_type" to "MAG250", "client_type" to "STB",
                        "image_version" to "218", "video_out" to "hdmi", "device_id" to deviceId, "device_id2" to deviceId,
                        "signature" to "", "auth_second_step" to "1", "hw_version" to "1.7-BD-00", "not_valid_token" to "0",
                        "api_signature" to "262", "prehash" to "",
                        "metrics" to JSONObject().put("mac", mac).put("sn", serial).put("type", "STB").put("model", "MAG250").put("uid", "").put("random", "").toString(),
                    ))
                }
                return
            } catch (e: Exception) {
                failure = e
                // A portal that answered but turned this device away says more than the addresses that were just wrong.
                if (e.message?.contains("refused this device") == true) refused = e
            }
        }
        token = ""
        api = null
        throw StalkerException((refused ?: failure)?.message ?: "Couldn't reach the portal")
    }

    /** Runs a request, signing in first, and again once if the portal says the token is no good. */
    private fun <T> withSession(block: (String) -> T): T {
        login()
        return try {
            block(api!!)
        } catch (e: StalkerException) {
            login(force = true)
            block(api!!)
        }
    }

    fun genres(): Map<String, String> = withSession { endpoint ->
        val js = call(query(endpoint, "type" to "itv", "action" to "get_genres")).opt("js")
        val out = LinkedHashMap<String, String>()
        val arr = (js as? JSONArray) ?: (js as? JSONObject)?.optJSONArray("data")
        if (arr != null) for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            out[g.optString("id")] = g.optString("title").ifBlank { g.optString("name") }
        }
        out
    }

    fun channels(): List<StalkerChannel> = withSession { endpoint ->
        val js = call(query(endpoint, "type" to "itv", "action" to "get_all_channels")).opt("js")
        parseChannels(js, logoBase)
    }

    /** A playable address for [cmd]; links are temporary, so this is asked for just before playing. */
    fun createLink(cmd: String): String = withSession { endpoint ->
        val js = call(query(endpoint, "type" to "itv", "action" to "create_link", "cmd" to cmd, "series" to "",
            "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0", "force_ch_link_check" to "0")).optJSONObject("js")
        val link = cleanLink(js?.optString("cmd").orEmpty())
        if (link.isBlank()) throw StalkerException("The portal gave no stream link for this channel")
        link
    }

    /** What's on, for every channel, from now for [hours]. Some portals don't offer it: then an empty map. */
    fun guide(hours: Int = 24): Map<String, List<StalkerProgram>> = runCatching {
        withSession { endpoint ->
            val js = call(query(endpoint, "type" to "itv", "action" to "get_epg_info", "period" to hours.toString())).opt("js")
            parseGuide(js)
        }
    }.getOrDefault(emptyMap())

    companion object {
        const val STB_USER_AGENT =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        /** "00:1A:79:AB:CD:EF" from whatever way it was typed. */
        fun normalizeMac(raw: String): String {
            val hex = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
            return if (hex.length == 12) hex.chunked(2).joinToString(":") else raw.trim().uppercase()
        }

        fun isValidMac(raw: String): Boolean = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(normalizeMac(raw))

        private fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** scheme://host:port, and the part of the path in front of "/c" ("/stalker_portal", or nothing). */
        fun splitPortal(raw: String): Pair<String, String> {
            var s = PlaylistSource.tidyLink(raw)
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            val url = runCatching { s.toHttpUrl() }.getOrNull()
                ?: throw StalkerException("That doesn't look like a portal address")
            val origin = "${url.scheme}://${url.host}" + if (url.port != (if (url.scheme == "https") 443 else 80)) ":${url.port}" else ""
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            val cut = segments.indexOfFirst { it == "c" || it.endsWith(".php") || it == "server" }
            val keep = if (cut >= 0) segments.take(cut) else segments
            return origin to keep.joinToString("") { "/$it" }
        }

        /** The addresses a portal's requests can go to, most likely first. */
        fun endpointCandidates(raw: String): List<String> {
            val (origin, prefix) = splitPortal(raw)
            val out = LinkedHashSet<String>()
            val typed = PlaylistSource.tidyLink(raw)
            if (typed.contains("load.php", true) || typed.contains("portal.php", true)) out += typed.substringBefore('?')
            out += "$origin$prefix/portal.php"
            out += "$origin$prefix/server/load.php"
            if (prefix.isEmpty()) out += "$origin/stalker_portal/server/load.php"
            return out.toList()
        }

        /** The JSON a portal answers with; null when it isn't JSON (a web page, an error). */
        fun parseJson(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()

        /** "ffmpeg http://host/stream" or "auto http://…" into just the address. */
        fun cleanLink(raw: String): String {
            val t = raw.trim()
            val at = t.indexOf("://")
            if (at < 0) return ""
            val start = t.lastIndexOf(' ', at).let { if (it < 0) 0 else it + 1 }
            return t.substring(start).trim()
        }

        fun parseChannels(js: Any?, logoBase: String): List<StalkerChannel> {
            val arr = (js as? JSONArray) ?: (js as? JSONObject)?.optJSONArray("data") ?: return emptyList()
            val out = ArrayList<StalkerChannel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val cmd = o.optString("cmd")
                if (id.isBlank() || cmd.isBlank()) continue
                val logo = o.optString("logo").trim().takeIf { it.isNotBlank() && it != "null" }
                out += StalkerChannel(
                    id = id,
                    name = o.optString("name").ifBlank { "Channel $id" }.replace("\n", " ").trim(),
                    number = o.optString("number").toIntOrNull() ?: (i + 1),
                    cmd = cmd,
                    logo = logo?.let { if (it.startsWith("http", true)) it else logoBase + it },
                    genreId = o.optString("tv_genre_id"),
                    epgId = o.optString("xmltv_id").takeIf { it.isNotBlank() && it != "null" },
                    archiveDays = if (o.optString("tv_archive") == "1" || o.optInt("tv_archive") == 1) {
                        o.optString("tv_archive_duration").toIntOrNull()?.takeIf { it > 0 } ?: 1
                    } else 0,
                )
            }
            return out
        }

        fun parseGuide(js: Any?): Map<String, List<StalkerProgram>> {
            val data = (js as? JSONObject)?.opt("data") ?: js
            val out = LinkedHashMap<String, List<StalkerProgram>>()
            fun readList(arr: JSONArray?): List<StalkerProgram> {
                if (arr == null) return emptyList()
                val list = ArrayList<StalkerProgram>(arr.length())
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val start = p.optString("start_timestamp").toLongOrNull() ?: p.optString("start").toLongOrNull() ?: continue
                    val stop = p.optString("stop_timestamp").toLongOrNull() ?: p.optString("stop").toLongOrNull() ?: continue
                    val title = p.optString("name").ifBlank { p.optString("title") }
                    if (title.isBlank() || stop <= start) continue
                    list += StalkerProgram(start, stop, title, p.optString("descr").ifBlank { p.optString("description") }.takeIf { it.isNotBlank() })
                }
                return list
            }
            if (data is JSONObject) {
                data.keys().forEach { channelId -> out[channelId] = readList(data.optJSONArray(channelId)) }
            }
            return out.filterValues { it.isNotEmpty() }
        }

        /** The guide as an XMLTV file, so it goes through the same matching as any other guide. */
        fun xmltv(channels: List<Pair<String, StalkerChannel>>, guide: Map<String, List<StalkerProgram>>): String {
            fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n")
            for ((tvgId, ch) in channels) {
                if (guide[ch.id].isNullOrEmpty()) continue
                sb.append("<channel id=\"").append(esc(tvgId)).append("\"><display-name>").append(esc(ch.name)).append("</display-name></channel>\n")
            }
            for ((tvgId, ch) in channels) {
                for (p in guide[ch.id].orEmpty()) {
                    sb.append("<programme start=\"").append(fmt.format(java.util.Date(p.startSec * 1000)))
                        .append("\" stop=\"").append(fmt.format(java.util.Date(p.stopSec * 1000)))
                        .append("\" channel=\"").append(esc(tvgId)).append("\"><title>").append(esc(p.title)).append("</title>")
                    p.description?.let { sb.append("<desc>").append(esc(it)).append("</desc>") }
                    sb.append("</programme>\n")
                }
            }
            return sb.append("</tv>\n").toString()
        }

        /** The id a channel has in the playlist and the guide. */
        fun tvgId(ch: StalkerChannel): String = ch.epgId ?: "stalker.${ch.id}"

        /** The pseudo link a channel is saved under; it becomes a real link only when played. */
        fun channelUrl(sourceId: String, ch: StalkerChannel, format: String = "auto"): String =
            "${StalkerLinks.SCHEME}$sourceId/${ch.id}?fmt=$format&cmd=${URLEncoder.encode(ch.cmd, "UTF-8")}"

        /** "hls" or "ts" for a real stream link, going by its address. */
        fun formatOf(link: String): String = if (link.substringBefore('?').lowercase().endsWith(".m3u8") || link.lowercase().contains(".m3u8")) "hls" else "ts"
    }
}

/**
 * The bridge between the saved channel links (stalker://source/channel?cmd=…) and the real, temporary
 * stream links the portal hands out. Asked from the player's loading thread, so it may block.
 */
object StalkerLinks {
    const val SCHEME = "stalker://"
    const val EPG_SCHEME = "stalker-epg://"

    private class Config(val portal: String, val mac: String)
    private class Cached(val link: String, val at: Long)

    private val configs = ConcurrentHashMap<String, Config>()
    private val clients = ConcurrentHashMap<String, StalkerClient>()
    private val recent = Collections.synchronizedMap(HashMap<String, Cached>())

    @Volatile private var shared: OkHttpClient? = null

    /** The app's own HTTP client (it copes with portals that have outdated certificates). */
    fun useHttp(client: OkHttpClient) {
        shared = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    }

    private val http: OkHttpClient
        get() = shared ?: OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).followRedirects(true).build()
            .also { shared = it }

    fun isStalker(url: String?): Boolean = url != null && url.startsWith(SCHEME)

    /** Keeps the portals' details current as sources are added, changed and removed. */
    fun configure(playlists: List<PlaylistSource>) {
        val live = playlists.filter { it.isStalker }.associateBy { it.id }
        configs.keys.filter { it !in live }.forEach { configs.remove(it); clients.remove(it) }
        live.forEach { (id, p) ->
            val next = Config(p.stalkerPortal, p.stalkerMac)
            val old = configs[id]
            if (old == null || old.portal != next.portal || old.mac != next.mac) {
                configs[id] = next
                clients.remove(id)
            }
        }
    }

    fun client(sourceId: String): StalkerClient? {
        val c = configs[sourceId] ?: return null
        return clients.getOrPut(sourceId) { StalkerClient(c.portal, c.mac, http) }
    }

    /** Parts of a saved link: the source and the portal's command. */
    fun parse(url: String): Pair<String, String>? {
        if (!isStalker(url)) return null
        val rest = url.removePrefix(SCHEME)
        val sourceId = rest.substringBefore('/')
        val cmd = rest.substringAfter("&cmd=", "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: return null
        return sourceId to cmd
    }

    /** "application/x-mpegURL" or "video/mp2t" when the portal's streams are known to be one or the other; else null. */
    fun mimeHint(url: String): String? = when (url.substringAfter("?fmt=", "").substringBefore('&')) {
        "hls" -> "application/x-mpegURL"
        "ts" -> "video/mp2t"
        else -> null
    }

    /** The real stream link for a saved one. Links are good for a short while, so they're reused for a few seconds. */
    fun resolve(url: String): String {
        if (!isStalker(url)) return url
        recent[url]?.takeIf { System.currentTimeMillis() - it.at < REUSE_MS }?.let { return it.link }
        val (sourceId, cmd) = parse(url) ?: throw StalkerException("Not a portal link")
        val client = client(sourceId) ?: throw StalkerException("This portal isn't set up any more")
        val link = client.createLink(cmd)
        recent[url] = Cached(link, System.currentTimeMillis())
        return link
    }

    /** [resolve] from any thread, the main one included (the request is then made on a background thread). */
    fun resolveOffMain(url: String): String =
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { resolveOrSelf(url) }
        } else resolveOrSelf(url)

    /** [resolve], or the link as it is when the portal can't be reached (so callers can try and fail in their own way). */
    fun resolveOrSelf(url: String): String = if (!isStalker(url)) url else runCatching { resolve(url) }.getOrDefault(url)

    private const val REUSE_MS = 15_000L
}
