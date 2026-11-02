package com.nuvio.app.youtube

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Serves a video's DASH manifest on the loopback address.
 *
 * YouTube's separate video and audio streams reach the player as a generated DASH manifest. It is
 * too big to carry in a navigation route and the player wants a URL, so the manifest is held here
 * and handed out as `http://127.0.0.1:<port>/yt/<videoId>.mpd`. Only the device itself can reach it.
 *
 * It also serves the auto-translated captions. YouTube refuses to hand those out without a token only its own
 * apps can make (HTTP 429), so when a translated language is picked, the video's own captions are fetched and
 * translated here instead.
 */
object YouTubeMpdServer {
    private const val TAG = "YouTubeCaptions"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    /** How much text goes to the translator in one request. */
    private const val CHUNK_CHARS = 4500
    /** How many translation requests may run at once. */
    private const val MAX_PARALLEL = 2

    /** When the translator says "too many requests", nothing more is sent to it until this time. */
    @Volatile private var pausedUntil = 0L
    private const val PAUSE_MS = 90_000L

    /** What became of one request: its text, "slow down", or a failure. */
    private sealed interface Reply {
        class Text(val text: String) : Reply
        object SlowDown : Reply
        object Failed : Reply
    }

    private val manifests = ConcurrentHashMap<String, ByteArray>()
    private val pool = java.util.concurrent.ThreadPoolExecutor(
        8, 8, 30L, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue()
    ).apply { allowCoreThreadTimeOut(true) }

    @Volatile private var server: ServerSocket? = null

    /** Local manifest address -> the video's own single stream (picture and sound together), for Cast. */
    private val castUrls = ConcurrentHashMap<String, String>()

    fun registerCast(manifestUrl: String, streamUrl: String) { if (castUrls.size > 128) castUrls.clear(); castUrls[manifestUrl] = streamUrl }

    /** What a Cast device can open instead of [url] (which only this phone can reach), if there is something. */
    fun castUrlFor(url: String): String? = castUrls[url]

    /** The stored manifest for [videoId], for casting (see CastRelay). */
    fun manifestBytes(videoId: String): ByteArray? = manifests[videoId]

    /** Stores [mpd] and returns the URL to play it from. */
    @Synchronized
    fun publish(videoId: String, mpd: ByteArray): String {
        // Kept at least as long as the view model keeps its cached lookups.
        if (manifests.size > 64) manifests.clear()
        manifests[videoId] = rewriteTranslatedCaptions(videoId, YouTubeAudioTracks.tidy(mpd))
        return "http://127.0.0.1:${ensureStarted().localPort}/yt/$videoId.mpd"
    }

    // ---- translated captions ----

    /** Each video's translated caption addresses, in the order they were swapped out of the manifest. */
    private val captionUrls = ConcurrentHashMap<String, List<String>>()
    private val translatedCache = ConcurrentHashMap<String, ByteArray>()
    private val http by lazy { OkHttpClient.Builder().followRedirects(true).build() }

