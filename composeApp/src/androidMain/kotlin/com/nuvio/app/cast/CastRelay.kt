package com.nuvio.app.cast

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lets a Cast device play a YouTube video. YouTube's picture and sound come as separate streams, described
 * in a manifest only this phone holds, and fetched with headers a Cast device can't send. While casting, this
 * little server hands the Cast device a copy of that manifest whose streams point back here, and fetches them
 * from YouTube on its behalf.
 *
 * It listens on the phone's Wi-Fi address only (not on every network), only while a video is being cast, and
 * only answers for a key made up for that video. It stops when casting ends.
 */
object CastRelay {
    private const val TAG = "NuvioCast"
    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private class Session(val key: String, val manifest: String, val upstream: List<String>, val userAgent: String)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val pool = Executors.newCachedThreadPool()
    private val http = OkHttpClient.Builder().followRedirects(true).retryOnConnectionFailure(true).build()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var host: String? = null

    /** The phone's address on the local network, or null when it isn't on one. */
    private fun lanAddress(): InetAddress? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedByDescending { it.name.startsWith("wlan") }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrNull()

    /**
     * Shares [manifest] for [videoId]; returns the address a Cast device opens it at, or null when there is
     * no local network to share it on.
     */
    @Synchronized
    fun share(videoId: String, manifest: ByteArray, userAgent: String?): String? {
        val address = lanAddress() ?: return null
        val socket = server?.takeIf { !it.isClosed && host == address.hostAddress } ?: run {
            server?.close()
            ServerSocket(0, 16, address).also { s ->
                server = s
                host = address.hostAddress
                Thread({
                    while (!s.isClosed) {
                        val client = runCatching { s.accept() }.getOrNull() ?: break
                        pool.execute { runCatching { handle(client) } }
                    }
                }, "cast-relay").apply { isDaemon = true }.start()
            }
        }
        val key = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val upstream = ArrayList<String>()
        val rewritten = castFriendly(manifest) { original ->
            upstream += original
            "http://${host}:${socket.localPort}/c/$key/s/${upstream.size - 1}"
        }
        sessions.clear()
        sessions[key] = Session(key, rewritten, upstream, userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_UA)
        Log.i(TAG, "Relay ready: ${upstream.size} streams on $host:${socket.localPort}")
        if (upstream.isEmpty()) return null
        return "http://${host}:${socket.localPort}/c/$key/m.mpd"
    }

    /**
     * YouTube's manifest lists every picture and sound format it has (a hundred and more streams, in many codecs
     * and languages). Cast devices all play H.264 picture and AAC sound, so the copy sent to them keeps just
     * those (the picture up to 1080p, and one language of sound), and leaves out the captions. When a video has
     * no H.264 or no AAC, its other formats stay in.
     */
    private fun castFriendly(manifest: ByteArray, rewrite: (String) -> String): String = runCatching {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(java.io.ByteArrayInputStream(manifest))
        val period = doc.getElementsByTagName("Period").item(0) ?: return@runCatching String(manifest, Charsets.UTF_8)
        fun kids(n: org.w3c.dom.Node, name: String) = (0 until n.childNodes.length).map { n.childNodes.item(it) }.filter { it.nodeName == name }
        fun attr(n: org.w3c.dom.Node, a: String) = (n as? org.w3c.dom.Element)?.getAttribute(a).orEmpty()
        fun mime(set: org.w3c.dom.Node, rep: org.w3c.dom.Node) = attr(rep, "mimeType").ifBlank { attr(set, "mimeType") }
        fun codecs(set: org.w3c.dom.Node, rep: org.w3c.dom.Node) = attr(rep, "codecs").ifBlank { attr(set, "codecs") }
        val sets = kids(period, "AdaptationSet")
        val videoSets = sets.filter { s -> kids(s, "Representation").any { mime(s, it).startsWith("video") } }
        val audioSets = sets.filter { s -> kids(s, "Representation").any { mime(s, it).startsWith("audio") } }
        // Anything that isn't picture or sound (captions) goes.
        (sets - videoSets.toSet() - audioSets.toSet()).forEach { period.removeChild(it) }
        // Picture: H.264 up to 1080p.
        val avc = videoSets.flatMap { s -> kids(s, "Representation").map { s to it } }
            .filter { (s, r) -> codecs(s, r).startsWith("avc1") && (attr(r, "height").toIntOrNull() ?: 0) in 1..1080 }
        if (avc.isNotEmpty()) {
            val keep = avc.map { it.second }.toSet()
            videoSets.forEach { s -> kids(s, "Representation").filter { it !in keep }.forEach { s.removeChild(it) } }
        }
        videoSets.filter { kids(it, "Representation").isEmpty() }.forEach { period.removeChild(it) }
        // Sound: AAC, and one language (the first set, which YouTube lists as the original).
        val aac = audioSets.flatMap { s -> kids(s, "Representation").map { s to it } }.filter { (s, r) -> codecs(s, r).startsWith("mp4a") }
        // Which language: YouTube tags each sound track "original", "dubbed", "dubbed-auto" or "descriptive" in its language.
        val candidates = (aac.map { it.first }.distinct().ifEmpty { audioSets })
        fun isMain(set: org.w3c.dom.Node) = kids(set, "Role").any { attr(it, "value") == "main" }
        Log.i(TAG, "Sound tracks in the manifest: " + audioSets.joinToString { "'${attr(it, "lang")}'${if (isMain(it)) "*" else ""}" })
        val chosenSet = candidates.firstOrNull { isMain(it) } ?: candidates.firstOrNull()
        Log.i(TAG, "Sound track chosen: '${chosenSet?.let { attr(it, "lang") }}'")
        audioSets.filter { it != chosenSet }.forEach { period.removeChild(it) }
        if (chosenSet != null && aac.isNotEmpty()) {
            val keep = aac.filter { it.first == chosenSet }.map { it.second }.toSet()
            kids(chosenSet, "Representation").filter { it !in keep }.forEach { chosenSet.removeChild(it) }
        }
        // Every stream's address now points at this phone (read from the tree, so nothing depends on how it's written out).
        val bases = doc.getElementsByTagName("BaseURL")
        for (i in 0 until bases.length) {
            val node = bases.item(i)
            val address = node.textContent.trim()
            if (address.startsWith("http")) node.textContent = rewrite(address)
        }
        val out = java.io.StringWriter()
        javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
        Log.i(TAG, "Cast manifest: video=${kids(period, "AdaptationSet").sumOf { s -> kids(s, "Representation").count { mime(s, it).startsWith("video") } }} " +
            "audio=${kids(period, "AdaptationSet").sumOf { s -> kids(s, "Representation").count { mime(s, it).startsWith("audio") } }} (from ${sets.sumOf { kids(it, "Representation").size }})")
        out.toString()
    }.getOrElse {
        Log.w(TAG, "Couldn't simplify the manifest: ${it.message}")
        Regex("<BaseURL>(.*?)</BaseURL>").replace(String(manifest, Charsets.UTF_8)) { m -> "<BaseURL>" + rewrite(m.groupValues[1].replace("&amp;", "&")) + "</BaseURL>" }
    }

