package com.nuvio.app.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.livetv.model.EpgSource
import com.nuvio.app.livetv.model.PlaylistSource

/** Your playlists: switch one off, remove it, update everything, or add another. */
@Composable
fun PlaylistsDialog(
    playlists: List<PlaylistSource>,
    epgs: List<EpgSource>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddEpg: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onEnabled: (String, Boolean) -> Unit,
    onRemoveEpg: (String) -> Unit,
    onEpgEnabled: (String, Boolean) -> Unit,
    accounts: Map<String, com.nuvio.app.livetv.model.PlaylistAccount> = emptyMap(),
    onEdit: (PlaylistSource) -> Unit = {},
) {
    var editing by remember { mutableStateOf<PlaylistSource?>(null) }
    var confirmRemove by remember { mutableStateOf<PlaylistSource?>(null) }
    var confirmRemoveEpg by remember { mutableStateOf<EpgSource?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Playlists and guide", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        playlists.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            if (p.isStalker) "Stalker portal" else if (p.isXtream) "Xtream" else "M3U",
                                            "${p.channelCount} channels".takeIf { p.channelCount > 0 },
                                            accounts[p.id]?.expiryLabel()?.let { "expires $it" },
                                            p.lastError,
                                        ).joinToString("  ·  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p.lastError != null) Color(0xFFFF6B6B) else Color(0xFFB0B0B0),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { editing = p }) { Text("Edit") }
                                Switch(checked = p.enabled, onCheckedChange = { onEnabled(p.id, it) })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                        }
                        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onAdd) { Text("Add a playlist") }
                            if (playlists.isNotEmpty()) TextButton(onClick = onRefresh) { Text("Update all") }
                        }
                        Text("Guide sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                        Text(
                            "Playlists with a guide link bring their own. Add an XMLTV link here for the rest.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0),
                        )
                        epgs.forEach { g ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(g.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            "${g.programCount} programmes".takeIf { g.programCount > 0 },
                                            g.lastError,
                                        ).joinToString("  ·  ").ifBlank { "Waiting for the first update" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (g.lastError != null) Color(0xFFFF6B6B) else Color(0xFFB0B0B0),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { confirmRemoveEpg = g }) { Text("Remove") }
                                Switch(checked = g.enabled, onCheckedChange = { onEpgEnabled(g.id, it) })
                            }
                            HorizontalDivider(color = Color(0x22FFFFFF))
                        }
                        Row(Modifier.padding(vertical = 16.dp)) {
                            Button(onClick = onAddEpg) { Text("Add a guide") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { p ->
        PlaylistEditDialog(
            playlist = p,
            account = accounts[p.id],
            onSave = { onEdit(it); editing = null },
            onRemove = { confirmRemove = p; editing = null },
            onDismiss = { editing = null },
        )
    }
    confirmRemoveEpg?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmRemoveEpg = null },
            title = { Text("Remove \"${g.name}\"?") },
            text = { Text("Channels using it lose their programme listings.") },
            confirmButton = { TextButton(onClick = { onRemoveEpg(g.id); confirmRemoveEpg = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemoveEpg = null }) { Text("Cancel") } },
        )
    }
    confirmRemove?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove \"${p.name}\"?") },
            text = { Text("Its channels leave your list. You can add it again later.") },
            confirmButton = { TextButton(onClick = { onRemove(p.id); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

/** Add a guide: the link to an XMLTV file (plain or .gz). */
@Composable
fun AddEpgDialog(onDismiss: () -> Unit, onAdd: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a guide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    url, { url = it }, label = { Text("XMLTV link") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onAdd(name, url) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add a playlist link (M3U), or an Xtream login. */
@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onM3u: (name: String, url: String) -> Unit,
    onXtream: (name: String, server: String, username: String, password: String) -> Unit,
    onStalker: (name: String, portal: String, mac: String) -> Unit = { _, _, _ -> },
) {
    // 0: M3U link, 1: Xtream login, 2: Stalker portal
    var mode by remember { mutableStateOf(0) }
    val xtream = mode == 1
    val stalker = mode == 2
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var portal by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    val ready = when (mode) {
        1 -> server.isNotBlank()
        2 -> portal.isNotBlank() && com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac)
        else -> url.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("M3U link") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Xtream") })
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("Stalker", maxLines = 1) })
                }
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                if (stalker) {
                    OutlinedTextField(
                        portal, { portal = it }, singleLine = true, label = { Text("Portal address") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        placeholder = { Text("http://host/c/", color = Color(0xFF808080)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        mac, { mac = it }, singleLine = true, label = { Text("MAC address") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        placeholder = { Text("00:1A:79:AB:CD:EF", color = Color(0xFF808080)) },
                        isError = mac.isNotBlank() && !com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac),
                    )
                    Text("Use the MAC address your provider registered for you.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0))
                } else if (!xtream) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text("Playlist link") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3,
                    )
                } else {
                    OutlinedTextField(
                        server, { server = it }, singleLine = true, label = { Text("Server, like http://host:port") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(user, { user = it }, singleLine = true, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(
                        pass, { pass = it }, singleLine = true, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    when (mode) {
                        1 -> onXtream(name, server, user, pass)
                        2 -> onStalker(name, portal, mac)
                        else -> onM3u(name, url)
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Outlines that show up on the dark dialog background. */
@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color(0x66FFFFFF),
    focusedBorderColor = Color.White,
    unfocusedLabelColor = Color(0xFFB0B0B0),
    focusedLabelColor = Color.White,
)

/** One playlist's options: its name, user agent, what to import, stream format, logos and account details. */
@Composable
private fun PlaylistEditDialog(
    playlist: PlaylistSource,
    account: com.nuvio.app.livetv.model.PlaylistAccount?,
    onSave: (PlaylistSource) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var agent by remember { mutableStateOf(playlist.userAgent) }
    var live by remember { mutableStateOf(playlist.importLive) }
    var vod by remember { mutableStateOf(playlist.importVod) }
    var embedded by remember { mutableStateOf(playlist.useEmbeddedEpg) }
    var format by remember { mutableStateOf(playlist.streamFormat) }
    var logos by remember { mutableStateOf(playlist.logoSource) }
    var portal by remember { mutableStateOf(playlist.stalkerPortal) }
    var mac by remember { mutableStateOf(playlist.stalkerMac) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.name, maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }, colors = fieldColors())
                OutlinedTextField(
                    value = agent, onValueChange = { agent = it }, singleLine = true,
                    label = { Text("User agent (leave empty for the default)") }, colors = fieldColors())
                if (playlist.isStalker) {
                    OutlinedTextField(
                        value = portal, onValueChange = { portal = it }, singleLine = true, label = { Text("Portal address") }, colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = mac, onValueChange = { mac = it }, singleLine = true, label = { Text("MAC address") }, colors = fieldColors(),
                        isError = mac.isNotBlank() && !com.nuvio.app.livetv.data.StalkerClient.isValidMac(mac),
                    )
                }
                if (playlist.isXtream) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Live TV channels", Modifier.weight(1f)); Switch(checked = live, onCheckedChange = { live = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Movies and series (On Demand)", Modifier.weight(1f)); Switch(checked = vod, onCheckedChange = { vod = it })
                    }
                    Text("Stream format", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("auto" to "Auto", "ts" to "TS", "m3u8" to "HLS").forEach { (v, l) ->
                            TextButton(onClick = { format = v }) {
                                Text(l, color = if (format == v) Color(0xFFE53935) else LocalContentColor.current)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the guide the playlist points to", Modifier.weight(1f)); Switch(checked = embedded, onCheckedChange = { embedded = it })
                }
                Text("Channel logos from", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("playlist" to "The playlist", "epg" to "The guide").forEach { (v, l) ->
                        TextButton(onClick = { logos = v }) {
                            Text(l, color = if (logos == v) Color(0xFFE53935) else LocalContentColor.current)
                        }
                    }
                }
                if (account != null && (account.expiresAtMs > 0 || account.status.isNotBlank() || account.maxConnections > 0)) {
                    HorizontalDivider()
                    Text("Account", style = MaterialTheme.typography.titleSmall)
                    account.expiryLabel()?.let { Text("Expires $it", color = if (account.expiringSoon()) Color(0xFFFF6B6B) else LocalContentColor.current) }
                    if (account.status.isNotBlank()) Text("Status: ${account.status}")
                    if (account.maxConnections > 0) Text("Connections: ${account.activeConnections} of ${account.maxConnections} in use")
                }
                TextButton(onClick = onRemove) { Text("Remove this playlist", color = Color(0xFFFF6B6B)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    playlist.copy(
                        name = name.trim().ifBlank { playlist.name }, userAgent = agent.trim(),
                        importLive = live, importVod = vod, useEmbeddedEpg = embedded, streamFormat = format, logoSource = logos,
                        stalkerPortal = if (playlist.isStalker) portal.trim().ifBlank { playlist.stalkerPortal } else playlist.stalkerPortal,
                        stalkerMac = if (playlist.isStalker) mac.trim().ifBlank { playlist.stalkerMac } else playlist.stalkerMac,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
