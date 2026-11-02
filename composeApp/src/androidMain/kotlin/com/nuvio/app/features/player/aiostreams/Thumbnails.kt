package com.nuvio.app.features.player.aiostreams

import android.content.Context

// The Enhanced fork's native (Rust) thumbnail grabber is not part of this build. These placeholders keep the
// player-based frame source compiling; nothing creates a native thumbnail session.
internal object Thumbnails {
    fun interface Listener {
        fun onThumbnail(positionMs: Long, jpeg: ByteArray?, error: String?)
    }

    fun open(url: String, headers: Array<String>, caPem: ByteArray?, listener: Listener): Long = 0L
    fun want(handle: Long, positionMs: Long, showingMs: Long) = Unit
    fun prepare(handle: Long) = Unit
    fun close(handle: Long) = Unit
    fun trustedRoots(context: Context): ByteArray? = null
}
