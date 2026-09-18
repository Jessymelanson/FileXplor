package com.filexplor.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.filexplor.app.data.Favourite
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.SortOrder
import com.filexplor.app.data.ThemeMode
import com.filexplor.app.data.TransferLogEntry
import com.filexplor.app.data.ThemePalette
import com.filexplor.app.data.remote.RemoteProtocol
import com.filexplor.app.data.remote.RemoteServer
import com.filexplor.app.ui.Dialog
import com.filexplor.app.ui.formatBytes
import com.filexplor.app.ui.formatDate

/**
 * Every dialog the app can raise, in one place.
 *
 * One `when` over a sealed type rather than a boolean per dialog scattered
 * through the screens. Two dialogs can never be open at once because the state
 * holds one, and adding a new one is a branch the compiler asks for.
 */
@Composable
fun FileXplorDialogHost(
    dialog: Dialog?,
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    isDarkTheme: Boolean,
    sortOrder: SortOrder,
    onDismiss: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onThemePalette: (ThemePalette) -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onRename: (FileItem, String) -> Unit,
    onConfirmDelete: () -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onChooseProtocol: (RemoteProtocol) -> Unit,
    onSaveServer: (RemoteServer) -> Unit,
    onConfirmDeleteServer: () -> Unit,
    onOpenItem: (FileItem) -> Unit,
    onShareItem: (FileItem) -> Unit,
    onAddShortcut: (String, Boolean, Boolean) -> Unit,
    onRenameFavourite: (Favourite, String) -> Unit,
    onClearLog: () -> Unit
) {
    when (dialog) {
        null -> Unit

        is Dialog.NewFolder -> TextPromptDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmLabel = "Create",
            onConfirm = onCreateFolder,
            onDismiss = onDismiss
        )

        is Dialog.NewFile -> TextPromptDialog(
            title = "New file",
            label = "File name",
            initial = "",
            confirmLabel = "Create",
            onConfirm = onCreateFile,
            onDismiss = onDismiss
        )

        is Dialog.Rename -> TextPromptDialog(
            title = "Rename",
            label = "Name",
            initial = dialog.item.name,
            confirmLabel = "Rename",
            onConfirm = { onRename(dialog.item, it) },
            onDismiss = onDismiss
        )

        is Dialog.ConfirmDelete -> {
            val one = dialog.items.size == 1
            val folders = dialog.items.count { it.isDirectory }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        if (one) "Delete ${dialog.items.first().name}?"
                        else "Delete ${dialog.items.size} items?"
                    )
                },
                text = {
                    Text(
                        buildString {
                            append("This removes ")
                            append(if (one) "it" else "them")
                            append(" from this device or server for good. ")
                            // Naming the folders matters: a folder row gives no
                            // hint of how much is inside it, and deleting one is
                            // the single easiest way to lose more than intended.
                            if (folders > 0) {
                                append("That includes ")
                                append(if (folders == 1) "a folder" else "$folders folders")
                                append(" and everything inside. ")
                            }
                            append("There is no undo and nothing goes to a bin.")
                        }
                    )
                },
                confirmButton = { TextButton(onClick = onConfirmDelete) { Text("Delete") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }

        is Dialog.Details -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialog.item.name) },
            text = {
                Column {
                    DetailRow("Kind", dialog.item.kind.name.lowercase().replaceFirstChar { it.uppercase() })
                    if (!dialog.item.isDirectory) {
                        DetailRow("Size", formatBytes(dialog.item.size))
                    }
                    DetailRow("Modified", formatDate(dialog.item.lastModified))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        dialog.item.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (!dialog.item.isDirectory) {
                    TextButton(onClick = { onDismiss(); onOpenItem(dialog.item) }) { Text("Open") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            },
            dismissButton = {
                if (!dialog.item.isDirectory) {
                    TextButton(onClick = { onDismiss(); onShareItem(dialog.item) }) { Text("Share") }
                }
            }
        )

        is Dialog.Sort -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sort by") },
            text = {
                Column {
                    SortOrder.entries.forEach { order ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSortOrder(order) }
                        ) {
                            Text(order.label, modifier = Modifier.weight(1f))
                            if (order == sortOrder) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )

        is Dialog.Appearance -> AppearanceDialog(
            themeMode = themeMode,
            themePalette = themePalette,
            isDarkTheme = isDarkTheme,
            onModeChange = onThemeMode,
            onPaletteChange = onThemePalette,
            onDismiss = onDismiss
        )

        is Dialog.ChooseProtocol -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What kind of server?") },
            text = {
                Column {
                    RemoteProtocol.entries.forEach { protocol ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChooseProtocol(protocol) }
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(protocol.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    describe(protocol),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        is Dialog.ServerForm -> ServerSetupDialog(
            protocol = dialog.protocol,
            existing = dialog.existing,
            busy = false,
            onSubmit = onSaveServer,
            onDismiss = onDismiss
        )

        is Dialog.ConfirmDeleteServer -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove ${dialog.server.displayName}?") },
            text = {
                Text(
                    "This forgets the address and the password. Nothing on the server " +
                        "itself is touched."
                )
            },
            confirmButton = { TextButton(onClick = onConfirmDeleteServer) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        is Dialog.AddShortcut -> AddShortcutDialog(
            request = dialog,
            onConfirm = onAddShortcut,
            onDismiss = onDismiss
        )

        is Dialog.RenameFavourite -> TextPromptDialog(
            title = "Rename shortcut",
            label = "Name",
            initial = dialog.favourite.label,
            confirmLabel = "Rename",
            onConfirm = { onRenameFavourite(dialog.favourite, it) },
            onDismiss = onDismiss
        )

        is Dialog.TransferLog -> TransferLogDialog(
            entries = dialog.entries,
            onClear = onClearLog,
            onDismiss = onDismiss
        )

        is Dialog.About -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("FileXplor") },
            text = {
                Text(
                    "A file explorer for this phone and for FTP, SFTP and SMB servers.\n\n" +
                        "No account, no cloud, no telemetry. Files are read and written " +
                        "where they are; nothing is uploaded anywhere on its own, and the " +
                        "app keeps no copy of what it has seen.\n\n" +
                        "Server passwords are encrypted with a key held in this phone's " +
                        "secure hardware."
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )
    }
}

/**
 * Where a folder should be kept, asked once.
 *
 * Two checkboxes rather than a choice between two buttons, because "both" is a
 * perfectly ordinary answer -- a folder worth an icon on the phone's home
 * screen is usually one worth having in the app's list as well -- and a
 * radio-style choice would make that two trips through the same dialog.
 *
 * The name is offered here rather than after the fact. A pinned icon cannot be
 * renamed by this app once the launcher has it, so the one chance to call it
 * something short enough to fit is before it goes.
 */
@Composable
private fun AddShortcutDialog(
    request: Dialog.AddShortcut,
    onConfirm: (String, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(request.suggested) }
    // Already saved means the app's own list has nothing to add, so the box
    // starts off and says why. Whoever opened this dialog on a folder they had
    // already kept is here for the other destination.
    var toApp by remember { mutableStateOf(!request.alreadySaved) }
    var toLauncher by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            // Scrollable, because this is the tallest dialog in the app and a
            // small phone in landscape has room for about half of it.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    request.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                CheckRow(
                    checked = toApp,
                    enabled = !request.alreadySaved,
                    title = "FileXplor's home screen",
                    subtitle = if (request.alreadySaved) "Already there"
                    else "Listed under Your folders",
                    onCheckedChange = { toApp = it }
                )
                CheckRow(
                    checked = toLauncher,
                    enabled = request.canPin,
                    title = "This phone's home screen",
                    subtitle = if (request.canPin) "An icon beside your apps"
                    else "This launcher doesn't take shortcuts",
                    onCheckedChange = { toLauncher = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, toApp, toLauncher) },
                enabled = (toApp || toLauncher) && name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A checkbox with the row beside it doing the same thing.
 *
 * The box alone is a 20dp target next to two lines of text explaining it, and
 * the text is what people aim at.
 */
@Composable
private fun CheckRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Everything a transfer could not manage, newest first.
 *
 * Three lines per entry and all three earn their place: which job it belonged
 * to and when, so a failure from last night is not mistaken for one from this
 * morning; the full path, because the name alone does not say which of the
 * four copies of DCIM it was; and the reason, which is the only part that says
 * what to do about it.
 *
 * The list is bounded in height rather than left to grow. An AlertDialog will
 * happily size itself past the top of the screen and take its buttons with it,
 * and the button is half the point of this one.
 */
@Composable
private fun TransferLogDialog(
    entries: List<TransferLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (entries.isEmpty()) "Transfer log" else "Transfer log (${entries.size})")
        },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "Nothing has failed. Any file a copy, move, delete or download " +
                        "can't manage is listed here with the reason, and stays until " +
                        "you clear it."
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                "${entry.operation} · ${formatDate(entry.at)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                entry.path,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                // Java's own IO messages begin with the path,
                                // which is already the line directly above
                                // this one -- so an entry read as the path,
                                // then the path again, then the four words
                                // that actually say what happened.
                                entry.reason.removePrefix("${entry.path}: ")
                                    .trim()
                                    .ifBlank { entry.reason },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (index < entries.lastIndex) HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear") }
        }
    )
}

private fun describe(protocol: RemoteProtocol): String = when (protocol) {
    RemoteProtocol.SFTP -> "Over SSH. The usual choice for a Linux box or a NAS."
    RemoteProtocol.FTP -> "Plain FTP. Simple and old; the password crosses in the clear."
    RemoteProtocol.SMB -> "Windows file sharing, and what most NAS boxes speak too."
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * One text field and two buttons — new folder and rename are the same dialog.
 *
 * The confirm button is disabled on an empty name rather than accepting it and
 * failing afterwards, because on a server that failure costs a round trip to
 * learn something the dialog already knew.
 */
@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
