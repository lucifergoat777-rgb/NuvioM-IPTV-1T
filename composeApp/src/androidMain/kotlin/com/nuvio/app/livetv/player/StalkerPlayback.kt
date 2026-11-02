package com.nuvio.app.livetv.player

import androidx.core.net.toUri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import com.nuvio.app.livetv.data.StalkerLinks

/**
 * Stalker portal channels are saved as stalker:// links. Their real stream links are temporary, so one is asked
 * for each time the player opens the channel (including when it reconnects after a drop), here, on the
 * player's loading thread.
 */
object StalkerPlayback {
    fun wrap(upstream: DataSource.Factory): DataSource.Factory =
        ResolvingDataSource.Factory(upstream) { spec ->
            val link = spec.uri.toString()
            if (StalkerLinks.isStalker(link)) spec.withUri(StalkerLinks.resolve(link).toUri()) else spec
        }
}
