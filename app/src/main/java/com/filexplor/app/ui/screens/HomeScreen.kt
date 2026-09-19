package com.filexplor.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.Favourite
import com.filexplor.app.data.remote.RemoteServer
import com.filexplor.app.ui.UiState
import com.filexplor.app.ui.theme.backdropRunning

/**
 * Where to go: the phone's own folders, and the servers that have been set up.
 *
 * Deliberately a starting point rather than a folder. A file manager that opens
 * straight into the storage root shows twenty dotfolders with names nobody
 * recognises; opening onto Download, Pictures and the servers is opening onto
 * the places people actually keep things.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onOpenPath: (String) -> Unit,
    onOpenFavourite: (Favourite) -> Unit,
    onRenameFavourite: (Favourite) -> Unit,
    onRemoveFavourite: (Favourite) -> Unit,
    onMoveFavourite: (Favourite, Int) -> Unit,
    onPinFavourite: (Favourite) -> Unit,
    onOpenServer: (RemoteServer) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (RemoteServer) -> Unit,
    onDeleteServer: (RemoteServer) -> Unit,
    onGrantStorage: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflow by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("FileXplor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("Appearance") },
                            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                            onClick = { overflow = false; onOpenAppearance() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.logCount > 0) "Transfer log (${state.logCount})"
                                    else "Transfer log"
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            },
                            onClick = { overflow = false; onOpenLog() }
                        )
                        DropdownMenuItem(
                            text = { Text("About FileXplor") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { overflow = false; onOpenAbout() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            if (!state.storagePermitted) {
                item { StorageNotice(onGrantStorage) }
            }

            item { SectionHeader("This phone") }
            items(state.shortcuts.size, key = { state.shortcuts[it].path }) { index ->
                val shortcut = state.shortcuts[index]
                PlaceRow(
                    title = shortcut.label,
                    subtitle = shortcut.path,
                    icon = iconForPlace(shortcut.label),
                    onClick = { onOpenPath(shortcut.path) }
                )
            }

            item { SectionHeader("Your folders") }
            if (state.favourites.isEmpty()) {
                item {
                    Hint(
                        "Nothing saved yet. Open any folder, tap the menu and choose " +
                            "\"Add shortcut\" to keep it here."
                    )
                }
            }
            // Keyed on the favourite rather than on its path, because a path is
            // not unique across this list: the same folder can be saved on two
            // different servers, and two rows sharing a key is a crash rather
            // than a cosmetic problem. The prefix keeps them clear of the
            // phone's own shortcuts above, which key on their paths.
            items(state.favourites.size, key = { "fav:" + state.favourites[it].key }) { index ->
                val favourite = state.favourites[index]
                PlaceRow(
                    title = favourite.label,
                    subtitle = subtitleFor(favourite, state.servers),
                    icon = if (favourite.serverId != null) Icons.Filled.Dns else Icons.Filled.Folder,
                    onClick = { onOpenFavourite(favourite) },
                    onLongClick = { onRenameFavourite(favourite) },
                    trailing = {
                        FavouriteMenu(
                            canMoveUp = index > 0,
                            canMoveDown = index < state.favourites.lastIndex,
                            onRename = { onRenameFavourite(favourite) },
                            onMoveUp = { onMoveFavourite(favourite, -1) },
                            onMoveDown = { onMoveFavourite(favourite, 1) },
                            onPin = { onPinFavourite(favourite) },
                            onRemove = { onRemoveFavourite(favourite) }
                        )
                    }
                )
            }

            item { SectionHeader("Servers") }
            // Said plainly rather than left to be assumed. With servers saved
            // and no screen lock on the phone, the unlock prompt cannot run and
            // they open on a tap — the app should not imply a check it is not
            // performing.
            if (state.servers.isNotEmpty() && !state.screenLockSet) {
                item {
                    Text(
                        "No screen lock on this phone, so these open without a check. " +
                            "Set a PIN or fingerprint in Settings to protect them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            if (state.servers.isEmpty()) {
                item {
                    Text(
                        "No servers yet. Add an SFTP, FTP or SMB location and it shows up here " +
                            "beside the phone's own folders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            items(state.servers.size, key = { state.servers[it].id }) { index ->
                val server = state.servers[index]
                PlaceRow(
                    title = server.displayName,
                    subtitle = "${server.protocol.label} · ${server.host}",
                    icon = Icons.Filled.Dns,
                    onClick = { onOpenServer(server) },
                    onLongClick = { onEditServer(server) },
                    trailing = {
                        ServerMenu(
                            onEdit = { onEditServer(server) },
                            onDelete = { onDeleteServer(server) }
                        )
                    }
                )
            }

            item {
                Button(
                    onClick = onAddServer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a server")
                }
            }
        }
    }
}

/**
 * What a saved folder says underneath its name.
 *
 * A path on its own is enough on the phone and useless on a server, where the
 * same /home/media exists on every machine the user has set up. Naming the
 * server is what makes two otherwise identical rows tellable apart.
 *
 * A server that has been deleted since says so here rather than only when the
 * row is tapped, so the reason is visible without finding out the hard way.
 */
