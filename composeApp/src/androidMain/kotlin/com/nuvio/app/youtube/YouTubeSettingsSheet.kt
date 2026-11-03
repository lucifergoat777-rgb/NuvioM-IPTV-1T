package com.nuvio.app.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/** The YouTube settings, full screen over the YouTube page. [onClose] reloads the feed. */
@Composable
fun YouTubeSettingsSheet(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("YouTube settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("Done") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        YouTubeSettingsContent()
                    }
                }
            }
        }
    }
}

/**
 * Every YouTube setting, in the colours of whatever it sits in: the dialog above, or a page of the
 * app's own Settings. Changes apply as they're made; the YouTube page reloads its feed when it's next shown.
 */
@Composable
fun YouTubeSettingsContent() {
    // Outside a Material surface the default text colour is black, so take the app's own.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        YouTubeSettingsBody()
    }
}

@Composable
private fun YouTubeSettingsBody() {
    val context = LocalContext.current.applicationContext
    // Bumped after every change so the switches below re-read what's stored.
    var version by remember { mutableIntStateOf(0) }
    fun changed(block: () -> Unit) {
        block()
        version++
        YouTubeSession.settingsVersion++
    }
    @Suppress("UNUSED_EXPRESSION") version

    val accounts: YouTubeAccountViewModel = viewModel { YouTubeAccountViewModel(context) }
    val account by accounts.state.collectAsStateWithLifecycle()
    var showAccounts by remember { mutableStateOf(false) }
    var showRegion by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { accounts.refresh() }

    Column(Modifier.fillMaxWidth()) {
        Section("Account")
        if (account.signedIn) {
            Choice("Signed in as ${account.accountName ?: "your account"}", "Switch or sign out") { showAccounts = true }
        } else if (account.checked) {
            Choice("Sign in", "See your subscriptions and history") { accounts.startSignIn() }
        }

        Section("Playback")
        Toggle("Autoplay next video", "When a video ends, play the next one", YouTubeSettings.autoplayNext(context)) {
            changed { YouTubeSettings.setAutoplayNext(context, it) }
        }
        Choice("Start at", YouTubeSettings.qualityLabel(YouTubeSettings.preferredHeight(context))) {
            val c = YouTubeSettings.qualityChoices
            changed { YouTubeSettings.setPreferredHeight(context, c[(c.indexOf(YouTubeSettings.preferredHeight(context)) + 1) % c.size]) }
        }
        Choice("Preferred codec", YouTubeSettings.codecLabel(YouTubeSettings.codecPreference(context))) {
            val c = YouTubeSettings.codecChoices
            changed { YouTubeSettings.setCodecPreference(context, c[(c.indexOf(YouTubeSettings.codecPreference(context)) + 1) % c.size]) }
        }
        Toggle("Open on the last tab", "Otherwise the page always starts on Home", YouTubeSettings.rememberLastTab(context)) {
            changed { YouTubeSettings.setRememberLastTab(context, it) }
        }

        Section("SponsorBlock")
        Toggle("Skip marked parts", "Sponsors, intros and other parts the community has marked", YouTubeSettings.sponsorBlockEnabled(context)) {
            changed { YouTubeSettings.setSponsorBlockEnabled(context, it) }
        }
        Toggle("Ask before skipping", "Show a Skip button instead of skipping on its own", YouTubeSettings.sponsorAsk(context)) {
            changed { YouTubeSettings.setSponsorAsk(context, it) }
        }
        Toggle("Show a notice when skipping", null, YouTubeSettings.sponsorNotice(context)) {
            changed { YouTubeSettings.setSponsorNotice(context, it) }
        }
        val on = YouTubeSettings.sponsorCategoriesOn(context)
        YouTubeSettings.sponsorCategories.forEach { (id, label) ->
            Toggle(label, null, id in on) { changed { YouTubeSettings.setSponsorCategory(context, id, it) } }
        }

        Section("Feeds")
        Toggle("Preview videos in feeds", "Plays a short muted preview of the video in view. Turn off to save data and battery.", YouTubeSettings.feedPreview(context)) { changed { YouTubeSettings.setFeedPreview(context, it) } }
        Choice("Content region", contentRegionLabel(YouTubeSettings.contentRegion(context))) { showRegion = true }
        Toggle("Hide Shorts", null, YouTubeSettings.hideShorts(context)) { changed { YouTubeSettings.setHideShorts(context, it) } }
        Toggle("Hide videos you've watched", null, YouTubeSettings.hideWatched(context)) { changed { YouTubeSettings.setHideWatched(context, it) } }
        Toggle("Hide upcoming videos", "Premieres that haven't started", YouTubeSettings.hideUpcoming(context)) { changed { YouTubeSettings.setHideUpcoming(context, it) } }
        Toggle("Better titles", "Community-written titles in place of clickbait (DeArrow)", YouTubeSettings.deArrowTitles(context)) { changed { YouTubeSettings.setDeArrowTitles(context, it) } }
        Toggle("Better thumbnails", "Thumbnails picked from the video itself (DeArrow)", YouTubeSettings.deArrowThumbnails(context)) { changed { YouTubeSettings.setDeArrowThumbnails(context, it) } }

        Section("Tabs")
        val hidden = YouTubeSettings.hiddenTabs(context)
        val order = YouTubeSettings.tabOrder(context)
        order.forEachIndexed { i, tab ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(enabled = i > 0, onClick = {
                    changed { YouTubeSettings.setTabOrder(context, order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }
                }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(enabled = i < order.lastIndex, onClick = {
                    changed { YouTubeSettings.setTabOrder(context, order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }
                }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                Switch(checked = tab.name !in hidden, onCheckedChange = { changed { YouTubeSettings.setTabVisible(context, tab, it) } })
            }
        }

        Section("Hidden words")
        var word by remember { mutableStateOf("") }
        Text(
            "Videos with these words in the title are left out of the feeds.",
            style = MaterialTheme.typography.bodySmall, color = secondary(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = word, onValueChange = { word = it }, singleLine = true,
                modifier = Modifier.weight(1f), placeholder = { Text("A word or phrase") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (word.isNotBlank()) changed { YouTubeSettings.addHiddenKeyword(context, word.trim()) }
                    word = ""
                }),
            )
            Button(onClick = {
                if (word.isNotBlank()) changed { YouTubeSettings.addHiddenKeyword(context, word.trim()) }
                word = ""
            }) { Text("Add") }
        }
        YouTubeSettings.hiddenKeywords(context).forEach { kw ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(kw, Modifier.weight(1f))
                TextButton(onClick = { changed { YouTubeSettings.removeHiddenKeyword(context, kw) } }) { Text("Remove") }
            }
        }

        Section("Hidden channels")
        val channels = YouTubeSettings.hiddenChannels(context)
        if (channels.isEmpty()) {
            Text("None. Hide a channel from the panel in the player.", style = MaterialTheme.typography.bodySmall, color = secondary())
        }
        channels.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f))
                TextButton(onClick = { changed { YouTubeSettings.unhideChannel(context, id) } }) { Text("Show again") }
            }
        }
        Text("", Modifier.padding(24.dp))
    }

    YouTubeSignInDialog(accounts, account)
    if (showRegion) {
        ContentRegionDialog(
            current = YouTubeSettings.contentRegion(context),
            onSelect = { region ->
                changed {
                    YouTubeSettings.setContentRegion(context, region)
                    YouTubeContentRegion.apply(context)
                }
                showRegion = false
            },
            onDismiss = { showRegion = false },
        )
    }
    if (showAccounts) {
        YouTubeAccountsDialog(
            account = account,
            onSelect = { id -> showAccounts = false; accounts.selectAccount(id) { YouTubeSession.settingsVersion++ } },
            onAdd = { showAccounts = false; accounts.startSignIn() },
            onSignOut = { showAccounts = false; accounts.signOut { YouTubeSession.settingsVersion++ } },
            onDismiss = { showAccounts = false },
        )
    }
    // Signing in finishes on another device, so the feeds are refreshed when it does.
    LaunchedEffect(account.signedIn, account.accountName) { YouTubeSession.settingsVersion++ }
}

@Composable
private fun secondary(): Color = LocalContentColor.current.copy(alpha = 0.65f)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.14f))
}

@Composable
private fun Choice(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = secondary())
    }
}

@Composable
private fun Toggle(label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary()) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ContentRegionDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = remember { contentRegionChoices() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Content region", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(options, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (code == current) Text("\u2713", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun contentRegionChoices(): List<Pair<String, String>> =
    listOf("" to "Device (automatic)") +
        Locale.getISOCountries().map { code ->
            code to (runCatching {
                Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: code)
        }.sortedBy { it.second }

private fun contentRegionLabel(region: String): String =
    if (region.isBlank()) "Device (automatic)"
    else runCatching {
        Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault())
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: region
