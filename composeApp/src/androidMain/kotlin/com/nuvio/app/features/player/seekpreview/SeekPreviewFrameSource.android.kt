package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.graphics.ImageBitmap

// The picture-preview frames in the Enhanced fork come from a native (Rust) frame grabber that this build
// doesn't include, so on Android the seek bar shows the time only.
internal actual fun openSeekPreviewFrameSource(
    url: String,
    headers: Map<String, String>,
): SeekPreviewFrameSource? = null

internal actual fun openSeekPreviewFeed(
    url: String,
    headers: Map<String, String>,
    onThumbnail: (positionMs: Long, bitmap: ImageBitmap?, error: String?) -> Unit,
): SeekPreviewFeed? = null
