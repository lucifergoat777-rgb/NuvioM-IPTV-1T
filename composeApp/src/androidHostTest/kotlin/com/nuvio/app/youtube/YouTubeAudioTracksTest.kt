package com.nuvio.app.youtube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeAudioTracksTest {
    private fun set(id: Int, lang: String) =
        "<AdaptationSet id=\"$id\" mimeType=\"audio/mp4\" lang=\"$lang\"><Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>" +
            "<Representation id=\"140\" codecs=\"mp4a.40.2\" bandwidth=\"128000\"><BaseURL>http://x/$id</BaseURL></Representation></AdaptationSet>"

    private fun mpd(vararg langs: String) =
        ("<?xml version=\"1.0\"?><MPD><Period><AdaptationSet id=\"0\" mimeType=\"video/mp4\"><Representation id=\"137\" codecs=\"avc1\" height=\"1080\"><BaseURL>http://v</BaseURL></Representation></AdaptationSet>" +
            langs.withIndex().joinToString("") { set(it.index + 1, it.value) } + "</Period></MPD>").toByteArray()

    @Test
    fun `reads YouTube's track names`() {
        assertEquals("en-US", YouTubeAudioTracks.parse("en-US (original)").code)
        assertEquals(YouTubeAudioTracks.Kind.ORIGINAL, YouTubeAudioTracks.parse("en-US (original)").kind)
        assertEquals(YouTubeAudioTracks.Kind.AUTO, YouTubeAudioTracks.parse("es-US (dubbed-auto)").kind)
        assertEquals(YouTubeAudioTracks.Kind.DUBBED, YouTubeAudioTracks.parse("de-DE.10").kind)
        assertEquals(YouTubeAudioTracks.Kind.ORIGINAL, YouTubeAudioTracks.parse("ja.4").kind)
        assertEquals("en", YouTubeAudioTracks.parse("en").code)
    }

    @Test
    fun `the original goes first and is the default, with proper names`() {
        val out = String(YouTubeAudioTracks.tidy(mpd("es-US (dubbed-auto)", "fr (dubbed)", "ja (original)")))
        val firstAudio = out.indexOf("audio/mp4")
        assertTrue(out.indexOf("lang=\"ja\"") in 0..(firstAudio + 120), out)
        assertTrue("Japanese \u00B7 original" in out, out)
        assertTrue("Spanish (United States) \u00B7 auto-dubbed" in out, out)
        // Only the original is "main".
        assertEquals(1, Regex("value=\"main\"").findAll(out).count())
    }

    @Test
    fun `without an original, English comes first`() {
        val out = String(YouTubeAudioTracks.tidy(mpd("es-US (dubbed-auto)", "en-US (dubbed)")))
        assertTrue(out.indexOf("lang=\"en-US\"") < out.indexOf("lang=\"es-US\""), out)
    }

    @Test
    fun `a manifest with no sound is left alone`() {
        val bare = "<MPD><Period><AdaptationSet mimeType=\"video/mp4\"/></Period></MPD>".toByteArray()
        assertEquals(String(bare), String(YouTubeAudioTracks.tidy(bare)))
    }
}
