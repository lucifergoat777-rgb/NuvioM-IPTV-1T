package com.nuvio.app.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val VISIBLE_LINES = 12

/**
 * The live chat of a live stream, drawn over the right side of the picture as messages arrive, newest at the
 * bottom. Read-only, and it ignores touches, so the player underneath keeps working.
 */
@Composable
fun YouTubeChatOverlay(videoId: String) {
    val context = LocalContext.current.applicationContext
    val vm: YouTubePlayerViewModel = viewModel(key = "yt-player-$videoId") { YouTubePlayerViewModel(context) }
    val lines by vm.chat.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 150.dp, end = 16.dp), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x99000000))
                .padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
        ) {
            if (lines.isEmpty()) {
                Text("Waiting for chat…", color = Color(0xFFBBBBBB), fontSize = 12.sp)
            }
            lines.takeLast(VISIBLE_LINES).forEach { line ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF9BB8FF), fontWeight = FontWeight.SemiBold)) { append(line.author) }
                        append("  ")
                        withStyle(SpanStyle(color = Color.White)) { append(line.message) }
                    },
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
