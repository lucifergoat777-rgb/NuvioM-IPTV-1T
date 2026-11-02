package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.player.PictureShape

/** One tappable line in an actions list. */
@Composable
private fun ActionRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Asks for one line of text (a name, a number). An empty answer clears it. */
@Composable
internal fun TextPromptDialog(
    title: String,
    initial: String,
    hint: String? = null,
    numeric: Boolean = false,
    label: String? = null,
    onOk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val focus = androidx.compose.ui.focus.FocusRequester()
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (numeric) it.filter(Char::isDigit).take(5) else it },
                    singleLine = true,
                    label = { Text(label ?: title) },
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    colors = fieldColors(),
                )
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What you can do with one channel, from a long press. */
@Composable
internal fun ChannelActionsDialog(
    channel: LiveChannel,
    favorite: Boolean,
    shape: PictureShape,
    onFavorite: () -> Unit,
    onRename: (String?) -> Unit,
    onNumber: (Int?) -> Unit,
    onHide: () -> Unit,
    onShape: (PictureShape) -> Unit,
    onRemind: (() -> Unit)?,
    onRecord: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    onAddToGroup: (() -> Unit)? = null,
    onCopyToGroup: (() -> Unit)? = null,
    onRemoveFromGroup: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
    onLogo: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var numbering by remember { mutableStateOf(false) }
    if (renaming) {
        TextPromptDialog(
            title = "Rename channel",
            initial = channel.name,
            hint = "Leave it empty to go back to the playlist's name.",
            onOk = { onRename(it.ifBlank { null }); renaming = false; onDismiss() },
            onDismiss = { renaming = false },
        )
        return
    }
    if (numbering) {
        TextPromptDialog(
            title = "Channel number",
            initial = if (channel.number > 0) channel.number.toString() else "",
            hint = "Leave it empty to use the playlist's number.",
            numeric = true,
            onOk = { onNumber(it.toIntOrNull()); numbering = false; onDismiss() },
            onDismiss = { numbering = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channel.name, maxLines = 1) },
        text = {
            Column {
                ActionRow(if (favorite) "Remove from favorites" else "Add to favorites") { onFavorite(); onDismiss() }
                if (onRecord != null) ActionRow("Record\u2026") { onRecord(); onDismiss() }
                if (onMove != null) {
                    ActionRow("Move earlier in this list") { onMove(-1) }
                    ActionRow("Move later in this list") { onMove(1) }
                }
                if (onAddToGroup != null) ActionRow("Add to my group\u2026") { onAddToGroup(); onDismiss() }
                if (onCopyToGroup != null) ActionRow("Copy to another group\u2026") { onCopyToGroup(); onDismiss() }
                if (onRemoveFromGroup != null) ActionRow("Remove from this group") { onRemoveFromGroup(); onDismiss() }
                if (onGuide != null) ActionRow("Choose its guide\u2026") { onGuide(); onDismiss() }
                ActionRow("Rename") { renaming = true }
                if (onLogo != null) ActionRow("Change logo\u2026") { onLogo(); onDismiss() }
                ActionRow("Set channel number") { numbering = true }
                ActionRow("Picture shape", shape.label) {
                    val all = PictureShape.entries
                    onShape(all[(all.indexOf(shape) + 1) % all.size])
                }
                if (onRemind != null) ActionRow("Remind me when the next show starts") { onRemind(); onDismiss() }
                ActionRow("Hide channel") { onHide(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** What you can do with a group chip, from a long press. */
@Composable
internal fun GroupActionsDialog(
    title: String,
    locked: Boolean,
    onRename: (String?) -> Unit,
    onHide: () -> Unit,
    onLock: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        TextPromptDialog(
            title = "Rename group",
            initial = title,
            hint = "Leave it empty to go back to the playlist's name.",
            onOk = { onRename(it.ifBlank { null }); renaming = false; onDismiss() },
            onDismiss = { renaming = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1) },
        text = {
            Column {
                ActionRow("Rename") { renaming = true }
                ActionRow("Move earlier") { onMove(-1) }
                ActionRow("Move later") { onMove(1) }
                ActionRow(if (locked) "Remove PIN lock" else "Lock with PIN") { onLock(); onDismiss() }
                if (onDelete != null) ActionRow("Delete this group") { onDelete(); onDismiss() }
                else ActionRow("Hide group") { onHide(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** A four-digit PIN: asks for it, or sets a new one when [setting]. */
@Composable
internal fun PinDialog(
    title: String,
    setting: Boolean = false,
    expected: String? = null,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4); wrong = false },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = wrong,
                    supportingText = if (wrong) ({ Text("That PIN is wrong.") }) else null, colors = fieldColors())
                if (setting) Text(
                    "Choose four digits. You'll need them to open locked groups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4,
                onClick = { if (expected != null && pin != expected) wrong = true else onPin(pin) },
            ) { Text(if (setting) "Set PIN" else "Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hidden groups and channels, with a way to bring them back. */
@Composable
internal fun HiddenDialog(
    groups: List<Pair<String, String>>,
    channels: List<LiveChannel>,
    onShowGroup: (String) -> Unit,
    onShowChannel: (String) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden") },
        text = {
            if (groups.isEmpty() && channels.isEmpty()) {
                Text("Nothing is hidden.")
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(groups, key = { "g" + it.first }) { (id, title) ->
                        ActionRow("$title  (group)", "Show") { onShowGroup(id) }
                    }
                    items(channels, key = { "c" + it.key }) { c ->
                        ActionRow(c.name, "Show") { onShowChannel(c.key) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (groups.isNotEmpty() || channels.isNotEmpty()) TextButton(onClick = onShowAll) { Text("Show all") } },
    )
}

/** Pick one of a list. */
@Composable
internal fun PickDialog(
    title: String,
    options: List<Pair<String, String>>,
    emptyText: String = "Nothing to pick.",
    extra: Pair<String, () -> Unit>? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty() && extra == null) Text(emptyText)
            else LazyColumn(Modifier.fillMaxWidth()) {
                if (extra != null) item { ActionRow(extra.first) { extra.second() } }
                items(options, key = { it.first }) { (id, label) -> ActionRow(label) { onPick(id) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Choose which guide channel feeds a playlist channel. */
@Composable
internal fun GuidePickerDialog(
    channelName: String,
    sources: List<com.nuvio.app.livetv.model.EpgSourceChannels>,
    onPick: (com.nuvio.app.livetv.model.EpgAssignment?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(channelName.take(20)) }
    val results = remember(sources, query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList() else sources.flatMap { src ->
            src.channels.filter { e -> e.id.lowercase().contains(q) || e.names.any { it.lowercase().contains(q) } }
                .take(40).map { e -> Triple(src.sourceId, e.id, (e.names.firstOrNull() ?: e.id) + "  \u00B7  " + src.name) }
        }.take(80)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guide for $channelName", maxLines = 1) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search the guide") }, colors = fieldColors())
                ActionRow("Automatic (let the app match it)") { onPick(null) }
                ActionRow("No guide for this channel") { onPick(com.nuvio.app.livetv.model.EpgAssignment.None) }
                LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    items(results, key = { it.first + "|" + it.second }) { (src, id, label) ->
                        ActionRow(label) { onPick(com.nuvio.app.livetv.model.EpgAssignment(src, id)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
