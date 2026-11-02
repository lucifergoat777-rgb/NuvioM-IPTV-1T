package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.data.ChannelNameCleaner
import com.nuvio.app.livetv.data.LiveTvRepository
import com.nuvio.app.livetv.data.LogoChoice
import com.nuvio.app.livetv.data.LogoLibrary
import com.nuvio.app.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Logos for [channel], closest first: the same channel in your other playlists, then the two free
 * logo libraries (their lists are fetched the first time and kept for a week). [query] is what was
 * typed in the search box, instead of the channel's own name.
 */
private suspend fun logoChoices(live: LiveTv, context: android.content.Context, channel: LiveChannel, query: String): List<LogoChoice> =
    withContext(Dispatchers.Default) {
        val clean: (String) -> String = { ChannelNameCleaner.clean(it) }
        val norm: (String) -> String = { LiveTvRepository.normalize(clean(it)) }
        val targets = listOfNotNull(
            norm(channel.name),
            LiveTvRepository.normalize(channel.name),
            channel.tvgName?.let { norm(it) },
            channel.tvgId?.let { LiveTvRepository.idName(it) },
        ).filter { it.length >= 2 }.distinct()
        val words = clean(query).lowercase().split(Regex("\\s+")).map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        fun score(key: String): Int {
            if (key.isEmpty()) return 0
            if (words.isNotEmpty()) return if (words.all { key.contains(it) }) 50 + (if (key in targets) 50 else 0) - (key.length / 8) else 0
            var best = 0
            for (t in targets) {
                val sc = when {
                    key == t -> 100
                    (key.startsWith(t) || t.startsWith(key)) && minOf(key.length, t.length) >= 3 -> 70 - kotlin.math.abs(key.length - t.length).coerceAtMost(30)
                    t.length >= 4 && key.contains(t) -> 35
                    else -> 0
                }
                if (sc > best) best = sc
            }
            return best
        }
        val out = ArrayList<LogoChoice>()
        val seen = HashSet<String>()
        fun add(url: String?, name: String, from: String, group: Int, sc: Int) {
            val u = url?.trim()
            if (u.isNullOrEmpty() || sc <= 0 || !seen.add(u)) return
            out += LogoChoice(u, name, from, group, sc)
        }
        live.store.displayChannels.value.asSequence()
            .filter { it.key != channel.key && !it.logo.isNullOrBlank() }
            .map { it to maxOf(score(norm(it.name)), it.tvgName?.let { n -> score(norm(n)) } ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(40)
            .forEach { (c, sc) -> add(c.logo, c.name, c.sourceName, LogoChoice.PLAYLISTS, sc) }
        LogoLibrary.entries(context).asSequence()
            .map { it to score(it.key) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(60)
            .forEach { (e, sc) -> add(e.url, e.label, e.from, LogoChoice.LIBRARY, sc) }
        // Title logos from TMDB (a show's or film's name on a clear background), for 24/7 channels: "24/7 Friends" -> Friends.
        val title = clean(query.ifBlank { channel.name }).replace(Regex("""^24\s*/\s*7\s*:?\s*"""), "").trim()
        if (title.length >= 2) runCatching { com.nuvio.app.livetv.data.TmdbLogos.find(title) }.getOrDefault(emptyList())
            .forEach { c -> add(c.url, c.name, c.from, LogoChoice.TMDB, c.score) }
        out.sortedWith(compareBy<LogoChoice> { it.group }.thenByDescending { it.score })
    }

/** "Change logo": pick a different logo for a channel from your other playlists and two free logo libraries. */
@Composable
internal fun ChangeLogoDialog(channel: LiveChannel, hasPicked: Boolean, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    var query by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<List<LogoChoice>?>(null) }
    LaunchedEffect(query) {
        choices = null
        if (query.isNotEmpty()) delay(400)
        choices = runCatching { logoChoices(live, context, channel, query) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logo for ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search logos") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                val list = choices
                when {
                    list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Text(
                        "No logos found. Try other words in the box above.",
                        style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(88.dp),
                        modifier = Modifier.height(320.dp),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.url }) { c ->
                            Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(c.url) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(88.dp, 60.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)), contentAlignment = Alignment.Center) {
                                    AsyncImage(c.url, null, Modifier.fillMaxWidth().padding(6.dp), contentScale = ContentScale.Fit)
                                }
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                Text(c.from, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { if (hasPicked) TextButton(onClick = { onPick(null) }) { Text("Use its own") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
