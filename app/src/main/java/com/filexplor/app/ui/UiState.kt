package com.filexplor.app.ui

import com.filexplor.app.data.Favourite
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.Location
import com.filexplor.app.data.Progress
import com.filexplor.app.data.SortOrder
import com.filexplor.app.data.sortedFor
import com.filexplor.app.data.ThemeMode
import com.filexplor.app.data.TransferLogEntry
import com.filexplor.app.data.ThemePalette
import com.filexplor.app.data.ViewMode
import com.filexplor.app.data.local.LocalFileSource
import com.filexplor.app.data.remote.RemoteProtocol
import com.filexplor.app.data.remote.RemoteServer

/** Which screen is in front. */
enum class Screen { HOME, BROWSE, VIEW_IMAGE, EDIT_TEXT, VIEW_PDF }

/**
 * A text file open in the editor.
 *
 * [original] is kept beside [text] so "has this changed" is answered by
 * comparison rather than by a flag something has to remember to set. A flag
 * gets out of step the first time an edit is undone by hand and the app still
 * insists there is something to save.
 */
data class EditorState(
    val item: FileItem,
    val text: String,
    val original: String,
    val saving: Boolean = false,
    /**
     * True where the file was too large or not really text.
     *
     * Shown read-only rather than refused: seeing the first part of a file you
     * cannot edit is more useful than being told nothing about it.
     */
    val readOnly: Boolean = false
) {
    val dirty: Boolean get() = !readOnly && text != original
}

/**
 * Pictures in the folder, and which one is on screen.
 *
 * The whole folder rather than the one tapped, so the viewer can be swiped
 * through — which is what anyone opening the second of forty photographs
 * expects to be able to do.
 */
data class ViewerState(
    val items: List<FileItem>,
    val index: Int
) {
    val current: FileItem? get() = items.getOrNull(index)
}

/**
 * What the clipboard is holding, and what pasting it will do.
 *
 * Cut and copy differ only here, in a single flag, and only at paste time. That
 * is deliberate: a cut that moved the files immediately would be a move with a
 * confusing name, and one that could not be pasted anywhere else would be a
 * delete with a worse one.
 */
data class Clipboard(
    val items: List<FileItem>,
    val from: Location,
    val move: Boolean
) {
    val count: Int get() = items.size
}

/**
 * A running copy, move or delete.
 *
 * Held here rather than in a service. The operation lives as long as the screen
 * that started it, which is the honest scope for something the user is watching
 * — and a file manager that carries on moving files after it is closed is a
 * file manager that has to explain itself when something goes wrong later.
 */
data class RunningJob(
    val title: String,
    val progress: Progress
)

