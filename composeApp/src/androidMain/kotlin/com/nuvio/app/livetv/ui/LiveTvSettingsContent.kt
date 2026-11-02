package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.ChannelSort
import com.nuvio.app.livetv.model.LiveTvSettings
import kotlinx.coroutines.launch

/** The Live TV settings page, shown inside Nuvio's settings. */
@Composable
fun LiveTvSettingsContent() {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val settings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = LiveTvSettings())
    val scope = rememberCoroutineScope()
    fun change(transform: (LiveTvSettings) -> LiveTvSettings) {
        scope.launch { live.prefs.updateSettings(transform) }
    }
    var pinStep by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxWidth()) {
            Section("Channels")
            ChoiceRow(
                "Sort channels",
                when (settings.channelSort) { ChannelSort.PLAYLIST -> "As in the playlist"; ChannelSort.NUMBER -> "By number"; ChannelSort.NAME -> "By name" },
                listOf("As in the playlist" to ChannelSort.PLAYLIST, "By number" to ChannelSort.NUMBER, "By name" to ChannelSort.NAME),
            ) { v -> change { it.copy(channelSort = v) } }
            ChoiceRow(
                "Order of groups",
                when (settings.groupSort) { "playlist" -> "As in the playlist"; "name" -> "A to Z"; else -> "Mine" },
                listOf("Mine (long-press a group to move it)" to "custom", "As in the playlist" to "playlist", "A to Z" to "name"),
            ) { v -> change { it.copy(groupSort = v) } }
            SwitchRow("Remember the last group", "Open Live TV on the group you were in.", settings.rememberLastGroup) { v -> change { it.copy(rememberLastGroup = v) } }
            SwitchRow("All channels group", null, settings.showAllChannelsGroup) { v -> change { it.copy(showAllChannelsGroup = v) } }
            SwitchRow("Favorites group", null, settings.showFavoritesGroup) { v -> change { it.copy(showFavoritesGroup = v) } }
            SwitchRow("Recent group", "Channels you watched lately.", settings.showRecentGroup) { v -> change { it.copy(showRecentGroup = v) } }
            SwitchRow("Channel count on groups", null, settings.showGroupCounts) { v -> change { it.copy(showGroupCounts = v) } }
            SwitchRow("Channel numbers", null, settings.showChannelNumbers) { v -> change { it.copy(showChannelNumbers = v) } }
            SwitchRow("Channel logos", null, settings.showChannelLogos) { v -> change { it.copy(showChannelLogos = v) } }
            SwitchRow("Compact rows", "Fit more channels on screen.", settings.compactRows) { v -> change { it.copy(compactRows = v) } }
            SwitchRow("Number channels in order", "1, 2, 3 down the list you see, whatever the playlist says.", settings.sequentialNumbers) { v -> change { it.copy(sequentialNumbers = v) } }
            var editNames by remember { mutableStateOf(false) }
            ActionLine("Clean up channel names", if (settings.nameRemovals.isBlank()) "Remove tags like \"US:\" or \"HD\" from names." else "Removing: ${settings.nameRemovals}") { editNames = true }
            if (editNames) {
                TextPromptDialog(
                    title = "Words to remove from names",
                    initial = settings.nameRemovals,
                    hint = "Separate them with commas, for example: US:, HD, FHD, 4K",
                    onOk = { v -> change { it.copy(nameRemovals = v) }; editNames = false },
                    onDismiss = { editNames = false },
                )
            }
            ActionLine("Put channels back in playlist order", "Forget the order you set with Move earlier / later.") { scope.launch { live.prefs.resetChannelOrder() } }

            Section("Guide")
            SwitchRow("24-hour clock", null, settings.use24HourClock) { v -> change { it.copy(use24HourClock = v) } }
            StepperRow("Shift the guide", "${settings.epgOffsetMinutes / 60.0} hours".replace(".0 ", " "),
                onMinus = { change { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes - 30).coerceAtLeast(-12 * 60)) } },
                onPlus = { change { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes + 30).coerceAtMost(12 * 60)) } })
            StepperRow("Days of guide to keep", "${settings.epgFutureDays} days ahead",
                onMinus = { change { it.copy(epgFutureDays = (it.epgFutureDays - 1).coerceAtLeast(1)) } },
                onPlus = { change { it.copy(epgFutureDays = (it.epgFutureDays + 1).coerceAtMost(14)) } })
            StepperRow("Past programmes", "${settings.epgPastHours} hours back",
                onMinus = { change { it.copy(epgPastHours = (it.epgPastHours - 12).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(epgPastHours = (it.epgPastHours + 12).coerceAtMost(7 * 24)) } })
            ChoiceRow(
                "Guide size",
                when (settings.guideSize) { "COMPACT" -> "Compact"; "LARGE" -> "Large"; "XLARGE" -> "Extra large"; else -> "Standard" },
                listOf("Compact" to "COMPACT", "Standard" to "STANDARD", "Large" to "LARGE", "Extra large" to "XLARGE"),
            ) { v -> change { it.copy(guideSize = v) } }
            SwitchRow("Show start and end times", "Both times under each show, in taller rows.", settings.showProgramTimes) { v -> change { it.copy(showProgramTimes = v) } }
            SwitchRow("Show episode numbers", "\"S2 E5\" under a show's name.", settings.showEpisodeNumbers) { v -> change { it.copy(showEpisodeNumbers = v) } }
            SwitchRow("Line for the current time", null, settings.showNowLine) { v -> change { it.copy(showNowLine = v) } }
            ChoiceRow(
                "Refresh the guide",
                "Every ${settings.epgRefreshHours} hours",
                listOf(6, 12, 24, 48).map { "Every $it hours" to it },
            ) { v -> change { it.copy(epgRefreshHours = v) } }
            ChoiceRow(
                "Refresh playlists",
                "Every ${settings.playlistRefreshHours} hours",
                listOf(6, 12, 24, 48, 168).map { "Every $it hours" to it },
            ) { v -> change { it.copy(playlistRefreshHours = v) } }

            Section("Playback")
            SwitchRow("Reconnect when a channel drops", "Try again a few times before giving up.", settings.autoReconnect) { v -> change { it.copy(autoReconnect = v) } }
            SwitchRow("Prefer HLS for catch-up", "Gives replays a length, so you can skip around.", settings.catchupPreferHls) { v -> change { it.copy(catchupPreferHls = v) } }
            StepperRow("Catch-up time shift", "${settings.catchupShiftMinutes} minutes",
                onMinus = { change { it.copy(catchupShiftMinutes = (it.catchupShiftMinutes - 5).coerceAtLeast(-180)) } },
                onPlus = { change { it.copy(catchupShiftMinutes = (it.catchupShiftMinutes + 5).coerceAtMost(180)) } })

            ChoiceRow(
                "Buffer size",
                when (settings.bufferSize) { "small" -> "Small"; "large" -> "Large"; "xlarge" -> "Extra large"; else -> "Normal" },
                listOf("Small (fastest channel changes)" to "small", "Normal" to "normal", "Large (for a poor connection)" to "large", "Extra large" to "xlarge"),
            ) { v -> change { it.copy(bufferSize = v) } }
            SwitchRow("Try another copy when a channel won't play", "Another channel with the same name takes over.", settings.autoBackupChannel) { v -> change { it.copy(autoBackupChannel = v) } }
            SwitchRow("Closed captions on by default", "Turn captions on when a channel has them.", settings.captionsByDefault) { v -> change { it.copy(captionsByDefault = v) } }
            SwitchRow("Dolby / DTS passthrough", "Send surround sound on untouched. Off: the phone decodes it.", settings.audioPassthrough) { v -> change { it.copy(audioPassthrough = v) } }
            ChoiceRow(
                "Frame rate",
                if (settings.frameRateMode == "OFF") "Leave as it is" else "Match the picture",
                listOf("Leave as it is" to "OFF", "Match the picture (smoother motion)" to "START"),
            ) { v -> change { it.copy(frameRateMode = v) } }

            Section("Pause and rewind")
            SwitchRow("Pause and rewind", "Pausing a channel keeps recording it, so you carry on where you stopped.", settings.timeshiftEnabled) { v -> change { it.copy(timeshiftEnabled = v) } }
            StepperRow("Keep the last", "${settings.timeshiftMinutes} minutes",
                onMinus = { change { it.copy(timeshiftMinutes = (it.timeshiftMinutes - 5).coerceAtLeast(5)) } },
                onPlus = { change { it.copy(timeshiftMinutes = (it.timeshiftMinutes + 5).coerceAtMost(30)) } })
            SwitchRow("Rewind anytime", "Keep the last minutes while you watch, so you can rewind without pausing. Uses a second connection to your provider.", settings.rewindAlways) { v -> change { it.copy(rewindAlways = v) } }

            Section("Sports")
            val sports = remember { live.sports }
            val sportsSettings by sports.settings.collectAsStateWithLifecycle()
            ChoiceRow(
                "Your region",
                com.nuvio.app.livetv.sports.SportsCatalog.regionName(com.nuvio.app.livetv.sports.SportsCatalog.regionOf(sportsSettings)),
                com.nuvio.app.livetv.sports.SportsCatalog.regions.map { (code, name) -> name to code },
            ) { v -> sports.updateSettings { it.copy(region = v) } }
            ChoiceRow(
                "Channels from",
                com.nuvio.app.livetv.sports.ChannelRegions.all.firstOrNull { it.first == sportsSettings.channelRegion }?.second ?: "All regions",
                com.nuvio.app.livetv.sports.ChannelRegions.all.map { (code, name) -> name to code },
            ) { v -> sports.updateSettings { it.copy(channelRegion = v) } }
            SwitchRow("Video window", "Watch games in a window at the top of the Sports page. Off: games open full screen.", sportsSettings.videoWindow) { v -> sports.updateSettings { it.copy(videoWindow = v) } }
            SwitchRow("Hide scores", "Keep results off the screen until you're ready.", sportsSettings.hideScores) { v -> sports.updateSettings { it.copy(hideScores = v) } }
            SwitchRow("Scoring alerts", "A notification when a team you follow scores.", sportsSettings.scoringAlerts) { v -> sports.updateSettings { it.copy(scoringAlerts = v) } }
            SwitchRow("Remind me about my teams", "A notification before every game of a team you follow, without asking for each one.", sportsSettings.remindMyTeams) { v -> sports.updateSettings { it.copy(remindMyTeams = v) } }
            SwitchRow("Switch to the game", "Open the game's channel by itself a minute before it starts, with a few seconds to cancel. Needs the app open.", sportsSettings.autoSwitch) { v -> sports.updateSettings { it.copy(autoSwitch = v) } }
            StepperRow("Remind me before a game", "${sportsSettings.remindMinutes} minutes",
                onMinus = { sports.updateSettings { it.copy(remindMinutes = (it.remindMinutes - 5).coerceAtLeast(0)) } },
                onPlus = { sports.updateSettings { it.copy(remindMinutes = (it.remindMinutes + 5).coerceAtMost(120)) } })
            SwitchRow("Only games on my channels", "Leave out games none of your channels show.", sportsSettings.onlyOnMyChannels) { v -> sports.updateSettings { it.copy(onlyOnMyChannels = v) } }
            SwitchRow("Best channel first", "Put the likeliest channel at the top of a game's list.", sportsSettings.bestChannelFirst) { v -> sports.updateSettings { it.copy(bestChannelFirst = v) } }
            ActionLine("Forget the channels I picked", "Undo every \"Wrong channel?\" fix.") { sports.clearFixes() }

            Section("Recordings")
            StepperRow("Start recording early", "${settings.recordPreMinutes} minutes",
                onMinus = { change { it.copy(recordPreMinutes = (it.recordPreMinutes - 1).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(recordPreMinutes = (it.recordPreMinutes + 1).coerceAtMost(30)) } })
            StepperRow("Keep recording after the show", "${settings.recordPostMinutes} minutes",
                onMinus = { change { it.copy(recordPostMinutes = (it.recordPostMinutes - 1).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(recordPostMinutes = (it.recordPostMinutes + 1).coerceAtMost(60)) } })
            val storage = remember { com.nuvio.app.livetv.record.RecordingStorage.choices(context) }
            ChoiceRow(
                "Save recordings to",
                storage.firstOrNull { it.id == settings.recordingStorage }?.label ?: "This device",
                storage.map { "${it.label}  (${com.nuvio.app.livetv.record.RecordingStorage.sizeLabel(it.freeBytes)} free)" to it.id },
            ) { v -> change { it.copy(recordingStorage = v) } }
            ChoiceRow(
                "When space runs low",
                if (settings.recordingSpaceRule == "DELETE_OLDEST") "Delete the oldest recording" else "Stop recording",
                listOf("Stop recording" to "STOP", "Delete the oldest recording" to "DELETE_OLDEST"),
            ) { v -> change { it.copy(recordingSpaceRule = v) } }

            Section("On screen")
            SwitchRow("4K / FHD / HD badges", "Next to channels you've watched.", settings.showQualityBadges) { v -> change { it.copy(showQualityBadges = v) } }
            ChoiceRow(
                "Clock over the picture",
                if (settings.clockMode == "always") "Always" else "Off",
                listOf("Off" to "never", "Always" to "always"),
            ) { v -> change { it.copy(clockMode = v) } }
            ChoiceRow(
                "Clock position",
                when (settings.clockPosition) { "top_left" -> "Top left"; "bottom_left" -> "Bottom left"; "bottom_right" -> "Bottom right"; else -> "Top right" },
                listOf("Top left" to "top_left", "Top right" to "top_right", "Bottom left" to "bottom_left", "Bottom right" to "bottom_right"),
            ) { v -> change { it.copy(clockPosition = v) } }
            ChoiceRow(
                "Clock size",
                when (settings.clockSize) { "small" -> "Small"; "large" -> "Large"; else -> "Medium" },
                listOf("Small" to "small", "Medium" to "medium", "Large" to "large"),
            ) { v -> change { it.copy(clockSize = v) } }
            StepperRow("Clock opacity", "${settings.clockOpacity} %",
                onMinus = { change { it.copy(clockOpacity = (it.clockOpacity - 10).coerceAtLeast(20)) } },
                onPlus = { change { it.copy(clockOpacity = (it.clockOpacity + 10).coerceAtMost(100)) } })
            SwitchRow("Clock background", "A soft dark pill behind the clock.", settings.clockBackground) { v -> change { it.copy(clockBackground = v) } }

            Section("Starting")
            var startPage by remember { mutableStateOf(LiveTvStart.startPage(context)) }
            ChoiceRow(
                "Open the app on",
                if (startPage == "LIVE_TV") "Live TV" else "Home",
                listOf("Home" to "HOME", "Live TV" to "LIVE_TV"),
            ) { v -> LiveTvStart.setStartPage(context, v); startPage = v }
            SwitchRow("Play the last channel", "When Live TV opens, start the channel you watched last.", settings.autoPlayLastChannel) { v -> change { it.copy(autoPlayLastChannel = v) } }

            DriveSyncSection(live)

            Section("Parental controls")
            if (settings.parentalPin.isBlank()) {
                ActionLine("Set a PIN", "Then long-press a group to lock it.") { pinStep = "set" }
            } else {
                ActionLine("Change PIN", null) { pinStep = "verify-change" }
                ActionLine("Remove PIN", "Locked groups open freely again.") { pinStep = "verify-remove" }
            }

            if (settings.parentalPin.isNotBlank()) {
                ChoiceRow(
                    "Lock again",
                    when (settings.relockMinutes) {
                        -1 -> "When I leave Live TV"
                        0 -> "When the app closes"
                        60 -> "After 1 hour"
                        1 -> "After 1 minute"
                        else -> "After ${settings.relockMinutes} minutes"
                    },
                    listOf("When I leave Live TV" to "-1", "After 1 minute" to "1", "After 5 minutes" to "5", "After 15 minutes" to "15", "After 1 hour" to "60", "When the app closes" to "0"),
                ) { v -> change { it.copy(relockMinutes = v.toIntOrNull() ?: -1) } }
                SwitchRow("Lock adult groups automatically", "Groups with an adult name need the PIN too.", settings.lockAdultContent) { v -> change { it.copy(lockAdultContent = v) } }
            }

            Section("Reset")
            ActionLine("Clear names, numbers and group order", "Hidden channels and favorites stay as they are.") { confirmReset = true }
        }
    }

    when (pinStep) {
        "set", "change" -> PinDialog(
            title = "Choose a new PIN",
            setting = true,
            onPin = { pin -> change { it.copy(parentalPin = pin) }; pinStep = null },
            onDismiss = { pinStep = null },
        )
        "verify-change" -> PinDialog(
            title = "Enter your current PIN",
            expected = settings.parentalPin,
            onPin = { pinStep = "change" },
            onDismiss = { pinStep = null },
        )
        "verify-remove" -> PinDialog(
            title = "Enter your PIN to remove it",
            expected = settings.parentalPin,
            onPin = {
                change { it.copy(parentalPin = "") }
                scope.launch { live.userState.value.lockedGroups.forEach { id -> live.prefs.setGroupLocked(id, false) } }
                pinStep = null
            },
            onDismiss = { pinStep = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Clear your edits?") },
            text = { Text("Channel names, channel numbers, group names and group order go back to what the playlist says.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { live.prefs.resetChannelEdits(); live.prefs.resetGroupEdits() }
                    confirmReset = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    HorizontalDivider(Modifier.padding(bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionLine(title: String, detail: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onMinus) { Text("−") }
        TextButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ActionLine(title, current) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEach { (label, value) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(value); open = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** Syncs your Live TV setup through your own Google Drive, shared with the TV app. */
@Composable
private fun DriveSyncSection(live: LiveTv) {
    val sync = live.driveSync
    val state by sync.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askRestore by remember { mutableStateOf<com.nuvio.app.livetv.sync.LiveTvDriveSync.BackupInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    Section("Sync with Google Drive")
    if (!state.available) {
        Text(
            "Google Drive sync isn't set up in this build (no Google client in the build settings).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }
    if (!state.connected) {
        ActionLine(
            "Connect Google Drive",
            "Keeps your groups, favorites, names and playlists in step with your TV and other devices. Stored in a hidden folder only this app can read.",
        ) { sync.beginSignIn() }
    } else {
        Text(
            "Connected" + (state.email?.let { " as $it" } ?: "") +
                if (state.lastSyncMs > 0) ".  Last synced " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(state.lastSyncMs)) else ".",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp),
        )
        SwitchRow("Sync automatically", "Back up changes soon after you make them, and pick up newer ones from your other devices.", state.autoSync) { sync.setAutoSync(it) }
        ActionLine("Back up now", "Save this device's setup to Google Drive.") { sync.backUpNow() }
        ActionLine("Restore from Google Drive", "Replace this device's setup with the saved copy.") {
            scope.launch {
                checking = true
                val info = sync.backupInfo()
                checking = false
                if (info == null) android.widget.Toast.makeText(context, "No backup found in Google Drive yet.", android.widget.Toast.LENGTH_SHORT).show()
                else askRestore = info
            }
        }
        ActionLine("Disconnect", "Stops syncing. Your backup stays in your Google Drive.") { sync.disconnect() }
    }
    if (state.busy || checking) Text("Working…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }

    state.signIn?.let { p ->
        // The browser opens at Google's page with the code already in it: choose your account and approve.
        androidx.compose.runtime.LaunchedEffect(p.userCode) {
            // The code goes on the clipboard too, to paste if Google's page asks for it.
            runCatching {
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Google code", p.userCode))
            }
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.qrUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        AlertDialog(
            onDismissRequest = { sync.cancelSignIn() },
            title = { Text("Connect Google Drive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your browser opens Google's page. If it asks for a code, paste it (it's already copied) or type this one:")
                    Text(p.userCode, style = MaterialTheme.typography.headlineMedium)
                    Text(p.url, style = MaterialTheme.typography.bodyMedium)
                    Text("This window closes by itself once you've approved it.", style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        runCatching {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("Google code", p.userCode))
                            android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Copy code") }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.qrUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Open again") }
                }
            },
            dismissButton = { TextButton(onClick = { sync.cancelSignIn() }) { Text("Cancel") } },
        )
    }
    askRestore?.let { info ->
        AlertDialog(
            onDismissRequest = { askRestore = null },
            title = { Text("Restore from Google Drive?") },
            text = {
                Text(
                    "Saved " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(info.savedAtMs)) +
                        " on ${if (info.fromThisTv) "this device" else info.device}. This replaces this device's groups, favorites, names and playlists.",
                )
            },
            confirmButton = { TextButton(onClick = { askRestore = null; sync.restoreNow() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { askRestore = null }) { Text("Cancel") } },
        )
    }
}