private fun subtitleFor(favourite: Favourite, servers: List<RemoteServer>): String {
    val id = favourite.serverId ?: return favourite.path
    val server = servers.firstOrNull { it.id == id }
        ?: return "Server removed · ${favourite.path}"
    return "${server.displayName} · ${favourite.path}"
}

/**
 * Reordering by menu rather than by dragging.
 *
 * A drag handle inside a LazyColumn that also scrolls is a gesture fight, and
 * the list it would reorder is usually three or four rows long -- two taps to
 * move one up is quicker than getting a long-press-and-drag to take, and it
 * works for anyone who cannot hold a drag steady. The two items disable
 * themselves at the ends, so the menu always says what it will do.
 */
@Composable
private fun FavouriteMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPin: () -> Unit,
    onRemove: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Shortcut options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
        DropdownMenuItem(
            text = { Text("Move up") },
            enabled = canMoveUp,
            onClick = { open = false; onMoveUp() }
        )
        DropdownMenuItem(
            text = { Text("Move down") },
            enabled = canMoveDown,
            onClick = { open = false; onMoveDown() }
        )
        DropdownMenuItem(
            text = { Text("Add to phone's home screen") },
            onClick = { open = false; onPin() }
        )
        DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; onRemove() })
    }
}

/** A line of explanation under a section heading. */
@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * A glyph per shortcut, rather than the same folder seven times.
 *
 * Matched on the label because these labels are fixed strings written in
 * LocalFileSource.shortcuts() — not names from the filesystem and not anything
 * the user can set — and anything unrecognised falls back to a folder, so a new
 * shortcut added there looks plain rather than wrong.
 *
 * Worth the few lines: seven identical icons carry no information at all, and
 * the eye ends up reading every label to find Pictures. A camera and a musical
 * note are found without reading.
 */
private fun iconForPlace(label: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (label) {
        "Internal storage" -> Icons.Filled.Smartphone
        "SD card" -> Icons.Filled.SdCard
        "Download" -> Icons.Filled.Download
        "Pictures" -> Icons.Filled.Image
        "Camera" -> Icons.Filled.PhotoCamera
        "Music" -> Icons.Filled.MusicNote
        "Movies" -> Icons.Filled.Movie
        "Documents" -> Icons.Filled.Description
        else -> Icons.Filled.Folder
    }

/**
 * The banner shown until all-files access is granted.
 *
 * Named as the thing it stops the app doing rather than as a permission, and it
 * offers the route. Without the grant the phone's folders list is nearly empty,
 * which on its own looks like the app is broken.
 */
@Composable
private fun StorageNotice(onGrant: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "FileXplor can't see your files yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Android keeps apps out of shared storage unless you allow it. " +
                    "Without it FileXplor can reach servers, but almost nothing on the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onGrant) { Text("Allow access to files") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    // Set to the same rhythm as a row in the file list: 16dp in from the edge,
    // a 26dp glyph, 12dp of gap, the name at bodyMedium. This screen and that
    // one are the same list of places seen a moment apart, and until they were
    // measured the same the app appeared to change its mind about spacing
    // between the first tap and the second.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ServerMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Server options")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
        DropdownMenuItem(text = { Text("Remove") }, onClick = { open = false; onDelete() })
    }
}