data class UiState(
    val screen: Screen = Screen.HOME,

    // ---------- where we are ----------
    val location: Location = Location.Device,
    val path: String = "",
    val label: String = "",
    val entries: List<FileItem> = emptyList(),
    val loading: Boolean = false,

    /**
     * A refresh the user asked for by pulling, as opposed to any other listing.
     *
     * Kept apart from [loading] because the two want opposite treatment. The
     * pull indicator has to appear at once — the finger is already dragging it
     * down, and a spinner that waits looks like the gesture missed. Every other
     * listing wants the opposite: entering a local folder takes a few
     * milliseconds, and driving the same indicator off [loading] meant every
     * tap into a folder flashed a wheel that was gone before it could be read.
     */
    val refreshing: Boolean = false,

    /**
     * The trail back up, as full paths.
     *
     * Kept rather than recomputed by splitting the current path, because a
     * server's root is not "/" — it can be a base path several levels deep, and
     * splitting would offer to walk above it into folders the user has no
     * business in and often no permission for.
     */
    val crumbs: List<Crumb> = emptyList(),

    // ---------- home ----------
    val shortcuts: List<LocalFileSource.Shortcut> = emptyList(),

    /**
     * The folders the user saved, in the order they chose.
     *
     * Kept apart from [shortcuts] rather than merged into it. Those are the
     * phone's own places, the same on every phone and not removable; these are
     * a list somebody built, and the two want different rows, different menus
     * and a heading each that says which is which.
     */
    val favourites: List<Favourite> = emptyList(),

    val servers: List<RemoteServer> = emptyList(),
    val storagePermitted: Boolean = false,

    /**
     * Whether the unlock prompt has been satisfied this visit.
     *
     * Once per foreground session rather than once per server. Asking again
     * for the second server, or for the same one after a trip to the home
     * screen, teaches people to dismiss the prompt without reading it — and
     * every extra ask protects nothing, since the phone has not left their
     * hand in between. Leaving the app clears it.
     */
    val serversUnlocked: Boolean = false,

    /**
     * Whether this phone has a screen lock, and so whether the gate can exist.
     *
     * Shown to the user, so it asks the keyguard rather than the biometric
     * stack — see ServerGate.deviceSecure for why those are not the same
     * question.
     */
    val screenLockSet: Boolean = false,

    // ---------- selection ----------
    val selected: Set<String> = emptySet(),

    /**
     * Paths in this folder that already have a downloaded copy.
     *
     * Held here rather than asked per row. A row cannot notice a file arriving
     * on disk: nothing about the row changes when it does, so a per-row check
     * runs once and never again — which is why the marker used to appear only
     * after switching layouts, where the composables are torn down and rebuilt.
     */
    val downloaded: Set<String> = emptySet(),
    val clipboard: Clipboard? = null,

    // ---------- presentation ----------
    val viewMode: ViewMode = ViewMode.LIST,
    val sortOrder: SortOrder = SortOrder.NAME,
    val showHidden: Boolean = false,
    val query: String = "",

    /**
     * How many failures are waiting in the transfer log.
     *
     * The count rather than the entries: it is read on every recomposition to
     * label a menu item, and the list itself is only wanted when somebody asks
     * to see it. Nothing else needs the whole log in memory.
     */
    val logCount: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePalette: ThemePalette = ThemePalette.PAPER,

    // ---------- transient ----------
    val job: RunningJob? = null,
    val editor: EditorState? = null,
    val viewer: ViewerState? = null,
    val dialog: Dialog? = null,
    val notice: String? = null
) {
    val selecting: Boolean get() = selected.isNotEmpty()

    val selectedItems: List<FileItem> get() = entries.filter { it.path in selected }

    /**
     * What the current folder shows, after hiding, searching and sorting.
     *
     * One derived property rather than three states kept in step. Every one of
     * the three can change on its own and all of them have to be applied before
     * anything is drawn, so computing it where it is read is the only version
     * that cannot go stale.
     */
    val visible: List<FileItem>
        get() = entries
            .filter { showHidden || !it.isHidden }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedFor(sortOrder)

    val atRoot: Boolean get() = crumbs.size <= 1
}

/** One step in the path bar. */
data class Crumb(val label: String, val path: String)

sealed interface Dialog {
    /** Creating a folder in the current directory. */
    data object NewFolder : Dialog

    /**
     * Creating an empty file in the current directory.
     *
     * Separate from [NewFolder] rather than one dialog with a switch, because
     * the two differ in what can go wrong. A folder that already exists is a
     * no-op; a file that already exists would be truncated to nothing by the
     * write, so the name means something different in each case and the
     * checking is not shared.
     */
    data object NewFile : Dialog

    data class Rename(val item: FileItem) : Dialog

    data class ConfirmDelete(val items: List<FileItem>) : Dialog

    /**
     * The details panel for one entry.
     *
     * [summary] is a folder's contents, filled in while it is counted; null
     * for a file, and for a folder until the first listing comes back.
     */
    data class Details(
        val item: FileItem,
        val summary: com.filexplor.app.data.FolderSummary? = null
    ) : Dialog

    data object Appearance : Dialog

    data object Sort : Dialog

    /** Adding or editing a server. Null [existing] means adding. */
    data class ServerForm(
        val existing: RemoteServer?,
        val protocol: RemoteProtocol
    ) : Dialog

    data class ConfirmDeleteServer(val server: RemoteServer) : Dialog

    /** Choosing which protocol a new server speaks. */
    data object ChooseProtocol : Dialog

    data object About : Dialog

    /**
     * Keeping the folder currently open.
     *
     * Carries what is known at the moment the menu item is tapped rather than
     * looking it up when the dialog draws, because by then the answer could
     * have changed -- and because a dialog that read "the current folder" would
     * save wherever the user had got to, not where they were standing when
     * they asked.
     */
    data class AddShortcut(
        val path: String,
        val suggested: String,
        val serverId: String?,
        /** Already on FileXplor's home screen, so that box is offered as done. */
        val alreadySaved: Boolean,
        /** Whether this launcher takes pinned shortcuts at all. */
        val canPin: Boolean
    ) : Dialog

    data class RenameFavourite(val favourite: Favourite) : Dialog

    /**
     * The files a transfer could not manage, with the reason for each.
     *
     * Carries the entries rather than reading them as it draws, so clearing
     * the log can hand back an empty one and the dialog visibly empties
     * instead of closing out from under the button that was just pressed.
     */
    data class TransferLog(val entries: List<TransferLogEntry>) : Dialog

}
