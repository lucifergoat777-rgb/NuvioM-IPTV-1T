package com.nuvio.app.livetv.player

import android.view.View
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/** How to draw a channel whose stream carries a wrong pixel-shape flag (some 24/7 channels re-encode movies this way). */
enum class PictureShape(val label: String) {
    /** Trust the flag, except on full-HD and HD widths, where it's almost always a mistake. */
    AUTO("Auto"),
    /** Always draw pixels square: 1920x800 shows as 2.40:1. */
    REAL("Use real size"),
    /** Always follow the stream's flag, as before. */
    STREAM("Use stream's shape");

    companion object {
        fun of(name: String?): PictureShape = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * Streams carry a "pixel shape" flag that says how wide each pixel should be drawn. Real SD
 * broadcasts (720x576) need it, but some 24/7 channels re-encode movies to 1920x800 and leave a
 * wrong flag in, so the movie comes out squashed instead of wide with black bars.
 */
object LivePictureShape {
    private val SQUARE_WIDTHS = setOf(1280, 1920, 2560, 3840)

    /** Channel key -> shape name, kept current from the stored choices. */
    @Volatile var shapes: Map<String, String> = emptyMap()

    private fun hasFlag(par: Float) = par > 0f && abs(par - 1f) > 0.01f

    private fun ignoresFlag(width: Int, par: Float, shape: PictureShape) = hasFlag(par) && when (shape) {
        PictureShape.REAL -> true
        PictureShape.STREAM -> false
        PictureShape.AUTO -> width in SQUARE_WIDTHS
    }

    /** The shape to draw (width / height), or null before the size is known. */
    fun aspect(size: VideoSize, shape: PictureShape): Float? {
        if (size.width <= 0 || size.height <= 0) return null
        val par = size.pixelWidthHeightRatio
        val p = if (ignoresFlag(size.width, par, shape) || par <= 0f) 1f else par
        return size.width * p / size.height
    }

    /** Sizes the picture in [view] to the worked-out shape for the Live TV channel being watched. */
    fun apply(view: PlayerView, size: VideoSize, channelKey: String?) {
        val shape = PictureShape.of(channelKey?.let { shapes[it] })
        val aspect = aspect(size, shape) ?: return
        val frame = view.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame) as? AspectRatioFrameLayout ?: return
        frame.setAspectRatio(aspect)
    }
}
