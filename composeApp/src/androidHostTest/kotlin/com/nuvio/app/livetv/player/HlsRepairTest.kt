package com.nuvio.app.livetv.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsRepairTest {
    @Test
    fun `drops a byte-order mark and blank lines before the header`() {
        val fixed = HlsRepair.fix("\uFEFF\n\n#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4,\nseg.ts\n".toByteArray())
        assertTrue(fixed!!.startsWith("#EXTM3U"))
    }

    @Test
    fun `adds the BANDWIDTH a stream list line is missing`() {
        val fixed = HlsRepair.fix("#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\nhigh.m3u8\n".toByteArray())!!
        assertTrue(fixed.contains("#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1280x720"))
    }

    @Test
    fun `leaves a stream list line that has BANDWIDTH alone`() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nlow.m3u8\n"
        assertEquals(text.trimEnd(), HlsRepair.fix(text.toByteArray())!!.trimEnd())
    }

    @Test
    fun `gives a header to a playlist without one`() {
        val fixed = HlsRepair.fix("#EXT-X-VERSION:3\n#EXTINF:4,\nseg.ts\n".toByteArray())!!
        assertTrue(fixed.startsWith("#EXTM3U"))
    }

    @Test
    fun `a transport stream is not a playlist`() {
        val ts = ByteArray(400).also { it[0] = 0x47; it[188] = 0x47 }
        assertNull(HlsRepair.fix(ts))
    }
}