    /** Casting ended: close the door. */
    @Synchronized
    fun stop() {
        sessions.clear()
        runCatching { server?.close() }
        server = null
    }

    private fun cors(h: StringBuilder) {
        h.append("Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        h.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
    }

    private fun simple(out: java.io.OutputStream, status: String, type: String? = null, body: ByteArray? = null) {
        val h = StringBuilder("HTTP/1.1 $status\r\n")
        cors(h)
        if (type != null) h.append("Content-Type: $type\r\n")
        h.append("Content-Length: ${body?.size ?: 0}\r\nConnection: close\r\n\r\n")
        out.write(h.toString().toByteArray())
        if (body != null) out.write(body)
        out.flush()
    }

    private fun handle(client: Socket) = client.use { c ->
        val input = c.getInputStream().bufferedReader()
        val request = input.readLine().orEmpty()
        val headers = HashMap<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val out = c.getOutputStream()
        if (request.startsWith("OPTIONS")) return@use simple(out, "204 No Content")
        val m = Regex("""^(GET|HEAD) /c/([0-9a-f]+)/(m\.mpd|s/(\d+)) """).find(request) ?: return@use simple(out, "404 Not Found")
        val session = sessions[m.groupValues[2]] ?: return@use simple(out, "404 Not Found")
        val head = m.groupValues[1] == "HEAD"
        if (m.groupValues[3] == "m.mpd") {
            Log.i(TAG, "Relay: the Cast device asked for the manifest")
            val body = session.manifest.toByteArray()
            return@use simple(out, "200 OK", "application/dash+xml", if (head) null else body)
        }
        val index = m.groupValues[4].toInt()
        val url = session.upstream.getOrNull(index) ?: return@use simple(out, "404 Not Found")
        val req = Request.Builder().url(url).header("User-Agent", session.userAgent).apply {
            headers["range"]?.let { header("Range", it) }
            if (head) head()
        }.build()
        http.newCall(req).execute().use { r ->
            if (index < 6 || !r.isSuccessful) Log.i(TAG, "Relay: stream $index range=${headers["range"]} -> YouTube ${r.code} ${r.header("Content-Type")} ${r.header("Content-Length")}")
            val h = StringBuilder("HTTP/1.1 ${r.code} ${r.message.ifBlank { "OK" }}\r\n")
            cors(h)
            for (name in listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges")) {
                r.header(name)?.let { h.append("$name: $it\r\n") }
            }
            h.append("Connection: close\r\n\r\n")
            out.write(h.toString().toByteArray())
            if (!head) r.body?.byteStream()?.use { it.copyTo(out, 32 * 1024) }
            out.flush()
        }
    }
}
