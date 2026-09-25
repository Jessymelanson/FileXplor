package com.filexplor.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.ViewMode
import com.filexplor.app.ui.Crumb
import com.filexplor.app.ui.UiState
import com.filexplor.app.ui.formatBytes
import com.filexplor.app.ui.formatDate
import com.filexplor.app.ui.badgeFor
import com.filexplor.app.ui.formatCount
import com.filexplor.app.ui.colourFor
import com.filexplor.app.ui.iconFor
import com.filexplor.app.ui.rememberThumbnail
import com.filexplor.app.ui.rememberFolderCount
import com.filexplor.app.ui.theme.backdropRunning

/**
 * One folder, wherever it is.
 *
 * The screen has no idea whether it is looking at the phone or a share on
 * another machine — it draws [UiState.visible] and calls back. That is the
 * payoff for the [com.filexplor.app.data.FileSource] abstraction: every feature
 * added here works on all four protocols at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: UiState,
    onUp: () -> Unit,
    onHome: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpen: (FileItem) -> Unit,
    onToggle: (FileItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onDelete: () -> Unit,
    onRename: (FileItem) -> Unit,
    onDetails: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onAddShortcut: () -> Unit,
    onOpenLog: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onOpenSort: () -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    /**
     * Where notices are shown while this screen is in front.
     *
     * Hosted here rather than by the activity's own Scaffold, and that is the
     * whole fix for a snackbar that landed on top of the Paste button. A
     * Scaffold puts its snackbar above its bottom bar; the activity's Scaffold
     * has no bottom bar and no idea this one does, so anything it showed was
     * placed against the bottom of the window -- directly over the clipboard
     * bar and the job progress bar, which are exactly the two things on screen
     * at the moment a notice is most likely to appear.
     */
    snackbarHost: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var searching by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }

    // A spinner only for loads slow enough to be worth one.
    //
    // Listing a folder on the phone is a syscall and a sort — a few
    // milliseconds — so showing progress the instant loading begins meant a
    // wheel appeared and vanished on every tap into a folder, too fast to read
    // and just long enough to notice. Waiting a fifth of a second before
    // showing anything makes local navigation silent, while a server that
    // actually takes time still reports itself almost immediately.
    var slowLoad by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.path) {
        slowLoad = false
        if (state.loading) {
            delay(200)
            slowLoad = true
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                    }
                },
                title = {
                    if (searching) {
                        // Focused as it appears, so the tap that opened it is
                        // the only one needed. It used to open unfocused: the
                        // keyboard stayed down and typing went nowhere until the
                        // field was tapped a second time.
                        val filterFocus = remember { FocusRequester() }
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Filter this folder") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(filterFocus)
                        )
                        LaunchedEffect(Unit) { filterFocus.requestFocus() }
                    } else {
                        Column {
                            Text(
                                state.crumbs.lastOrNull()?.label ?: state.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${state.visible.size} " +
                                    if (state.visible.size == 1) "item" else "items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) onQueryChange("")
                    }) {
                        Icon(
                            if (searching) Icons.Filled.Cancel else Icons.Filled.Search,
                            contentDescription = if (searching) "Stop filtering" else "Filter"
                        )
                    }
                    IconButton(onClick = { viewMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "View")
                    }
                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                        ViewMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                trailingIcon = {
                                    if (mode == state.viewMode) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { viewMenu = false; onSetViewMode(mode) }
                            )
                        }
                    }
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    // Four groups, divided, rather than seven items in the
                    // order they happened to be written.
                    //
                    // The grouping is by what the item acts on, because that is
                    // what somebody opening this menu already knows: whether
                    // they want to make something, do something to this folder,
                    // change how it is shown, or go somewhere else. A flat list
                    // makes them read all seven to find out which. "Sort" and
                    // "Show hidden files" in particular were sitting between
                    // "Add shortcut" and "Select all", so the two items that
                    // only change the view were the two hardest to spot.
                    //
                    // Every item carries an icon now. Half of them did, which
                    // is worse than none: an item with no icon reads as a
                    // different kind of thing rather than as an oversight, and
                    // the unlabelled ones were the view settings again.
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        // Make something. First because it is the only group
                        // that puts something new in the folder, and because
                        // it is what the menu is opened for most.
                        DropdownMenuItem(
                            text = { Text("New file") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                            },
                            onClick = { overflow = false; onNewFile() }
                        )
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                            },
                            onClick = { overflow = false; onNewFolder() }
                        )

                        HorizontalDivider()

                        // This folder, as a thing in itself.
                        DropdownMenuItem(
                            text = { Text("Select all") },
                            leadingIcon = {
                                Icon(Icons.Filled.SelectAll, contentDescription = null)
                            },
                            onClick = { overflow = false; onSelectAll() }
                        )
                        // On the folder that is open, not on a selection. A
                        // shortcut is to a place rather than to a thing, and
                        // the selection bar is for things -- putting it there
                        // would also mean answering what a shortcut to four
                        // files at once is supposed to be.
                        DropdownMenuItem(
                            text = { Text("Add shortcut…") },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            onClick = { overflow = false; onAddShortcut() }
                        )
                        // Last in its group because the listing also pulls to
                        // refresh, so this is the second way to do it rather
                        // than the first.
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { overflow = false; onRefresh() }
                        )

                        HorizontalDivider()

                        // How it is shown. Neither of these changes a file.
                        DropdownMenuItem(
                            text = { Text("Sort") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                            },
                            onClick = { overflow = false; onOpenSort() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.showHidden) "Hide hidden files"
                                    else "Show hidden files"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (state.showHidden) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            },
                            onClick = { overflow = false; onToggleHidden(!state.showHidden) }
                        )

                        HorizontalDivider()

                        // Not about this folder at all. Here as well as on the
                        // home screen, because this is the screen somebody is
                        // standing on when a paste comes back saying three
                        // files failed.
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
                    }
                }
            )
        },
        bottomBar = {
            // No job bar here any more: the activity hosts it below every
            // screen, because a copy outlives the folder it was started from.
            Column {
                if (state.selecting) {
                    SelectionBar(
                        count = state.selected.size,
                        single = state.selectedItems.singleOrNull(),
                        canShare = state.location == com.filexplor.app.data.Location.Device,
                        onCut = onCut,
                        onCopy = onCopy,
                        onRename = onRename,
                        onShare = onShare,
                        onDelete = onDelete,
                        onClear = onClearSelection
                    )
                } else if (state.clipboard != null && state.job == null) {
                    // Not while a job runs. The clipboard is only emptied once
                    // a paste has landed, so the bar used to stay up through
                    // the whole copy, still offering "Paste here" for the paste
                    // already under way. It comes back only if that paste
                    // landed nothing or was stopped, which is when it is wanted.
                    ClipboardBar(
                        count = state.clipboard.count,
                        move = state.clipboard.move,
                        onPaste = onPaste,
                        onClear = onClearClipboard
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CrumbBar(state.crumbs, onHome, onNavigate)

            // Pull to refresh, wrapping the whole listing.
            //
            // The overflow has a Refresh too, and both are wanted. On a server
            // the folder is someone else's and can change under you between one
            // look and the next — which is exactly the situation the gesture
            // exists for, and reaching into a menu to ask "is this still true?"
            // is too much ceremony for a question asked that often.
            //
            // An empty folder gets the gesture as well. That is the case where
            // it matters most: "empty" is also what a folder looks like when
            // the listing arrived a moment too early.
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                slowLoad && state.entries.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                state.visible.isEmpty() -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.query.isBlank()) "This folder is empty."
                        else "Nothing here matches \"${state.query}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                state.viewMode == ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.visible.size, key = { state.visible[it].path }) { index ->
                        val item = state.visible[index]
                        GridCell(
                            item = item,
                            location = state.location,
                            showHidden = state.showHidden,
                            downloaded = item.path in state.downloaded,
                            selected = item.path in state.selected,
                            onClick = { if (state.selecting) onToggle(item) else openOrEnter(item, onNavigate, onOpen) },
                            onLongClick = { onToggle(item) }
                        )
                    }
                }

                // A little air top and bottom so the first row does not sit
                // flush against the trail above it, and the last one clears
                // the bars that appear over the bottom of the list.
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp)
                ) {
                    items(state.visible.size, key = { state.visible[it].path }) { index ->
                        val item = state.visible[index]
                        FileRow(
                            item = item,
                            location = state.location,
                            showHidden = state.showHidden,
                            downloaded = item.path in state.downloaded,
                            detailed = state.viewMode == ViewMode.DETAILS,
                            selected = item.path in state.selected,
                            onClick = { if (state.selecting) onToggle(item) else openOrEnter(item, onNavigate, onOpen) },
                            onLongClick = { onToggle(item) },
                            onInfo = { onDetails(item) }
                        )
                    }
                }
            }
            }
        }
    }
}

