package com.nuvio.app.livetv.data

import android.app.Application
import com.nuvio.app.livetv.model.PlaylistSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StalkerPortalTest {
    @Test
    fun `a MAC address is tidied up and checked`() {
        assertEquals("00:1A:79:AB:CD:EF", StalkerClient.normalizeMac("00-1a-79-ab-cd-ef"))
        assertEquals("00:1A:79:AB:CD:EF", StalkerClient.normalizeMac(" 001a79abcdef "))
        assertTrue(StalkerClient.isValidMac("00:1a:79:ab:cd:ef"))
        assertFalse(StalkerClient.isValidMac("00:1A:79"))
        assertFalse(StalkerClient.isValidMac("hello"))
    }

    @Test
    fun `portal addresses give the places to try`() {
        assertEquals(
            listOf(
                "http://host:8080/portal.php",
                "http://host:8080/server/load.php",
                "http://host:8080/stalker_portal/server/load.php",
            ),
            StalkerClient.endpointCandidates("http://host:8080/c/"),
        )
        assertEquals(
            listOf("http://host/stalker_portal/portal.php", "http://host/stalker_portal/server/load.php"),
            StalkerClient.endpointCandidates("host/stalker_portal/c"),
        )
        // The address of the API itself is tried first.
        assertEquals("http://host/portal.php", StalkerClient.endpointCandidates("http://host/portal.php").first())
        assertEquals("https://secure.example", StalkerClient.splitPortal("https://secure.example/c/").first)
    }

    @Test
    fun `stream links lose the player name in front`() {
        assertEquals("http://x.tv/live/1.ts?token=a", StalkerClient.cleanLink("ffmpeg http://x.tv/live/1.ts?token=a"))
        assertEquals("http://x.tv/s", StalkerClient.cleanLink("auto http://x.tv/s"))
        assertEquals("http://x.tv/s", StalkerClient.cleanLink("http://x.tv/s"))
        assertEquals("", StalkerClient.cleanLink("nothing"))
        assertEquals("hls", StalkerClient.formatOf("http://x/play/1.m3u8?t=1"))
        assertEquals("ts", StalkerClient.formatOf("http://x/play/live.php?stream=1&extension=ts"))
    }

    @Test
    fun `channels are read from the portal's answer`() {
        val js = JSONObject().put("data", JSONArray()
            .put(JSONObject().put("id", "11").put("name", "News One").put("number", "5").put("cmd", "ffrt http://localhost/ch/11")
                .put("logo", "11.png").put("tv_genre_id", "3").put("xmltv_id", "news.one").put("tv_archive", "1").put("tv_archive_duration", "3"))
            .put(JSONObject().put("id", "12").put("name", "Films").put("cmd", "ffrt http://localhost/ch/12").put("logo", "http://cdn/f.png"))
            .put(JSONObject().put("id", "").put("cmd", "x")))
        val list = StalkerClient.parseChannels(js, "http://host/misc/logos/320/")
        assertEquals(2, list.size)
        assertEquals("http://host/misc/logos/320/11.png", list[0].logo)
        assertEquals("news.one", list[0].epgId)
        assertEquals(3, list[0].archiveDays)
        assertEquals("http://cdn/f.png", list[1].logo)
        assertEquals(2, list[1].number)
        assertEquals("stalker.12", StalkerClient.tvgId(list[1]))
    }

    @Test
    fun `saved channel links keep the portal's command`() {
        val ch = StalkerChannel("7", "Seven", 7, "ffrt http://localhost/ch/7?a=b&c=d", null, "1", null, 0)
        val url = StalkerClient.channelUrl("src-1", ch, "hls")
        assertTrue(StalkerLinks.isStalker(url))
        assertEquals("src-1" to "ffrt http://localhost/ch/7?a=b&c=d", StalkerLinks.parse(url))
        assertEquals("application/x-mpegURL", StalkerLinks.mimeHint(url))
        assertNull(StalkerLinks.mimeHint(StalkerClient.channelUrl("s", ch, "auto")))
    }

    @Test
    fun `the guide becomes an XMLTV file`() {
        val ch = StalkerChannel("7", "Seven & Co", 7, "cmd", null, "1", null, 0)
        val guide = StalkerClient.parseGuide(JSONObject().put("data", JSONObject().put("7", JSONArray()
            .put(JSONObject().put("name", "Evening <News>").put("descr", "Top stories").put("start_timestamp", "1700000000").put("stop_timestamp", "1700003600")))))
        assertEquals(1, guide["7"]?.size)
        val xml = StalkerClient.xmltv(listOf(StalkerClient.tvgId(ch) to ch), guide)
        assertTrue(xml.contains("<channel id=\"stalker.7\">"), xml)
        assertTrue(xml.contains("Seven &amp; Co"), xml)
        assertTrue(xml.contains("start=\"20231114221320 +0000\""), xml)
        assertTrue(xml.contains("Evening &lt;News&gt;"), xml)
    }

    /** A pretend portal: the way a MAG box talks to a real one. */
    private class FakePortal(var staleTokens: Boolean = false) : Dispatcher() {
        val seen = CopyOnWriteArrayList<String>()
        var tokenCounter = 0
        var goodToken = ""

        private fun js(body: Any) = MockResponse().setBody(JSONObject().put("js", body).toString())

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!
            val type = path.queryParameter("type")
            val action = path.queryParameter("action")
            seen += "${path.encodedPath} $type/$action cookie=${request.getHeader("Cookie")} auth=${request.getHeader("Authorization")}"
            if (path.encodedPath != "/portal.php") return MockResponse().setResponseCode(404)
            if (request.getHeader("Cookie")?.contains("mac=00:1A:79:AB:CD:EF") != true) return MockResponse().setResponseCode(401)
            return when ("$type/$action") {
                "stb/handshake" -> { goodToken = "tok${++tokenCounter}"; js(JSONObject().put("token", goodToken)) }
                "stb/get_profile" -> js(JSONObject().put("id", "1"))
                "itv/get_genres" -> js(JSONArray().put(JSONObject().put("id", "3").put("title", "News")))
                "itv/get_all_channels" -> js(JSONObject().put("data", JSONArray()
                    .put(JSONObject().put("id", "11").put("name", "News One").put("number", "1").put("cmd", "ffrt http://localhost/ch/11").put("tv_genre_id", "3"))))
                "itv/create_link" -> {
                    val bearer = request.getHeader("Authorization")?.removePrefix("Bearer ")
                    if (bearer != goodToken || staleTokens) js(JSONObject().put("cmd", ""))
                    else js(JSONObject().put("cmd", "ffmpeg http://stream.example/play/11.ts?token=$bearer"))
                }
                "itv/get_epg_info" -> js(JSONObject().put("data", JSONObject()))
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @Test
    fun `signs in like a set-top box, lists channels and asks for a link`() {
        val portal = FakePortal()
        MockWebServer().use { server ->
            server.dispatcher = portal
            server.start()
            val client = StalkerClient(server.url("/c/").toString(), "00-1a-79-ab-cd-ef", OkHttpClient())
            val genres = client.genres()
            assertEquals("News", genres["3"])
            val channels = client.channels()
            assertEquals(1, channels.size)
            assertEquals("ffrt http://localhost/ch/11", channels[0].cmd)
            val link = client.createLink(channels[0].cmd)
            assertEquals("http://stream.example/play/11.ts?token=tok1", link)
            // The first address tried that isn't the portal's (the API is at /portal.php) didn't stop it,
            // and later requests carry the token.
            assertTrue(portal.seen.any { it.contains("itv/get_all_channels") && it.contains("auth=Bearer tok1") }, portal.seen.joinToString("\n"))
        }
    }

    @Test
    fun `a token that has run out is replaced and the request repeated`() {
        val portal = FakePortal()
        MockWebServer().use { server ->
            server.dispatcher = portal
            server.start()
            val client = StalkerClient(server.url("/c/").toString(), "00:1A:79:AB:CD:EF", OkHttpClient())
            client.login()
            // The portal forgets the token (it expired): the next link request gets "nothing", so it signs in again.
            portal.goodToken = "forgotten"
            val link = client.createLink("ffrt http://localhost/ch/11")
            assertEquals("http://stream.example/play/11.ts?token=tok2", link)
        }
    }

    @Test
    fun `a MAC the portal doesn't know is refused with a clear message`() {
        MockWebServer().use { server ->
            server.dispatcher = FakePortal()
            server.start()
            val client = StalkerClient(server.url("/c/").toString(), "11:22:33:44:55:66", OkHttpClient())
            val error = runCatching { client.channels() }.exceptionOrNull()
            assertTrue(error is StalkerException, error.toString())
            assertTrue(error!!.message!!.contains("MAC"), error.message)
        }
    }

    @Test
    fun `a saved channel link turns into the portal's stream link when it plays`() {
        val portal = FakePortal()
        MockWebServer().use { server ->
            server.dispatcher = portal
            server.start()
            StalkerLinks.useHttp(OkHttpClient())
            StalkerLinks.configure(listOf(PlaylistSource(id = "p1", name = "P", url = "", stalkerPortal = server.url("/c/").toString(), stalkerMac = "00:1A:79:AB:CD:EF")))
            val ch = StalkerChannel("11", "News One", 1, "ffrt http://localhost/ch/11", null, "3", null, 0)
            val saved = StalkerClient.channelUrl("p1", ch, "ts")
            assertEquals("http://stream.example/play/11.ts?token=tok1", StalkerLinks.resolve(saved))
            // Asked again straight away, it reuses the link instead of asking the portal again.
            val before = portal.seen.size
            StalkerLinks.resolve(saved)
            assertEquals(before, portal.seen.size)
            // Ordinary links pass through untouched.
            assertEquals("http://a/b.m3u8", StalkerLinks.resolve("http://a/b.m3u8"))
            StalkerLinks.configure(emptyList())
        }
    }
}
