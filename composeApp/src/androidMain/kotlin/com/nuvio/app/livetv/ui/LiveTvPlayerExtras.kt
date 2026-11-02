package com.nuvio.app.livetv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.LiveTvHost
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.LiveTvSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The sleep timer: when it runs out the player closes. */
object LiveTvSleep {
    var asking by mutableStateOf(false)
    var endsAt by mutableLongStateOf(0L)

    fun ask() { asking = true }
    fun cancel() { endsAt = 0L }
}

/** The start page and "open the last channel", which are read before Live TV itself is up. */
object LiveTvStart {
    private const val FILE = "livetv_start"
    @Volatile var autoPlayedThisRun = false

    fun startOnLiveTv(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("page", "HOME") == "LIVE_TV"

    fun setStartPage(context: Context, page: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("page", page).apply()
    }

    fun startPage(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("page", "HOME") ?: "HOME"
}

/** What sits over the Live TV picture: the clock, and the sleep timer's question and countdown. */
@Composable
fun LiveTvPlayerExtras(channelKey: String) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val settings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = LiveTvSettings())
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            // The sleep timer ending closes the player.
            if (LiveTvSleep.endsAt in 1..now) {
                LiveTvSleep.endsAt = 0L
                LiveTvHost.closePlayer?.invoke()
            }
            delay(1_000)
        }
    }
    if (settings.clockMode == "always") {
        val fmt = remember(settings.use24HourClock) { SimpleDateFormat(if (settings.use24HourClock) "HH:mm" else "h:mm a", Locale.getDefault()) }
        val size = when (settings.clockSize) { "small" -> 14.sp; "large" -> 26.sp; else -> 18.sp }
        val align = when (settings.clockPosition) {
            "top_left" -> Alignment.TopStart
            "bottom_left" -> Alignment.BottomStart
            "bottom_right" -> Alignment.BottomEnd
            else -> Alignment.TopEnd
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), contentAlignment = align) {
            Text(
                fmt.format(Date(now)),
                color = Color.White.copy(alpha = settings.clockOpacity.coerceIn(10, 100) / 100f),
                fontSize = size,
                modifier = Modifier
                    .then(if (settings.clockBackground) Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    if (LiveTvSleep.endsAt > now) {
        val left = ((LiveTvSleep.endsAt - now) / 60_000L).toInt() + 1
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), contentAlignment = Alignment.TopStart) {
            Text(
                "Sleep in $left min",
                color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    if (LiveTvSleep.asking) {
        AlertDialog(
            onDismissRequest = { LiveTvSleep.asking = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                        Text(
                            "Stop after $m minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { LiveTvSleep.endsAt = System.currentTimeMillis() + m * 60_000L; LiveTvSleep.asking = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                    if (LiveTvSleep.endsAt > 0L) {
                        Text(
                            "Turn the timer off",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { LiveTvSleep.cancel(); LiveTvSleep.asking = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { LiveTvSleep.asking = false }) { Text("Cancel") } },
        )
    }
}
