package com.nuvio.app.livetv.player

/** The playlist whose channel is being watched, so a recording can say when watching may have cut it off. */
object LiveWatching {
    @Volatile var sourceId: String? = null
}
