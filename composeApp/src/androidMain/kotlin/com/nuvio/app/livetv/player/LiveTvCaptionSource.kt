package com.nuvio.app.livetv.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/**
 * Closed captions on live channels. TV channels usually carry them inside the video (CEA-608 and
 * 708) without saying so, so the player never offered them. This looks for them anyway, the way
 * TV apps do, and the player's Subtitles list then shows the ones that have text.
 */
@OptIn(UnstableApi::class)
object LiveTvCaptionSource {
    /** A source that looks for captions, for the Live TV channel being opened; null for anything else. */
    fun mediaSourceFor(url: String, item: MediaItem, dataSourceFactory: DataSource.Factory): MediaSource? {
        if (!LiveTvReconnect.isLive(url)) return null
        LiveTvReconnect.armedChannel?.let { LiveTvPauseBuffer.onLiveOpened(it) }
        if (LiveTvDrm.hasDrm(url)) return null
        val mime = item.localConfiguration?.mimeType
        val lower = url.lowercase().substringBefore('?')
        // Only streams that can carry them: HLS and plain TS (not an mp4 or a stream with its own format).
        val isHls = mime == MimeTypes.APPLICATION_M3U8 || lower.endsWith(".m3u8")
        val isTs = lower.endsWith(".ts") || (mime == null && !lower.contains('.') ) || mime == MimeTypes.VIDEO_MP2T
        if (!isHls && !isTs) return null
        LiveCaptions.reset()
        return runCatching {
            if (isHls) {
                HlsMediaSource.Factory(dataSourceFactory)
                    // Providers' playlists with small faults (a byte-order mark, a missing BANDWIDTH) play, as in other IPTV apps.
                    .setPlaylistParserFactory(LenientHlsPlaylistParserFactory())
                    .setExtractorFactory(
                        LiveCaptions.Hls(
                            DefaultHlsExtractorFactory(
                                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
                                /* exposeCea608WhenMissingDeclarations = */ true,
                            )
                        )
                    )
                    .createMediaSource(item)
            } else {
                val extractors = DefaultExtractorsFactory()
                    .setTsExtractorFlags(
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS
                    )
                    .setTsSubtitleFormats(LiveCaptions.formats)
                DefaultMediaSourceFactory(dataSourceFactory, LiveCaptions.Extractors(extractors)).createMediaSource(item)
            }
        }.getOrNull()
    }
}