private fun openOrEnter(item: FileItem, onNavigate: (String) -> Unit, onOpen: (FileItem) -> Unit) {
    if (item.isDirectory) onNavigate(item.path) else onOpen(item)
}

/**
 * The path, one tappable segment per level, behind a home button.
 *
 * Scrolls sideways rather than truncating. A deep path is exactly where the
 * trail is most useful, and the segment people want is usually near the end —
 * which is where a scroll leaves it.
 *
 * Home sits outside that scroll, pinned to the left. Inside it, it would be the
 * first thing to slide off on exactly the deep paths where getting out in one
 * tap is worth most — and a way out that has to be scrolled back to is not one.
 * It reads as the start of the trail because that is what it is: everything
 * here hangs off either the phone or a server, and both are on that screen.
 */
@Composable
private fun CrumbBar(crumbs: List<Crumb>, onHome: () -> Unit, onNavigate: (String) -> Unit) {
    if (crumbs.isEmpty()) return

    // Kept at the far end as the trail grows. The comment above has always said
    // the segment people want is near the end; it took pinning Home to notice
    // that nothing was actually putting it there, so a deep path opened showing
    // its first two levels and hid the folder you were standing in. Keyed on
    // the depth, so walking back up scrolls back too.
    val scroll = rememberScrollState()
    // maxValue is a key, not just the target: the effect runs before the new
    // segment has been measured, so on its own it would animate to where the
    // end used to be and stop a word short.
    LaunchedEffect(crumbs.size, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }

    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(color = if (backdropRunning()) base.copy(alpha = 0.3f) else base) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sized and padded like a crumb rather than as an IconButton: a
            // 48dp button would set the height of this whole bar and leave the
            // path it belongs to looking like an afterthought beside it.
            Icon(
                Icons.Filled.Home,
                contentDescription = "Home",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onHome)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .size(18.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A separator before every segment, the first one included —
                // it is what joins the trail to the home button beside it.
                crumbs.forEachIndexed { index, crumb ->
                    Text(
                        "›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        crumb.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == crumbs.lastIndex) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = index != crumbs.lastIndex) { onNavigate(crumb.path) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * The icon for one entry: a coloured glyph, with the extension across it.
 *
 * The badge is the part that earns its place. Fifteen kinds cover most of what
 * a phone holds, but `.kt` and `.json` are both CODE and `.7z` and `.zip` are
 * both ARCHIVE — the icon says which family, and the three letters say which
 * member. Together they answer at a glance what the filename answers only by
 * being read.
 */
@Composable
private fun FileGlyph(
    item: FileItem,
    location: com.filexplor.app.data.Location,
    showHidden: Boolean,
    downloaded: Boolean,
    size: androidx.compose.ui.unit.Dp
) {
    val colour = colourFor(item.kind)
    val density = LocalDensity.current
    val pixels = with(density) { size.roundToPx() }
    val thumbnail = rememberThumbnail(item, location, pixels)
    val folderCount = rememberFolderCount(item, location, showHidden)

    // A folder is badged with how much is in it; a file with what it is. The
    // same chip either way, because they answer the same question — "what am I
    // looking at" — and two separate treatments in one column would read as two
    // kinds of row.
    val badge = when {
        item.isDirectory -> folderCount?.let(::formatCount)
        else -> badgeFor(item.kind, item.extension)
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            // Cropped to fill, not fitted. A row of thumbnails at mixed aspect
            // ratios is a ragged column of letterboxed boxes; filling the same
            // square every time is what makes the list read as a list.
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                iconFor(item.kind),
                contentDescription = null,
                tint = colour,
                modifier = Modifier.fillMaxSize()
            )
        }
        // No badge over a thumbnail. The picture is the label, and a chip on
        // top of it covers the part of the image most likely to identify it.
        if (badge != null && thumbnail == null) {
            // Sat on the lower body of the glyph, in a chip of the icon's own
            // colour. On the icon itself it would be unreadable at list size,
            // and beside it it would be a second column that has to line up.
            //
            // The inset is why it looks attached. A Material glyph does not
            // reach the bottom of the box it is drawn in — there is roughly a
            // tenth of clear space under the shape — so aligning the chip to
            // the box put it under the folder rather than on it, and every
            // folder in the grid appeared to have grown a small tail.
            //
            // The size is capped for the same reason it is scaled at all: at
            // list size a fixed chip would be illegible, and at grid size an
            // unbounded one became a headline. Two or three characters at 12sp
            // is the most this ever needs to be.
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontSize = (size.value * 0.24f).coerceAtMost(12f).sp,
                lineHeight = (size.value * 0.26f).coerceAtMost(13f).sp,
                color = MaterialTheme.colorScheme.surface,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = size * 0.1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colour)
                    .padding(horizontal = 2.dp)
            )
        }

        // The copy-on-disk marker, in the opposite corner from the badge so the
        // two never collide on a narrow icon. A server file wearing this opens
        // instantly and costs nothing to open again; without it there is no way
        // to tell that apart from a file that is about to be fetched twice.
        if (downloaded) {
            Icon(
                Icons.Filled.Save,
                contentDescription = "Already downloaded",
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(size * 0.06f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    location: com.filexplor.app.data.Location,
    showHidden: Boolean,
    downloaded: Boolean,
    detailed: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfo: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            // Tighter on the ends than the start: the trailing icon carries its
            // own padding, and 16dp on top of that pushed it away from the edge
            // far enough to read as a margin error.
            .padding(
                start = 16.dp,
                end = 4.dp,
                top = if (detailed) 7.dp else 5.dp,
                bottom = if (detailed) 7.dp else 5.dp
            )
    ) {
        FileGlyph(
            item = item,
            location = location,
            showHidden = showHidden,
            downloaded = downloaded,
            size = if (detailed) 36.dp else 26.dp
        )
        Spacer(Modifier.width(if (detailed) 14.dp else 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Only Details stacks. In list mode the size moved to the far right
            // instead, which is what halves the row: a second line costs the
            // full height of a line to say one short thing, and that thing
            // lines up into a readable column when it sits at the end.
            if (detailed) {
                Text(
                    buildString {
                        if (!item.isDirectory) {
                            append(formatBytes(item.size))
                            append(" · ")
                        }
                        append(formatDate(item.lastModified))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (!detailed && !item.isDirectory) {
            Text(
                formatBytes(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        // A plain clickable glyph rather than an IconButton. IconButton is 48dp
        // whatever is inside it, and with a 26dp icon beside it that was the
        // thing setting every row's height — the list could not be condensed at
        // all while it was there. The trade is a 34dp touch target instead of
        // 48dp, which is fine for a secondary action sitting on a row that is
        // itself tappable end to end.
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp).size(20.dp)
            )
        } else {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onInfo)
                    .padding(8.dp)
                    .size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    item: FileItem,
    location: com.filexplor.app.data.Location,
    showHidden: Boolean,
    downloaded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Each cell is a tile with a ground of its own. Before this the grid was
    // icons floating on the page with nothing holding them, which reads as
    // scattered rather than arranged — and against an animated backdrop it was
    // worse, since the moving pattern ran straight between them. The same
    // transparency the crumb bar uses lets the backdrop through without
    // letting it interrupt.
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val tile = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        backdropRunning() -> base.copy(alpha = 0.3f)
        else -> base
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tile)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        // Two thirds of the tile. At half it read as a small picture sitting in
        // a large empty box; much past this and a plain icon — which has no
        // detail to reward the size — starts to look shouted.
        FileGlyph(item = item, location = location, showHidden = showHidden, downloaded = downloaded, size = 76.dp)
        Spacer(Modifier.height(7.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 15.sp
        )
        if (!item.isDirectory) {
            Text(
                formatBytes(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    single: FileItem?,
    canShare: Boolean,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onRename: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    "$count selected",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BarAction(Icons.Filled.ContentCut, "Cut", onCut, Modifier.weight(1f))
                BarAction(Icons.Filled.ContentCopy, "Copy", onCopy, Modifier.weight(1f))
                // Renaming one thing is clear; renaming eight at once is a
                // different feature with its own rules, so it is offered only
                // where the answer is unambiguous.
                BarAction(
                    Icons.Filled.DriveFileRenameOutline, "Rename",
                    { single?.let(onRename) }, Modifier.weight(1f), enabled = single != null
                )
                BarAction(
                    Icons.Filled.Share, "Share",
                    { single?.let(onShare) }, Modifier.weight(1f),
                    enabled = single != null && canShare && !single.isDirectory
                )
                BarAction(
                    Icons.Filled.Delete, "Delete", onDelete, Modifier.weight(1f),
                    destructive = true
                )
            }
        }
    }
}

@Composable
private fun ClipboardBar(count: Int, move: Boolean, onPaste: () -> Unit, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "$count ${if (count == 1) "item" else "items"} to ${if (move) "move" else "copy"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Cancel") }
            TextButton(onClick = onPaste) { Text("Paste here") }
        }
    }
}

/**
 * The bar for a running copy, move or delete.
 *
 * Carries a Stop, because these are the only operations in the app that take
 * long enough to regret starting — and one that cannot be stopped part way is
 * one people hesitate to start at all.
 */
@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
