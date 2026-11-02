package com.nuvio.app.features.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.updater.AppUpdaterPlatform
import com.nuvio.app.features.updater.GITHUB_API_BASE
import com.nuvio.app.features.updater.GITHUB_OWNER
import com.nuvio.app.features.updater.GITHUB_REPO
import com.nuvio.app.features.updater.GitHubReleaseDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private val popupJson = Json { ignoreUnknownKeys = true; isLenient = true }

private class WhatsNewPopupContent(val version: String, val notes: String)

/** The release notes for [version], or null when they can't be fetched right now. */
private suspend fun fetchReleaseNotes(version: String): String? = try {
    val response = httpRequestRaw(
        method = "GET",
        url = "$GITHUB_API_BASE/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/$version",
        headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "NuvioMobile"),
        body = "",
        maxResponseBodyBytes = 512 * 1024,
    )
    if (response.status in 200..299) popupJson.decodeFromString<GitHubReleaseDto>(response.body).body.orEmpty() else null
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    null
}

/** Turns GitHub's markdown into plain readable lines. */
private fun readableNotes(markdown: String): String = markdown
    .replace("\r", "")
    .lines()
    .joinToString("\n") { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("#") -> trimmed.trimStart('#').trim()
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• " + trimmed.drop(2)
            else -> trimmed
        }
    }
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")
    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/**
 * Shows the notes of the installed version once, the first time the app opens after an update.
 * Nothing is shown (and nothing is marked as seen) when the notes can't be fetched, so it can try again next time.
 */
@Composable
fun WhatsNewPopupHost() {
    var content by remember { mutableStateOf<WhatsNewPopupContent?>(null) }
    LaunchedEffect(Unit) {
        val current = AppVersionConfig.VERSION_NAME
        if (AppUpdaterPlatform.getWhatsNewSeenVersion() == current) return@LaunchedEffect
        val notes = fetchReleaseNotes(current) ?: return@LaunchedEffect
        val readable = readableNotes(notes)
        if (readable.isBlank()) {
            AppUpdaterPlatform.setWhatsNewSeenVersion(current)
        } else {
            content = WhatsNewPopupContent(current, readable)
        }
    }
    val shown = content ?: return
    AlertDialog(
        onDismissRequest = {
            AppUpdaterPlatform.setWhatsNewSeenVersion(shown.version)
            content = null
        },
        title = { Text("What's new in ${shown.version}") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(shown.notes, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AppUpdaterPlatform.setWhatsNewSeenVersion(shown.version)
                content = null
            }) { Text("Got it") }
        },
    )
}