    /** For the translator: a redirect there means "slow down", so it isn't followed. */
    private val translator by lazy { http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private fun rewriteTranslatedCaptions(videoId: String, mpd: ByteArray): ByteArray {
        val port = ensureStarted().localPort
        val urls = ArrayList<String>()
        val rewritten = Regex("<BaseURL>([^<]*timedtext[^<]*)</BaseURL>").replace(String(mpd, Charsets.UTF_8)) { m ->
            val url = m.groupValues[1].replace("&amp;", "&")
            if ("tlang=" !in url) m.value
            else {
                urls += url
                "<BaseURL>http://127.0.0.1:$port/yt/cap/$videoId/${urls.size - 1}</BaseURL>"
            }
        }
        captionUrls[videoId] = urls
        if (translatedCache.size > 12) translatedCache.clear()
        return withLanguageCodes(rewritten).toByteArray(Charsets.UTF_8)
    }

    /** English language name (lower case) -> its code, for the names YouTube's manifest uses in place of codes. */
    private val languageCodes: Map<String, String> by lazy {
        val map = HashMap<String, String>()
        java.util.Locale.getAvailableLocales().forEach { l ->
            val name = l.getDisplayLanguage(java.util.Locale.ENGLISH).lowercase()
            if (name.isNotBlank() && l.language.isNotBlank()) map.putIfAbsent(name, l.language)
        }
        // Names the device's own language data may not have.
        mapOf(
            "abkhazian" to "ab", "afar" to "aa", "akan" to "ak", "aymara" to "ay", "bangla" to "bn", "bashkir" to "ba",
            "bhojpuri" to "bho", "cebuano" to "ceb", "divehi" to "dv", "dzongkha" to "dz", "ewe" to "ee", "fijian" to "fj",
            "filipino" to "fil", "ganda" to "lg", "haitian creole" to "ht", "hawaiian" to "haw", "hmong" to "hmn",
            "inuktitut" to "iu", "kalaallisut" to "kl", "khasi" to "kha", "kinyarwanda" to "rw", "krio" to "kri",
            "luba‐lulua" to "lua", "luba-lulua" to "lua", "luo" to "luo", "luxembourgish" to "lb", "manx" to "gv",
            "morisyen" to "mfe", "māori" to "mi", "maori" to "mi", "newari" to "new", "northern sotho" to "nso",
            "nyanja" to "ny", "occitan" to "oc", "odia" to "or", "ossetic" to "os", "pampanga" to "pam", "rundi" to "rn",
            "sango" to "sg", "scottish gaelic" to "gd", "southern sotho" to "st", "swati" to "ss", "tigrinya" to "ti",
            "tsonga" to "ts", "tswana" to "tn", "tumbuka" to "tum", "uyghur" to "ug", "venda" to "ve", "waray" to "war",
            "western frisian" to "fy", "yiddish" to "yi",
        ).forEach { (name, code) -> map[name] = code }
        map
    }

    /**
     * The manifest names a caption's language in English ("english", or "french*" for an auto-translated
     * one) instead of giving its code, so the player can't name them. Swap each for the real code.
     */
    private fun withLanguageCodes(mpd: String): String =
        Regex("""lang="([^"]+)"""").replace(mpd) { m ->
            val raw = m.groupValues[1]
            val name = raw.trimEnd('*').trim().lowercase()
            val code = languageCodes[name]
            // A translated track is marked, so it stays apart from the video's own caption in that language.
            if (code != null) "lang=\"$code${if (raw.endsWith("*")) "-x-tr" else ""}\"" else m.value
        }

    private fun fetchTranslatedCaption(videoId: String, index: Int): Pair<ByteArray, String>? {
        val address = captionUrls[videoId]?.getOrNull(index) ?: return null
        val language = Regex("[?&]tlang=([^&]+)").find(address)?.groupValues?.get(1) ?: return null
        val key = "$videoId/$language"
        translatedCache[key]?.let { return it to typeOf(it) }
        val began = System.currentTimeMillis()
        // First what SmartTube does: YouTube's own translation, from the caption link it gave us.
        fetchYouTubeTranslation(address)?.let { own ->
            val bytes = own.toByteArray(Charsets.UTF_8)
            translatedCache[key] = bytes
            Log.i(TAG, "captions for $key from YouTube in ${System.currentTimeMillis() - began} ms")
            return bytes to typeOf(bytes)
        }
        // The video's own captions are served without any token. Fetch those and translate them.
        val original = address.replace(Regex("&tlang=[^&]+"), "")
        val source = get(original) ?: return null
        val translated = translateCaptions(source, language) ?: return null
        val bytes = translated.toByteArray(Charsets.UTF_8)
        if (!lastWasPartial) translatedCache[key] = bytes
        Log.i(TAG, "captions for $key ready in ${System.currentTimeMillis() - began} ms")
        return bytes to typeOf(bytes)
    }

    /** When YouTube refused its own translation, it isn't asked again for a while. */
    @Volatile private var ownTranslationPausedUntil = 0L

    /** YouTube's own auto-translated caption, as its caption link gives it with the translation language on the end. */
    private fun fetchYouTubeTranslation(address: String): String? {
        if (System.currentTimeMillis() < ownTranslationPausedUntil) return null
        val result = runCatching {
            http.newCall(
                Request.Builder().url(address)
                    .header("User-Agent", com.liskovsoft.googlecommon.common.helpers.DefaultHeaders.USER_AGENT_TV)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "YouTube's own translation -> HTTP ${response.code}")
                    null
                } else response.body?.string()
            }
        }.onFailure { Log.w(TAG, "YouTube's own translation failed", it) }.getOrNull()
        val usable = result?.takeIf { it.length > 40 && ("-->" in it || "<p " in it || "<text" in it) }
        if (usable == null) ownTranslationPausedUntil = System.currentTimeMillis() + 10 * 60_000L
        return usable
    }

    private fun typeOf(body: ByteArray) = if (String(body, 0, minOf(body.size, 8), Charsets.UTF_8).startsWith("WEBVTT")) "text/vtt" else "application/ttml+xml"

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use {
            if (it.isSuccessful) it.body?.string() else {
                Log.w(TAG, "caption request -> HTTP ${it.code}")
                null
            }
        }
    }.onFailure { Log.w(TAG, "caption request failed", it) }.getOrNull()

    private val cue = Regex("(<p\\b[^>]*>)(.*?)(</p>)", RegexOption.DOT_MATCHES_ALL)

    /** Translates the text of every cue in a caption file (WebVTT or TTML), keeping the timing. */
    private fun translateCaptions(file: String, language: String): String? =
        (if (file.trimStart().startsWith("WEBVTT")) translateVtt(file, language) else translateTtml(file, language))
            .also { if (it == null) Log.w(TAG, "couldn't translate the captions to $language") }

    private fun translateVtt(vtt: String, language: String): String? {
        val blocks = vtt.replace("\r\n", "\n").split(Regex("\n{2,}"))
        // Each cue is a block with a timing line; its text is whatever follows that line.
        val timingIndex = blocks.map { block -> block.lines().indexOfFirst { "-->" in it } }
        val texts = blocks.indices.mapNotNull { i ->
            val t = timingIndex[i]
            if (t < 0) null else plainText(blocks[i].lines().drop(t + 1).joinToString(" "))
        }
        if (texts.none { it.isNotBlank() }) return null
        val translated = translateAll(texts, language) ?: return null
        var n = 0
        val rebuilt = blocks.indices.map { i ->
            val t = timingIndex[i]
            if (t < 0) blocks[i]
            else (blocks[i].lines().take(t + 1) + translated[n++]).joinToString("\n")
        }.joinToString("\n\n")
        Log.i(TAG, "translated ${texts.size} caption cues to $language")
        return rebuilt + "\n"
    }

    private fun translateTtml(ttml: String, language: String): String? {
        val cues = cue.findAll(ttml).toList()
        if (cues.isEmpty()) return null
        val translated = translateAll(cues.map { plainText(it.groupValues[2]) }, language) ?: return null
        var n = 0
        val rebuilt = cue.replace(ttml) { m -> m.groupValues[1] + escape(translated[n++]) + m.groupValues[3] }
        Log.i(TAG, "translated ${cues.size} caption cues to $language")
        return rebuilt
    }

    /**
     * Translates every text. YouTube's automatic captions repeat each line as the words roll in, so only the
     * different lines are sent, and the requests go out side by side instead of one after another.
     */
    private fun translateAll(texts: List<String>, language: String): List<String>? {
        val unique = texts.filter { it.isNotBlank() }.distinct()
        val chunks = ArrayList<List<String>>()
        var start = 0
        while (start < unique.size) {
            var end = start
            var chars = 0
            while (end < unique.size && (chars == 0 || chars + unique[end].length < CHUNK_CHARS)) {
                chars += unique[end].length + 1
                end++
            }
            chunks += unique.subList(start, end)
            start = end
        }
        val began = System.currentTimeMillis()
        val workers = Executors.newFixedThreadPool(minOf(MAX_PARALLEL, maxOf(chunks.size, 1)))
        val translated = try {
            chunks.map { chunk -> workers.submit<List<String>?> { translateChunk(chunk, language) } }
                .map { it.get() }
        } finally {
            workers.shutdown()
        }
        // A chunk that couldn't be translated keeps its own words, so the captions still show.
        val failed = translated.count { it == null }
        if (failed == chunks.size) return null
        val byText = HashMap<String, String>()
        chunks.forEachIndexed { i, chunk -> chunk.forEachIndexed { j, text -> byText[text] = translated[i]?.get(j) ?: text } }
        Log.i(TAG, "translated ${unique.size} different lines (of ${texts.size}) in ${chunks.size} request(s), $failed not translated, ${System.currentTimeMillis() - began} ms")
        lastWasPartial = failed > 0
        return texts.map { if (it.isBlank()) it else byText[it].orEmpty() }
    }

    /** The last file translated had parts the translator wouldn't do; it isn't kept, so the next try starts again. */
    @Volatile private var lastWasPartial = false

    /**
     * Translates [lines] together. If the translator merges or splits lines, a small chunk is done a line at
     * a time; a big one is left alone rather than sent as hundreds of requests. Null when it can't be done now.
     */
    private fun translateChunk(lines: List<String>, language: String): List<String>? {
        val reply = translate(lines.joinToString(NEWLINE), language)
        if (reply !is Reply.Text) return null
        val joined = reply.text.split(NEWLINE)
        if (joined.size == lines.size) return joined
        Log.w(TAG, "lines didn't line up (${joined.size} vs ${lines.size})")
        if (lines.size > MAX_LINE_BY_LINE) return null
        return lines.map { line ->
            if (line.isBlank()) line else (translate(line, language) as? Reply.Text)?.text ?: return null
        }
    }

    private const val MAX_LINE_BY_LINE = 12
    private val NEWLINE = String(charArrayOf(10.toChar()))

    /** Asks the translator, and when it says slow down, waits and tries again a couple of times, then pauses everything. */
    private fun translate(text: String, language: String): Reply {
        var wait = 2_000L
        repeat(3) { attempt ->
            if (System.currentTimeMillis() < pausedUntil) return Reply.SlowDown
            when (val reply = translateOnce(text, language)) {
                is Reply.Text, Reply.Failed -> return reply
                Reply.SlowDown -> {
                    if (attempt == 2) {
                        pausedUntil = System.currentTimeMillis() + PAUSE_MS
                        Log.w(TAG, "translator is busy; pausing requests for ${PAUSE_MS / 1000} s")
                        return Reply.SlowDown
                    }
                    Thread.sleep(wait)
                    wait *= 2
                }
            }
        }
        return Reply.SlowDown
    }

    private fun translateOnce(text: String, language: String): Reply = runCatching {
        val target = when (language) {
            "zh-Hans" -> "zh-CN"
            "zh-Hant" -> "zh-TW"
            "iw" -> "he"
            else -> language
        }
        val body = FormBody.Builder().add("q", text).build()
        val request = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t")
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        translator.newCall(request).execute().use { response ->
            when {
                response.code == 429 || response.isRedirect -> {
                    Log.w(TAG, "translate -> HTTP ${response.code}")
                    Reply.SlowDown
                }
                !response.isSuccessful -> {
                    Log.w(TAG, "translate -> HTTP ${response.code}")
                    Reply.Failed
                }
                else -> {
                    val parts = JSONArray(response.body?.string().orEmpty()).getJSONArray(0)
                    Reply.Text(buildString { for (i in 0 until parts.length()) append(parts.getJSONArray(i).optString(0)) })
                }
            }
        }
    }.onFailure { Log.w(TAG, "translate failed", it) }.getOrDefault(Reply.Failed)

    private fun plainText(inner: String): String =
        inner.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace(Regex("\\s+"), " ").trim()

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ---- the server ----

    private fun ensureStarted(): ServerSocket {
        server?.takeIf { !it.isClosed }?.let { return it }
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(client) } }
            }
        }, "yt-mpd-server").apply { isDaemon = true }.start()
        return socket
    }

    private fun reply(out: java.io.OutputStream, status: String, type: String?, body: ByteArray?) {
        val head = StringBuilder("HTTP/1.1 $status\r\n")
        if (type != null) head.append("Content-Type: $type\r\n")
        head.append("Content-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (body != null) out.write(body)
        out.flush()
    }

    private fun handle(client: Socket) = client.use { c ->
        c.soTimeout = 15_000
        val request = c.getInputStream().bufferedReader().readLine().orEmpty()
        val out = c.getOutputStream()
        val caption = Regex("""GET /yt/cap/([^/ ]+)/(\d+)""").find(request)
        if (caption != null) {
            val result = fetchTranslatedCaption(caption.groupValues[1], caption.groupValues[2].toInt())
            if (result == null) reply(out, "404 Not Found", null, null) else reply(out, "200 OK", result.second, result.first)
            return@use
        }
        val id = Regex("""GET /yt/([^./ ]+)\.mpd""").find(request)?.groupValues?.get(1)
        val body = id?.let { manifests[it] }
        if (body == null) reply(out, "404 Not Found", null, null) else reply(out, "200 OK", "application/dash+xml", body)
    }
}
