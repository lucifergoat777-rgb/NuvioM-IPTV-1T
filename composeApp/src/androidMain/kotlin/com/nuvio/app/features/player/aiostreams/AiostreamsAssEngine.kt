package com.nuvio.app.features.player.aiostreams

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.PlayerView
import com.nuvio.app.features.player.SubtitleStyleState

// The Enhanced fork draws ASS subtitles with a native (Rust) engine. This build doesn't include it, so the
// engine below is a placeholder that PlayerEngine.android.kt never creates: ASS comes from libass or libmpv.
internal object AiostreamsAss {
    val isAvailable: Boolean = false
}

@UnstableApi
internal class AiostreamsAssEngine {
    fun reset(fontSource: DataSource.Factory) = Unit
    fun extractors(base: ExtractorsFactory): ExtractorsFactory = base
    fun scripts(): SubtitleParser.Factory = throw UnsupportedOperationException()
    fun bind(player: ExoPlayer) = Unit
    fun unbind(player: ExoPlayer) = Unit
    fun attach(view: PlayerView) = Unit
    fun setStyle(style: SubtitleStyleState) = Unit
    fun setSubtitleDelayMs(delayMs: Int) = Unit
    fun showExternal(url: String, headers: Map<String, String>): Boolean = false
    fun clearExternal() = Unit
    fun release() = Unit
}
