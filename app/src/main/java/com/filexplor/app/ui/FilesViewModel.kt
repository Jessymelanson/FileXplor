package com.filexplor.app.ui

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filexplor.app.FileXplorApp
import com.filexplor.app.auth.ServerGate
import com.filexplor.app.data.Favourite
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.FileKind
import com.filexplor.app.data.FileOperations
import com.filexplor.app.data.DownloadCache
import com.filexplor.app.data.FileSource
import com.filexplor.app.data.JobNotifications
import com.filexplor.app.data.Location
import com.filexplor.app.data.PinShortcuts
import com.filexplor.app.data.Progress
import com.filexplor.app.data.SortOrder
import com.filexplor.app.data.TransferService
import com.filexplor.app.data.ThemeMode
import com.filexplor.app.data.ThemePalette
import com.filexplor.app.data.ViewMode
import com.filexplor.app.data.joinPath
import com.filexplor.app.data.isWithin
import com.filexplor.app.data.nameProblem
import com.filexplor.app.data.parentPath
import com.filexplor.app.data.samePath
import com.filexplor.app.data.readAtMost
import com.filexplor.app.data.parentOf
import com.filexplor.app.data.remote.RemoteFileSource
import com.filexplor.app.data.remote.RemoteProtocol
import com.filexplor.app.data.remote.RemoteServer
import com.filexplor.app.data.uniqueName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

/**
 * Everything the screens ask for, and the only thing that knows where the files
 * are coming from.
 *
 * The one rule it keeps: no source call happens on the main thread. Listing a
 * local directory is fast enough to be tempting, and doing it inline is how the
 * app freezes the first time somebody opens a folder on a slow SD card — so
 * both kinds of source go through the same [withContext] and neither gets a
 * special case.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val app: FileXplorApp get() = getApplication()

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /**
     * The source being browsed, and the connection behind it.
     *
     * One at a time. A file manager shows one folder, and holding open every
     * server ever visited would keep sockets alive for screens nobody is
     * looking at — on SMB that is a session the server counts against a limit.
     */
    private var source: FileSource? = null

    private var listJob: Job? = null

    /** Holds the pull indicator on screen; see [refresh]. */
    private var refreshJob: Job? = null

    /**
     * Which listing is the current one.
     *
     * Bumped on every navigation so a job that finishes late can tell whether
     * it is still the one the screen is waiting for.
     */
    private var listGeneration = 0
    private var operationJob: Job? = null

    /**
     * Whether the app is actually on screen.
     *
     * Only used to decide whether a finished job needs to leave a notification
     * behind. Somebody watching a copy finish has already read the snackbar;
     * somebody whose phone is in their pocket has not, and for them the
     * snackbar said its piece to an empty room and timed out. That is the whole
     * of "it failed and told me nothing".
     */
    private var foreground = true

    /** Set from the Activity's onStart and onStop. A rotation is not leaving. */
    fun setForeground(value: Boolean) {
        foreground = value
    }

    init {
        _state.value = _state.value.copy(
            themeMode = app.prefs.themeMode,
            themePalette = app.prefs.themePalette,
            viewMode = app.prefs.viewMode,
            sortOrder = app.prefs.sortOrder,
            showHidden = app.prefs.showHidden
        )
        refreshHome()
        mirrorJobToStatusBar()
    }

    /**
     * Keeps the notification in step with whatever job is running.
     *
     * Driven off state rather than posted by hand at each call site, which is
     * the only version that cannot drift: every path that starts, advances,
     * finishes, fails or cancels a job already writes it here, and none of them
     * has to remember anything extra. The first thing this publishes on a fresh
     * start is "no job", which also clears a notification stranded in the shade
     * by a process Android killed.
     */
    private fun mirrorJobToStatusBar() {
        viewModelScope.launch {
            var postedAt = 0L
            var running = false
            state.map { it.job }.distinctUntilChanged().collect { job ->
                if (job == null) {
                    postedAt = 0L
                    if (!running) {
                        // Nothing was running, so this is the first thing this
                        // collector ever publishes: a fresh start, clearing
                        // whatever a killed process left stranded in the shade.
                        JobNotifications.clear(app)
                        return@collect
                    }
                    running = false
                    TransferService.stop(app)

                    // Read here, while the state update that ended the job is
                    // still the current one, because the snackbar clears the
                    // notice a second or so later and by then there is nothing
                    // left to report.
                    val outcome = _state.value.notice
                    if (!foreground && !outcome.isNullOrBlank()) {
                        JobNotifications.showOutcome(app, "FileXplor", outcome)
                    }
                    return@collect
                }

                if (!running) {
                    running = true
                    // Once, from the tap that started the job, while the app is
                    // certainly in the foreground -- which is the only moment
                    // starting a foreground service is certainly allowed. It
                    // posts the first notification itself, so there is nothing
                    // to publish here.
                    TransferService.start(app, job.title)
                    postedAt = SystemClock.elapsedRealtime()
                    return@collect
                }

                // Throttled harder than the bar on screen. The system drops
                // notification updates that arrive faster than a few per
                // second anyway, so a post per 150ms progress tick is work
                // thrown away.
                val now = SystemClock.elapsedRealtime()
                if (now - postedAt < NOTIFICATION_INTERVAL_MS) return@collect
                postedAt = now
                JobNotifications.show(
                    context = app,
                    title = job.title,
                    current = job.progress.currentName,
                    percent = if (job.progress.preparing) {
                        null
                    } else {
                        (job.progress.fraction * 100).toInt()
                    }
                )
            }
        }
    }

    // ---------- home ----------

    /**
     * Re-reads the places list and the server list.
     *
     * Called on every return to the foreground, because the storage permission
     * is granted in Settings — outside this app — and coming back to a home
     * screen still saying "no permission" after granting it would read as the
     * app not noticing.
     */
    fun refreshHome() {
        viewModelScope.launch {
            val permitted = hasStoragePermission()
            val shortcuts = withContext(Dispatchers.IO) {
                if (permitted) app.local.shortcuts() else emptyList()
            }
            val servers = withContext(Dispatchers.IO) { app.servers.all() }
            val favourites = withContext(Dispatchers.IO) { app.favourites.all() }
            val failures = withContext(Dispatchers.IO) { app.log.count() }
            _state.value = _state.value.copy(
                storagePermitted = permitted,
                shortcuts = shortcuts,
                servers = servers,
                favourites = favourites,
                logCount = failures,
                screenLockSet = ServerGate.deviceSecure(app)
            )
        }
    }

    /**
     * Whether the app can see the filesystem.
     *
     * `isExternalStorageManager` is the honest question from API 30 on. Below
     * that the broad read permission still meant something, and the manifest
     * asks for it there.
     */
    fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    // ---------- saved folders ----------

    /**
     * Offers to keep the folder currently open.
     *
     * The folder is read once, here, and handed to the dialog. The dialog then
     * describes a fixed place rather than "wherever we are", which matters
     * because a listing can land while it is up.
     */
    fun requestAddShortcut() {
        val state = _state.value
        if (state.screen != Screen.BROWSE || state.path.isBlank()) return
        val serverId = (state.location as? Location.Server)?.id
        _state.value = state.copy(
            dialog = Dialog.AddShortcut(
                path = state.path,
                suggested = state.crumbs.lastOrNull()?.label?.takeIf { it.isNotBlank() }
                    ?: state.path.trimEnd('/').substringAfterLast('/').ifBlank { state.label },
                serverId = serverId,
                alreadySaved = app.favourites.contains(state.path, serverId),
                canPin = PinShortcuts.supported(app)
            )
        )
    }

    /**
     * Saves the folder to one home screen, the other, or both.
     *
     * The two destinations are independent and are reported independently. A
     * launcher can refuse a pinned shortcut while this app's own list takes it
     * happily, and saying "added" when half of it did not happen is how a
     * feature earns a reputation for not working.
     */
    fun addShortcut(label: String, toApp: Boolean, toLauncher: Boolean) {
        val request = _state.value.dialog as? Dialog.AddShortcut ?: return
        val name = label.trim().ifBlank { request.suggested }
        val favourite = Favourite(name, request.path, request.serverId)

        val saved = toApp && app.favourites.add(favourite)
        val pinned = toLauncher && PinShortcuts.request(app, favourite)

        val said = buildList {
            if (toApp) {
                add(
                    if (saved) "Saved $name to FileXplor's home screen."
                    else "$name was already on FileXplor's home screen."
                )
            }
            if (toLauncher) {
                // "Sent", not "added". Android shows its own dialog after this
                // and the user can still say no; there is no callback to learn
                // the answer from and no way to ask afterwards, so the app says
                // what it did rather than claiming an outcome it cannot see.
                add(
                    if (pinned) "Sent $name to the phone's home screen."
                    else "This launcher won't take pinned shortcuts."
                )
            }
        }

        _state.value = _state.value.copy(
            dialog = null,
            favourites = app.favourites.all(),
            notice = said.joinToString(" ").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Opens a saved folder.
     *
     * A folder on a server needs the server, and the server may have been
     * deleted since -- so it is looked up rather than assumed, and its absence
     * is explained instead of becoming a connection failure that would read as
     * the server being down.
     */
    fun openFavourite(favourite: Favourite) {
        val serverId = favourite.serverId
        if (serverId == null) {
            viewModelScope.launch {
                // Checked before the screen changes. A shortcut outlives the
                // folder it points at -- deleted, renamed, or on an SD card
                // that has since come out -- and openDevice commits to the
                // browse screen before it finds out, which would land the user
                // in an empty folder with a notice floating over it.
                val there = withContext(Dispatchers.IO) {
                    java.io.File(favourite.path).isDirectory
                }
                if (there) {
                    openDevice(favourite.path)
                    return@launch
                }
                // Without all-files access nearly every path answers "not a
                // directory", so the permission is the more likely truth and
                // the more useful thing to say.
                _state.value = _state.value.copy(
                    notice = if (!hasStoragePermission()) {
                        "FileXplor can't reach ${favourite.label} without access to your files."
                    } else {
                        "${favourite.label} isn't on this phone any more."
                    }
                )
            }
            return
        }
        viewModelScope.launch {
            val server = withContext(Dispatchers.IO) {
                app.servers.all().firstOrNull { it.id == serverId }
            }
            if (server == null) {
                _state.value = _state.value.copy(
                    notice = "${favourite.label} is on a server that isn't set up any more."
                )
                return@launch
            }
            openServer(server, startPath = favourite.path)
        }
    }

    /**
     * Pins an already-saved folder to the phone's home screen.
     *
     * The same request the add dialog makes, reachable from the row itself --
     * somebody who saved a folder last week and now wants it beside their apps
     * should not have to go and open the folder to ask.
     */
    fun pinFavourite(favourite: Favourite) {
        val pinned = PinShortcuts.request(app, favourite)
        _state.value = _state.value.copy(
            notice = if (pinned) "Sent ${favourite.label} to the phone's home screen."
            else "This launcher won't take pinned shortcuts."
        )
    }

    fun requestRenameFavourite(favourite: Favourite) {
        _state.value = _state.value.copy(dialog = Dialog.RenameFavourite(favourite))
    }

    /**
     * Renames the shortcut, and nothing else.
     *
     * Only the label in this app's own list changes: the folder keeps its name
     * on disk, and an icon already pinned to the phone's home screen keeps the
     * name it was pinned with, because the launcher owns that copy and there is
     * no supported way to reach back and edit it.
     */
    fun renameFavourite(favourite: Favourite, label: String) {
        val name = label.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(dialog = null, notice = "A shortcut needs a name.")
            return
        }
        app.favourites.rename(favourite.key, name)
        _state.value = _state.value.copy(dialog = null, favourites = app.favourites.all())
    }

    /**
     * Forgets a saved folder.
     *
     * The folder itself is untouched -- this list holds paths, never files --
     * and so is anything already pinned to the phone's home screen, which is
     * the launcher's to remove.
     */
    fun removeFavourite(favourite: Favourite) {
        app.favourites.remove(favourite.key)
        _state.value = _state.value.copy(
            favourites = app.favourites.all(),
            notice = "Removed ${favourite.label} from FileXplor's home screen."
        )
    }

    /** Shifts a saved folder up or down the list. Silent when it cannot move. */
    fun moveFavourite(favourite: Favourite, by: Int) {
        if (!app.favourites.move(favourite.key, by)) return
        _state.value = _state.value.copy(favourites = app.favourites.all())
    }

    // ---------- navigation ----------

    fun openDevice(path: String) {
        closeSource()
        source = app.local
        _state.value = _state.value.copy(
            screen = Screen.BROWSE,
            location = Location.Device,
            label = app.local.label,
            selected = emptySet(),
            query = ""
        )
        navigate(path, resetCrumbs = true)
    }

    /**
     * Connects to a server and shows its root.
     *
     * The connection is made on the IO dispatcher and failures land on the home
     * screen with the reason, rather than on a browse screen showing an empty
     * folder — "this server has nothing in it" and "this server refused the
     * password" must never look the same.
     */
    fun openServer(server: RemoteServer, startPath: String? = null) {
        closeSource()
        listJob?.cancel()
        val generation = ++listGeneration
        _state.value = _state.value.copy(loading = true, selected = emptySet(), query = "")
        listJob = viewModelScope.launch {
            // The connection and the first listing are made together, before
            // the screen changes. RemoteFileSource connects lazily — it has to,
            // so it can reconnect after an idle timeout — which means building
            // one proves nothing about whether the server is reachable. Opening
            // the browse screen first and discovering the password was wrong
            // afterwards would leave a refused server looking like an empty one.
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val full = app.servers.find(server.id) ?: server
                    val remote = RemoteFileSource(full) { app.connector.connect(full) }
                    // A saved folder deep inside the server opens there;
                    // everything else opens at the root. The folder that was
                    // actually asked for is listed here, before the screen
                    // changes, so a shortcut to one that has since been moved
                    // or renamed fails on the home screen with the reason --
                    // the same treatment a refused password gets, and for the
                    // same reason.
                    val target = startPath?.takeIf { it.isNotBlank() } ?: remote.root
                    val entries = remote.list(target)
                    OpenedServer(
                        remote,
                        target,
                        entries,
                        downloadedIn(Location.Server(server.id), entries)
                    )
                }
            }
            opened.onSuccess { result ->
                val remote = result.source
                source = remote
                _state.value = _state.value.copy(
                    screen = Screen.BROWSE,
                    location = Location.Server(server.id),
                    label = remote.label,
                    path = result.path,
                    entries = result.entries,
                    downloaded = result.downloaded,
                    // A shortcut is a starting point, not a trail: the crumbs
                    // begin where it opened, so Back leaves for the home screen
                    // rather than walking up through folders the user never
                    // came through. Exactly what a shortcut to a folder on the
                    // phone already does, and at the root this is the single
                    // crumb it always was.
                    crumbs = crumbsFor(remote, result.path, reset = true),
                    loading = false
                )
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    notice = error.message ?: "Couldn't connect to ${server.displayName}."
                )
            }
        }.clearsSpinnerOnCompletion(generation)
    }

    /**
     * Which of these entries already have a copy on the phone.
     *
     * A stat per entry, done here on IO alongside the listing that produced
     * them rather than by each row asking about itself. A row has no way to
     * notice a download finishing — nothing about the entry changes when the
     * bytes land — so a check made from the row runs once, before the file
     * exists, and never again. That is why the marker used to appear only after
     * a switch to grid and back: the switch throws the rows away and builds
     * them again, which runs the check a second time.
     *
     * Nothing to do on the phone, where every file is already the copy, so that
     * case returns before touching the disk at all.
     */
    private fun downloadedIn(location: Location, entries: List<FileItem>): Set<String> {
        if (location == Location.Device) return emptySet()
        return entries.filter { DownloadCache.isCached(app, location, it) }
            .mapTo(mutableSetOf()) { it.path }
    }

    /** Lists [path] and shows it. */
    fun navigate(path: String, resetCrumbs: Boolean = false) {
        val current = source ?: return
        listJob?.cancel()
        val generation = ++listGeneration
        val location = _state.value.location
        // Leaving for a different folder ends any refresh of this one, so the
        // held indicator cannot follow the user into the next listing.
        _state.value = _state.value.copy(
            loading = true,
            selected = emptySet(),
            refreshing = _state.value.refreshing && path == _state.value.path
        )
        listJob = viewModelScope.launch {
            val listed = withContext(Dispatchers.IO) {
                runCatching { current.list(path).let { it to downloadedIn(location, it) } }
            }
            listed.onSuccess { (entries, downloaded) ->
                _state.value = _state.value.copy(
                    path = path,
                    entries = entries,
                    downloaded = downloaded,
                    loading = false,
                    crumbs = crumbsFor(current, path, resetCrumbs)
                )
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    notice = error.message ?: "Couldn't open that folder."
                )
            }
        }.clearsSpinnerOnCompletion(generation)
    }

    /**
     * Guarantees the spinner stops when this job does, however it ended.
     *
     * Three separate paths used to leave `loading` true for good, and because
     * the pull-to-refresh indicator is driven by that same flag, the swirl
     * stayed on screen until the app was killed:
     *
     *  - the failure branch returned early on a cancellation without clearing
     *    it;
     *  - a job cancelled while suspended never reached its own clearing code at
     *    all, since `runCatching` sits inside `withContext` and cannot catch the
     *    coroutine's own cancellation;
     *  - going Home did not cancel the listing in flight or reset the flag.
     *
     * Clearing it in one place that runs on success, failure and cancellation
     * alike is the only version of this that does not need every future exit
     * path to remember. [generation] is what stops a stale job from switching
     * off the spinner belonging to the one that replaced it.
     */
    private fun Job.clearsSpinnerOnCompletion(generation: Int): Job = apply {
        invokeOnCompletion {
            if (generation == listGeneration) {
                _state.update { state ->
                    if (state.loading) state.copy(loading = false) else state
                }
            }
        }
    }

    /**
     * Records that the unlock prompt was satisfied.
     *
     * Held here rather than in the Activity so it survives a rotation. The
     * Activity is destroyed and rebuilt by a rotation, and re-prompting for
     * turning the phone sideways would be an unlock that punishes the wrong
     * thing.
     */
    fun markServersUnlocked() {
        _state.value = _state.value.copy(serversUnlocked = true)
    }

    /**
     * Says something in the snackbar.
     *
     * Used by the unlock prompt, which fails outside the view model and still
     * has to explain itself.
     */
    fun showNotice(message: String) {
        _state.value = _state.value.copy(notice = message)
    }

    /** Puts the gate back up — called when the app stops being visible. */
    fun lockServers() {
        if (_state.value.serversUnlocked) {
            _state.value = _state.value.copy(serversUnlocked = false)
        }
    }

    fun open(item: FileItem) {
        if (item.isDirectory) navigate(item.path) else _state.value =
            _state.value.copy(dialog = Dialog.Details(item))
    }

    /**
     * The same listing as [navigate], but flagged as one the user asked for.
     *
     * The flag is what the pull indicator watches, so it spins for this and for
     * nothing else.
     *
     * It is also held on screen for a moment after the listing is done, and
     * that is the whole point of this function existing rather than calling
     * navigate directly. Two reasons, one cosmetic and one not:
     *
     *  - A folder on the phone lists in about the time of one frame. Clearing
     *    the flag the instant it finished asked the indicator to retract while
     *    it was still animating out of the drag, and the two animations fought:
     *    sometimes it settled part-way and stayed there, which is the wheel
     *    that would not go away. Giving it a settled state to retract from
     *    removes the race rather than papering over it.
     *  - A refresh that finishes invisibly looks like a refresh that did not
     *    happen, and the next thing anybody does is pull again.
     *
     * Clearing it here rather than in [clearsSpinnerOnCompletion] also closes a
     * hole: [navigate] returns early when there is no source, and the flag set
     * before that call would have had nothing left to switch it off.
     */
    fun refresh() {
        val path = _state.value.path
        _state.value = _state.value.copy(refreshing = true)
        navigate(path)

        // Whatever navigate just started — or the already-finished job it left
        // in place, if it returned early.
        val listing = listJob
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val elapsed = measureTimeMillis { runCatching { listing?.join() } }
            if (elapsed < MIN_REFRESH_VISIBLE_MS) delay(MIN_REFRESH_VISIBLE_MS - elapsed)
            // update rather than a copy of the current value: this lands after
            // a delay, by which time anything else may have written state.
            _state.update { it.copy(refreshing = false) }
        }
    }

    /**
     * Up one level, or back to the home screen from the top.
     *
     * The root check uses the crumb trail rather than comparing the path to
     * "/": a server can be rooted several directories deep, and walking above
     * that base path is both useless and usually forbidden.
     */
    fun up(): Boolean {
        val current = source ?: return false
        val state = _state.value
        if (state.selecting) { clearSelection(); return true }
        if (state.atRoot) { goHome(); return true }
        navigate(current.parentOf(state.path))
        return true
    }

    fun goHome() {
        // Cancelling first, and bumping the generation, so a listing still in
        // flight cannot land on the home screen — or leave its spinner running
        // for the next folder that gets opened.
        listJob?.cancel()
        listGeneration++
        closeSource()
        _state.value = _state.value.copy(
            screen = Screen.HOME,
            entries = emptyList(),
            downloaded = emptySet(),
            selected = emptySet(),
            query = "",
            path = "",
            loading = false,
            refreshing = false
        )
        refreshHome()
    }

    private fun crumbsFor(source: FileSource, path: String, reset: Boolean): List<Crumb> {
        val existing = _state.value.crumbs
        if (!reset) {
            val at = existing.indexOfFirst { it.path == path }
            // Walking back up to a folder already on the trail truncates it
            // rather than appending a second copy of the same place.
            if (at >= 0) return existing.take(at + 1)
            if (existing.isNotEmpty()) {
                return existing + Crumb(path.substringAfterLast('/').ifBlank { path }, path)
            }
        }
        // The first crumb is named after the folder actually opened, not after
        // the source. Opening Pictures from the home screen and being told you
        // are in "This phone" is the app answering a question nobody asked —
        // the source name is only the right label at the source's own root.
        val name = path.trimEnd('/').substringAfterLast('/')
        val label = if (path.trimEnd('/') == source.root.trimEnd('/') || name.isBlank()) {
            source.label
        } else {
            name
        }
        return listOf(Crumb(label, path))
    }

    // ---------- selection ----------

    fun toggle(item: FileItem) {
        val selected = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (item.path in selected) selected - item.path else selected + item.path
        )
    }

    fun selectAll() {
        _state.value = _state.value.copy(selected = _state.value.visible.map { it.path }.toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    // ---------- clipboard ----------

    fun cut() = holdSelection(move = true)

    fun copy() = holdSelection(move = false)

    /**
     * Puts the selection on the clipboard.
     *
     * Deliberately silent. The bar that appears along the bottom already says
     * "3 items to move" and carries the Paste button, and a snackbar saying the
     * same thing in different words was posted directly over it -- so the one
     * thing the user wanted to tap next was covered by an announcement that it
     * was ready to be tapped.
     */
    private fun holdSelection(move: Boolean) {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        _state.value = _state.value.copy(
            clipboard = Clipboard(items, _state.value.location, move),
            selected = emptySet()
        )
    }

    fun clearClipboard() {
        _state.value = _state.value.copy(clipboard = null)
    }

    /**
     * Writes the clipboard into the current folder.
     *
     * Works in all four directions — phone to phone, phone to server, server to
     * phone, server to server — because both ends are only a [FileSource]. The
     * source end is opened fresh when it is not the folder being looked at,
     * since the app holds one connection at a time.
     */
    fun paste() {
        val clip = _state.value.clipboard ?: return
        val destination = source ?: return
        val destinationPath = _state.value.path

        // One job at a time.
        //
        // This used to cancel whatever was running and start anyway, so a
        // second tap on Paste -- or a tap on Paste while a delete was going --
        // abandoned the first job half done. A copy stopped half way leaves
        // files that are the right name and the wrong length, which is the
        // worst kind of leftover because nothing about it looks wrong.
        //
        // Asked of the coroutine rather than of the progress bar. Walking a
        // large tree before the first byte moves can take a while, and during
        // that there is no progress to show -- so a check against the bar alone
        // let a second paste through exactly when the first was busiest.
        if (busy()) return

        // Where the files came from, checked against where they are going.
        //
        // Only meaningful when both ends are the same place; a folder on the
        // phone and a folder on a server can share a path and have nothing to
        // do with each other.
        if (clip.from == _state.value.location) {
            // A folder cannot be pasted inside itself.
            //
            // Left alone this was destructive rather than merely odd. The copy
            // wrote the tree into a corner of itself, and the move then deleted
            // the original -- and because the delete re-listed the source after
            // the copy, the copy was inside what it deleted. Everything went.
            val nested = clip.items.firstOrNull {
                it.isDirectory && isWithin(destinationPath, it.path)
            }
            if (nested != null) {
                _state.value = _state.value.copy(
                    notice = "Can't put ${nested.name} inside itself."
                )
                return
            }

            // A move into the folder the files are already in has nothing to
            // do. It used to copy them under a "(2)" name and delete the
            // originals, which is a rename nobody asked for.
            if (clip.move && clip.items.all { samePath(parentPath(it.path), destinationPath) }) {
                _state.value = _state.value.copy(
                    clipboard = null,
                    notice = "Those are already here."
                )
                return
            }
        }

        val title = if (clip.move) "Moving" else "Copying"

        // On screen the moment Paste is tapped, not once the first byte moves.
        //
        // Everything below this line takes time before there is anything to
        // count: opening a second connection, then walking the whole tree to
        // work out what the job even is -- a listing per folder, and on a
        // server a round trip each. The bar used to wait for the first progress
        // callback, so on anything larger than a handful of files the answer to
        // tapping Paste was nothing at all, for long enough to tap it again.
        _state.value = _state.value.copy(job = RunningJob(title, Progress.Preparing))

        operationJob = viewModelScope.launch {
            val from = withContext(Dispatchers.IO) { runCatching { sourceFor(clip.from) } }
                .getOrNull()
            if (from == null) {
                _state.value = _state.value.copy(
                    job = null,
                    notice = "Couldn't reach where those files came from."
                )
                return@launch
            }

            try {
                val plan = withContext(Dispatchers.IO) { app.operations.plan(from, clip.items) }
                if (plan.isEmpty) {
                    _state.value = _state.value.copy(job = null, notice = "Nothing to paste.")
                    return@launch
                }

                // What is already at the destination, so nothing is written
                // over. Pasting into the folder the files came from would
                // otherwise overwrite them with themselves — which for a move
                // means deleting them afterwards.
                //
                // A destination that cannot be listed stops the paste rather
                // than being treated as empty. Renaming around collisions is
                // the only thing standing between a paste and silently
                // replacing somebody's file, and it can only do that against a
                // real listing; assuming "empty" when the answer is unknown
                // turns an unreadable folder into an overwrite.
                val existing = withContext(Dispatchers.IO) {
                    runCatching { destination.list(destinationPath).map { it.name }.toSet() }
                }
                if (existing.isFailure) {
                    _state.value = _state.value.copy(
                        job = null,
                        notice = "Couldn't read this folder, so nothing was pasted " +
                            "— it might have replaced something."
                    )
                    return@launch
                }
                val renamed = renameCollisions(plan, existing.getOrDefault(emptySet()))

                // Refused before it starts when it cannot fit. Left to find
                // out for itself, a copy of a large film filled the phone for
                // several minutes, failed at the last few hundred megabytes and
                // deleted what it had written -- the whole wait for nothing,
                // and a phone briefly at 0 bytes free, which other apps
                // handle badly. The clipboard is kept for after a tidy-up.
                val free = withContext(Dispatchers.IO) {
                    runCatching { destination.freeSpace(destinationPath) }.getOrNull()
                }
                if (free != null && plan.totalBytes > free) {
                    _state.value = _state.value.copy(
                        job = null,
                        notice = "Not enough space. This needs ${formatBytes(plan.totalBytes)} " +
                            "and ${formatBytes(free)} is free here."
                    )
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    app.operations.copy(from, destination, destinationPath, renamed) { progress ->
                        _state.value = _state.value.copy(job = RunningJob(title, progress))
                    }
                }

                // Originals go only when everything arrived.
                //
                // "Everything" includes the folders the planner could not read:
                // their contents were never copied, so deleting them would be
                // deleting the only copy. That case used to pass this test,
                // because an unreadable folder produced no failure to count.
                val complete = result.failed.isEmpty() && plan.unreadable.isEmpty()
                var removal: com.filexplor.app.data.OperationResult? = null

                if (clip.move && complete) {
                    // The plan from *before* the copy, not a fresh walk.
                    //
                    // Re-walking asks the source what is in it now, which after
                    // a copy into a subfolder of itself includes the copy. It
                    // also sweeps up anything that arrived in the folder while
                    // the copy was running -- deleting files that were never
                    // copied at all.
                    removal = withContext(Dispatchers.IO) {
                        app.operations.delete(from, plan, clip.items) { progress ->
                            _state.value = _state.value.copy(
                                job = RunningJob("Removing originals", progress)
                            )
                        }
                    }
                }

                // The clipboard is emptied once its contents have landed
                // somewhere, whether that was a move or a copy. Leaving it full
                // after a copy left the paste bar across the bottom of the
                // screen with no sign that anything had happened, and nothing
                // to do about it but press Cancel.
                //
                // A paste that achieved nothing keeps it, so the answer to
                // "that failed" is to try somewhere else rather than to select
                // the files again.
                noteFailures(title, result.failed)
                removal?.let { noteFailures("Removing originals", it.failed) }
                // Not a failure any result carries: a folder the planner could
                // not read contributed nothing to the job, so nothing inside it
                // ever appeared as a file that failed. Naming it here is the
                // only place the user finds out which folder went unseen.
                plan.unreadable.forEach {
                    noteFailure(title, it, "Couldn't be read, so nothing inside it was copied.")
                }

                val landed = result.succeeded > 0 || result.failed.isEmpty()

                _state.value = _state.value.copy(
                    job = null,
                    clipboard = if (landed) null else clip,
                    notice = report(clip.move, result, removal, plan.unreadable)
                )
                refresh()
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(job = null, notice = "Stopped.")
                refresh()
                throw e
            } catch (e: Exception) {
                // Anything that got past the per-file handling: the destination
                // going away mid-copy, a connection dropping, a disk filling.
                //
                // Without this the coroutine died with the progress bar still
                // on screen and no message -- a job that had plainly stopped
                // and still said it was running, with a Stop button that did
                // nothing.
                noteFailure(title, destinationPath, e.message ?: "That didn't work.")
                _state.value = _state.value.copy(
                    job = null,
                    notice = e.message ?: "That didn't work."
                )
                refresh()
            } finally {
                // The temporary connection, if one was opened for the other end.
                //
                // Guarded, because closing a socket that has already failed is
                // itself a common way to throw -- and a throw here would replace
                // whatever really went wrong, from inside a finally, with no
                // handler left above it.
                if (from !== destination && from !== app.local) {
                    withContext(Dispatchers.IO) { runCatching { from.close() } }
                }
            }
        }
    }

    /**
     * What to say once a paste has finished.
     *
     * Separate from [summarise] because a move is two operations and can half
     * succeed in a way a copy cannot: the files can arrive and the originals
     * stay put. Saying "moved" there would be a lie the user only discovers by
     * going back and finding everything still where it was.
     */
    private fun report(
        move: Boolean,
        copied: com.filexplor.app.data.OperationResult,
        removal: com.filexplor.app.data.OperationResult?,
        unreadable: List<String>
    ): String {
        val base = summarise(copied, if (move) "moved" else "copied")
        val notes = mutableListOf<String>()

        if (unreadable.isNotEmpty()) {
            val name = unreadable.first().substringAfterLast('/')
            notes += if (unreadable.size == 1) {
                "$name could not be read, so nothing inside it was included"
            } else {
                "${unreadable.size} folders could not be read, so nothing inside them was included"
            }
        }
        if (move && copied.failed.isEmpty() && unreadable.isEmpty() && removal == null) {
            notes += "the originals were left in place"
        }
        if (removal != null && removal.failed.isNotEmpty()) {
            notes += "${removal.failed.size} original(s) could not be removed"
        }
        if (move && (copied.failed.isNotEmpty() || unreadable.isNotEmpty())) {
            notes += "the originals were kept"
        }

        return if (notes.isEmpty()) base else "$base ${notes.joinToString("; ")}."
    }

    /**
     * Gives every top-level entry a name not already taken at the destination.
     *
     * Applied to the plan rather than at write time so that a folder and
     * everything inside it move together: renaming `photos` to `photos (2)`
     * has to rename the path of every file under it too, or half the copy lands
     * in the original folder.
     */
    private fun renameCollisions(plan: FileOperations.Plan, existing: Set<String>): FileOperations.Plan {
        if (existing.isEmpty()) return plan
        val taken = existing.toMutableSet()
        val mapping = mutableMapOf<String, String>()

        (plan.directories.map { it.substringBefore('/') } +
            plan.files.map { it.relativePath.substringBefore('/') })
            .distinct()
            .forEach { head ->
                val fresh = uniqueName(head, taken)
                taken += fresh
                if (fresh != head) mapping[head] = fresh
            }
        if (mapping.isEmpty()) return plan

        fun remap(relative: String): String {
            val head = relative.substringBefore('/')
            val replacement = mapping[head] ?: return relative
            val rest = relative.substringAfter('/', "")
            return if (rest.isEmpty()) replacement else "$replacement/$rest"
        }

        return plan.copy(
            files = plan.files.map { it.copy(relativePath = remap(it.relativePath)) },
            directories = plan.directories.map(::remap)
        )
    }

    /**
     * A source for [location] that a copy can read from while the open one is
     * being written to.
     *
     * The phone's source is shared, because two handles on a filesystem are
     * independent. A server's never is, and this used to hand back the
     * connection already on screen whenever the clipboard came from the folder
     * being pasted into. A copy reads from one end and writes to the other at
     * the same moment, so on FTP that was a RETR and a STOR down one control
     * connection: not a slow transfer, a protocol error. Copying a file from
     * one folder of a server to another failed every time, on the protocol
     * least able to say why.
     *
     * So a server always gets its own connection here, even when it is the
     * server already open. paste() closes it in its `finally`; [app.local] is
     * shared and is left alone.
     */
    private fun sourceFor(location: Location): FileSource = when (location) {
        Location.Device -> app.local
        is Location.Server -> {
            val server = app.servers.find(location.id)
                ?: throw IllegalStateException("That server is no longer set up.")
            RemoteFileSource(server) { app.connector.connect(server) }
        }
    }

    // ---------- making and unmaking ----------

    fun requestNewFolder() {
        _state.value = _state.value.copy(dialog = Dialog.NewFolder)
    }

    /**
     * Makes a folder here, or says why not.
     *
     * The existence check is for a different reason than the one in
     * [createFile], and both reasons are worth having. A folder cannot be
     * truncated by being created over, so nothing is destroyed here -- the
     * problem is silence. `makeDirectory` returns quietly when the directory
     * is already there, so typing a name that is already taken produced no
     * error, no new folder, and no explanation: the dialog closed and the
     * screen looked exactly as it had. That reads as the app ignoring the tap.
     *
     * A file sitting at the name is the other half. That one does fail, but
     * only as "Couldn't create X" from deep inside a driver, which says
     * nothing about the actual problem being a name that is already in use.
     * One check ahead of the call answers both cases in the same words.
     */
    fun createFolder(name: String) {
        val current = source ?: return
        val cleaned = name.trim()
        nameProblem(cleaned)?.let { problem ->
            _state.value = _state.value.copy(dialog = null, notice = problem)
            return
        }
        _state.value = _state.value.copy(dialog = null)
        val folder = _state.value.path
        viewModelScope.launch {
            val path = joinPath(folder, cleaned)
            val made = withContext(Dispatchers.IO) {
                runCatching {
                    if (current.exists(path)) {
                        throw java.io.IOException("$cleaned is already here.")
                    }
                    current.makeDirectory(path)
                }
            }

            made.exceptionOrNull()?.let { error ->
                // A cancelled scope is not a failed mkdir. runCatching catches
                // CancellationException like anything else, so without this the
                // app would explain itself to somebody who has already left.
                if (error is kotlinx.coroutines.CancellationException) throw error
                // No refresh on the way out. The folder was not created, so
                // nothing in the listing changed, and re-reading a directory
                // over the network to confirm that is a round trip spent
                // learning nothing.
                _state.value = _state.value.copy(
                    notice = error.message ?: "Couldn't create that folder."
                )
                return@launch
            }

            // Added to the listing rather than re-listing to find a folder
            // this function just made -- the same reasoning as createFile, and
            // the same two calls to the source instead of three.
            val item = FileItem(
                name = cleaned,
                path = path,
                isDirectory = true,
                // Directories carry -1 everywhere else in the app, meaning
                // "not a size". Zero would render as an empty folder, which is
                // a claim about its contents rather than about its size.
                size = -1L,
                lastModified = System.currentTimeMillis()
            )
            _state.update { state ->
                if (state.path == folder && state.entries.none { it.path == path }) {
                    state.copy(entries = state.entries + item)
                } else {
                    state
                }
            }
        }
    }

    fun requestNewFile() {
        _state.value = _state.value.copy(dialog = Dialog.NewFile)
    }

    /**
     * Makes an empty file here and opens it in the editor.
     *
     * Two calls to the source, and that is the whole design of this function.
     * Both of them have to happen; nothing else here talks to the server at
     * all.
     * On the phone the difference is invisible; on a server it is the
     * difference between instant and a wait long enough to wonder whether the
     * tap registered, because every one of these is a round trip and some are
     * far worse than that.
     *
     * The first version asked for five. It checked [FileSource.exists], wrote
     * the file, called [FileSource.stat] to get an item back, refreshed the
     * folder, and then opened the editor -- which read the file. On a remote
     * source `stat` is not a cheap call at all: there is no per-path stat in
     * the interface, so it lists the entire parent directory and scans it for
     * the name. So that was *two* full directory listings of the same folder,
     * one of them thrown away, plus a download of a file already known to be
     * empty. On FTP each of those opens its own data connection, and a STOR
     * followed straight down the same control connection by a LIST and then a
     * RETR is also the sequence most likely to leave the connection wedged.
     *
     * Everything those three calls went to find out is already known here. The
     * item is built rather than fetched -- the path, the name and the size of
     * a file this function just created are not facts the server can be more
     * authoritative about. The editor is opened on an empty string rather than
     * by reading, for the same reason. What is left is the existence check,
     * which has to talk to the server, and the refresh, which is what puts the
     * new file on the screen behind the editor.
     *
     * The existence check is the part that matters, and it is not the same
     * check [createFolder] gets for free. `makeDirectory` on a folder that is
     * already there does nothing; `write` on a file that is already there
     * opens it for writing and truncates it, so "New file" with a name that
     * happens to match something in this folder would destroy it in the time
     * it takes to open the handle -- and report success, because from the
     * app's side the write worked perfectly.
     *
     * It is a check and not a guarantee: between asking and writing, someone
     * else can create the same name on a share. Nothing in the three protocols
     * offers a create-only-if-absent write, so the window cannot be closed
     * from here, only made small. Worth having anyway, because the case it
     * does catch -- a name the user forgot was already in the folder they are
     * looking at -- is the one that actually happens.
     */
    fun createFile(name: String) {
        val current = source ?: return
        val cleaned = name.trim()
        nameProblem(cleaned)?.let { problem ->
            _state.value = _state.value.copy(dialog = null, notice = problem)
            return
        }
        _state.value = _state.value.copy(dialog = null)
        val folder = _state.value.path
        viewModelScope.launch {
            val path = joinPath(folder, cleaned)
            val made = withContext(Dispatchers.IO) {
                runCatching {
                    if (current.exists(path)) {
                        throw java.io.IOException("$cleaned is already here.")
                    }
                    current.write(path, ByteArray(0).inputStream(), 0L)
                }
            }
            made.exceptionOrNull()?.let { error ->
                // As in createFolder: a cancelled scope is not a failed write.
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = _state.value.copy(
                    notice = error.message ?: "Couldn't create that file."
                )
                return@launch
            }

            // Described rather than looked up. Everything here is known: it is
            // the path just written, it is not a directory, and it is empty.
            val item = FileItem(
                name = cleaned,
                path = path,
                isDirectory = false,
                size = 0L,
                lastModified = System.currentTimeMillis()
            )

            _state.update { state ->
                // Added to the listing rather than re-listing the folder to
                // discover a file this function just put there. refresh() is a
                // whole directory read, and it also holds its spinner for a
                // minimum time on purpose -- both right for a refresh somebody
                // asked for, both pure cost here.
                //
                // Only when the folder underneath is still the one written to.
                // A tap on a breadcrumb while a slow server was working would
                // otherwise drop this file into a listing it is not in.
                val listing =
                    if (state.path == folder && state.entries.none { it.path == path }) {
                        state.entries + item
                    } else {
                        state.entries
                    }
                // Opened on an empty string rather than through openText,
                // which would read back the nothing that was just written.
                // Never read-only: zero bytes cannot be too big to edit and
                // cannot sniff as binary.
                state.copy(
                    entries = listing,
                    screen = Screen.EDIT_TEXT,
                    editor = EditorState(item = item, text = "", original = ""),
                    loading = false
                )
            }
        }
    }

    fun requestRename(item: FileItem) {
        _state.value = _state.value.copy(dialog = Dialog.Rename(item))
    }

    fun rename(item: FileItem, newName: String) {
        val current = source ?: return
        val cleaned = newName.trim()
        if (cleaned == item.name) {
            _state.value = _state.value.copy(dialog = null)
            return
        }
        nameProblem(cleaned)?.let { problem ->
            _state.value = _state.value.copy(dialog = null, notice = problem)
            return
        }
        _state.value = _state.value.copy(dialog = null)
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) {
                runCatching {
                    // Asked here, of a fresh listing, because the servers do
                    // not ask at all. SMB renames with replace-if-exists and
                    // most FTP servers overwrite on RNTO, so renaming a.txt to
                    // the name of a file already there deleted that file
                    // without a word. Compared ignoring case for the same
                    // reason pasting is (see uniqueName); the file being
                    // renamed does not clash with itself, which is what lets
                    // notes.txt become Notes.txt.
                    val here = current.list(parentPath(item.path)).map { it.name }
                    if (here.any { it != item.name && it.equals(cleaned, ignoreCase = true) }) {
                        throw java.io.IOException("$cleaned already exists here.")
                    }
                    current.rename(item.path, cleaned)
                }
            }
            renamed.onFailure {
                _state.value = _state.value.copy(notice = it.message ?: "Couldn't rename that.")
            }
            refresh()
        }
    }

    fun requestDelete() {
        val items = _state.value.selectedItems
        if (items.isEmpty()) return
        _state.value = _state.value.copy(dialog = Dialog.ConfirmDelete(items))
    }

    fun requestDeleteOne(item: FileItem) {
        _state.value = _state.value.copy(dialog = Dialog.ConfirmDelete(listOf(item)))
    }

    fun confirmDelete() {
        val pending = (_state.value.dialog as? Dialog.ConfirmDelete)?.items ?: return
        val current = source ?: return
        _state.value = _state.value.copy(dialog = null, selected = emptySet())
        if (busy()) return

        // Up front, for the same reason paste does it: a delete plans the tree
        // before it removes anything, and that walk is the slow part.
        _state.value = _state.value.copy(job = RunningJob("Deleting", Progress.Preparing))

        operationJob = viewModelScope.launch {
            try {
                val plan = withContext(Dispatchers.IO) { app.operations.plan(current, pending) }
                val result = withContext(Dispatchers.IO) {
                    app.operations.delete(current, plan, pending) { progress ->
                        _state.value = _state.value.copy(job = RunningJob("Deleting", progress))
                    }
                }
                noteFailures("Deleting", result.failed)
                _state.value = _state.value.copy(
                    job = null,
                    notice = summarise(result, "deleted")
                )
                refresh()
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(job = null, notice = "Stopped.")
                refresh()
                throw e
            } catch (e: Exception) {
                // The same net paste has. delete() records a per-file failure
                // and carries on, so what reaches here is the whole operation
                // coming apart -- the connection dropping during the walk, the
                // source going away underneath it. Without this the coroutine
                // died with the progress bar still on screen and a Stop button
                // that had nothing left to stop.
                _state.value = _state.value.copy(
                    job = null,
                    notice = e.message ?: "That didn't work."
                )
                refresh()
            }
        }
    }

    /**
     * Fetches a remote file into the cache so something else can open it.
     *
     * Android hands a file to another app as a `content://` URI backed by a
     * real path, and a file on a server has neither. So it comes down first,
     * into the app's own cache, and what is opened is that copy.
     *
     * Worth being plain about what this is not: editing the copy does not write
     * back to the server. A file manager that silently pretended otherwise
     * would be the one thing here that loses somebody's work.
     */
    fun openRemote(item: FileItem, then: (java.io.File) -> Unit) {
        val current = source ?: return
        if (item.isDirectory) return

        // Queued behind whatever is running, not on top of it.
        //
        // This used to cancel the current job and start regardless, which is
        // fine when that job was another download and quietly destructive when
        // it was a copy: tapping a file to look at it while a move was running
        // abandoned the move half done, and the originals had not been deleted
        // yet so nothing looked wrong until later. The app has one connection
        // and one progress bar; refusing is the honest answer, and the Stop
        // button on the bar is right there for anyone who meant it.
        if (busy()) return

        // Before the cache lookup, so a file that has to come down says so
        // immediately. A file already cached clears it again a moment later
        // without ever drawing.
        _state.value = _state.value.copy(job = RunningJob("Downloading", Progress.Preparing))

        operationJob = viewModelScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    // Already here from a previous open, and the same file:
                    // DownloadCache keys on size and timestamp as well as path,
                    // so this can only match the bytes that are on the server
                    // now. Fetching a 40 MB APK twice to open it twice is the
                    // thing the cache exists to stop.
                    if (DownloadCache.isCached(app, _state.value.location, item)) {
                        return@withContext DownloadCache.fileFor(app, _state.value.location, item)
                    }
                    val file = DownloadCache.prepare(app, _state.value.location, item)

                    // As for a paste: said before the wait, not after it.
                    val free = file.parentFile?.let { app.local.freeSpace(it.path) }
                    if (free != null && item.size > free) {
                        throw java.io.IOException(
                            "Not enough space to open ${item.name}. It needs " +
                                "${formatBytes(item.size)} and ${formatBytes(free)} is free."
                        )
                    }

                    // The same second chance a copy gets. A download is a
                    // transfer like any other, and losing a 40 MB APK at 90%
                    // to a wifi handover -- then being told only that it could
                    // not be downloaded -- is the version of this that sends
                    // people back to tap the file again.
                    var attempt = 0
                    while (true) {
                        attempt++
                        coroutineContext.ensureActive()
                        val pulled = runCatching { pull(current, item, file) }
                        if (pulled.isSuccess) break

                        val error = pulled.exceptionOrNull()
                        if (error is CancellationException) throw error

                        // The partial copy goes before every attempt, so a
                        // retry writes onto nothing and isCached can never
                        // mistake a stub for the whole file.
                        runCatching { file.delete() }

                        val again = attempt < TRANSFER_ATTEMPTS &&
                            error != null &&
                            current.isTransient(error)
                        if (!again) throw error ?: java.io.IOException("Couldn't download ${item.name}.")

                        _state.value = _state.value.copy(
                            job = RunningJob("Reconnecting", Progress.Preparing)
                        )
                        delay(RETRY_BACKOFF_MS * attempt)
                    }
                    file
                }
                // Marked the moment the bytes are down, so the row the user
                // just tapped wears the badge without waiting for the folder to
                // be listed again.
                _state.value = _state.value.copy(
                    job = null,
                    downloaded = _state.value.downloaded + item.path
                )
                then(target)
            } catch (e: CancellationException) {
                // The half-written copy goes with it. isCached checks the
                // length so a stub would not be mistaken for the real thing,
                // but leaving one means the cache reports space held by a file
                // that can never be used.
                discardDownload(item)
                _state.value = _state.value.copy(job = null, notice = "Download stopped.")
                throw e
            } catch (e: Exception) {
                discardDownload(item)
                noteFailure(
                    "Downloading",
                    item.path,
                    e.message ?: "Couldn't download ${item.name}."
                )
                _state.value = _state.value.copy(
                    job = null,
                    notice = e.message ?: "Couldn't download ${item.name}."
                )
            }
        }
    }

    /**
     * One attempt at bringing [item] down into [file].
     *
     * Split out so the retry above has something to call twice. It starts from
     * nothing every time -- none of the three protocols offers a resume this
     * app could trust, and a range request the server quietly ignored would
     * append the whole file to the half already there.
     */
    private suspend fun pull(source: FileSource, item: FileItem, file: java.io.File) {
        var copied = 0L
        var reportedAt = 0L
        source.openRead(item.path).use { input ->
            java.io.FileOutputStream(file).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    // Throttled. This used to publish state on every buffer,
                    // which at 8 KB a time meant thousands of recompositions
                    // for one file and a progress bar that cost more than the
                    // download.
                    val now = SystemClock.elapsedRealtime()
                    if (now - reportedAt >= DOWNLOAD_PROGRESS_MS) {
                        reportedAt = now
                        _state.value = _state.value.copy(
                            job = RunningJob(
                                "Downloading",
                                Progress(0, 1, copied, item.size, item.name)
                            )
                        )
                    }
                }
            }
        }
        // Short means the connection ended, not the file. Opening it anyway
        // hands a viewer a truncated video or a corrupt archive; thrown
        // instead, the retry above fetches it again.
        if (item.size >= 0L && copied < item.size) {
            throw com.filexplor.app.data.IncompleteTransferException(
                "Only part of ${item.name} arrived."
            )
        }
    }

    fun cancelJob() {
        operationJob?.cancel()
    }

    /** Removes the partial copy left by a download that did not finish. */
    private fun discardDownload(item: FileItem) {
        runCatching {
            DownloadCache.fileFor(app, _state.value.location, item).delete()
        }
    }

    /**
     * Whether something is already running, saying so if it is.
     *
     * The app holds one connection and one progress bar, so two file operations
     * at once cannot be shown and, on a server, cannot even be sent. Refusing
     * is the honest answer; the alternative the code used to take -- cancelling
     * the first and starting the second -- threw away work the user had asked
     * for and was watching.
     */
    private fun busy(): Boolean {
        if (operationJob?.isActive != true) return false
        _state.value = _state.value.copy(notice = "Wait for the current job to finish.")
        return true
    }

    private fun summarise(result: com.filexplor.app.data.OperationResult, verb: String): String {
        val n = result.succeeded
        val noun = if (n == 1) "item" else "items"
        return when {
            result.failed.isEmpty() -> "$n $noun $verb."
            n == 0 -> "Nothing was $verb. ${result.failed.first().reason}"
            else -> "$n $noun $verb, ${result.failed.size} failed."
        }
    }

    // ---------- servers ----------

    fun requestAddServer() {
        _state.value = _state.value.copy(dialog = Dialog.ChooseProtocol)
    }

    fun chooseProtocol(protocol: RemoteProtocol) {
        _state.value = _state.value.copy(dialog = Dialog.ServerForm(null, protocol))
    }

    fun requestEditServer(server: RemoteServer) {
        viewModelScope.launch {
            val full = withContext(Dispatchers.IO) { app.servers.find(server.id) } ?: server
            _state.value = _state.value.copy(dialog = Dialog.ServerForm(full, full.protocol))
        }
    }

    fun saveServer(server: RemoteServer) {
        _state.value = _state.value.copy(dialog = null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.servers.save(server) }
            refreshHome()
        }
    }

    fun requestDeleteServer(server: RemoteServer) {
        _state.value = _state.value.copy(dialog = Dialog.ConfirmDeleteServer(server))
    }

    fun confirmDeleteServer() {
        val server = (_state.value.dialog as? Dialog.ConfirmDeleteServer)?.server ?: return
        _state.value = _state.value.copy(dialog = null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.servers.delete(server.id) }
            refreshHome()
        }
    }

    // ---------- presentation ----------

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun setViewMode(mode: ViewMode) {
        app.prefs.viewMode = mode
        _state.value = _state.value.copy(viewMode = mode, dialog = null)
    }

    fun setSortOrder(order: SortOrder) {
        app.prefs.sortOrder = order
        _state.value = _state.value.copy(sortOrder = order, dialog = null)
    }

    fun setShowHidden(show: Boolean) {
        app.prefs.showHidden = show
        _state.value = _state.value.copy(showHidden = show)
    }

    fun setThemeMode(mode: ThemeMode) {
        app.prefs.themeMode = mode
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setThemePalette(palette: ThemePalette) {
        app.prefs.themePalette = palette
        _state.value = _state.value.copy(themePalette = palette)
    }

    // ---------- the transfer log ----------

    /**
     * Writes down the files an operation could not manage.
     *
     * Called wherever a result carries failures rather than at one choke
     * point, because the two halves of a move fail for different reasons and
     * "Moving" and "Removing originals" are worth telling apart when reading
     * it back a day later.
     */
    private fun noteFailures(
        operation: String,
        failures: List<com.filexplor.app.data.OperationResult.Failure>
    ) {
        if (failures.isEmpty()) return
        app.log.record(operation, failures)
        _state.value = _state.value.copy(logCount = app.log.count())
    }

    private fun noteFailure(operation: String, path: String, reason: String) {
        app.log.record(operation, path, reason)
        _state.value = _state.value.copy(logCount = app.log.count())
    }

    fun showTransferLog() {
        _state.value = _state.value.copy(
            dialog = Dialog.TransferLog(app.log.all()),
            logCount = app.log.count()
        )
    }

    /**
     * Empties it, and leaves the dialog open on the empty list.
     *
     * Closing instead would be the button disappearing along with the thing it
     * acted on, which reads as a dismissal rather than as "done".
     */
    fun clearTransferLog() {
        app.log.clear()
        _state.value = _state.value.copy(dialog = Dialog.TransferLog(emptyList()), logCount = 0)
    }

    fun showDialog(dialog: Dialog) {
        if (dialog !is Dialog.Details) stopCounting()
        _state.value = _state.value.copy(dialog = dialog)
    }

    fun dismissDialog() {
        stopCounting()
        _state.value = _state.value.copy(dialog = null)
    }

    /** The folder count behind an open Details panel, if one is running. */
    private var countingJob: Job? = null

    /**
     * The details panel, with a folder's contents counted while it is open.
     *
     * A folder's panel used to say only that it was a folder and when it was
     * last changed -- nothing about how much was in it, which is the question
     * someone opening it usually has before copying, moving or deleting it.
     *
     * Counted on a connection of its own for a server, like the far end of a
     * paste: the one the browser holds is not safe to share between two
     * threads, and a count can outlast the panel by one listing. Stopped when
     * the panel closes.
     */
    fun showDetails(item: FileItem) {
        stopCounting()
        _state.value = _state.value.copy(dialog = Dialog.Details(item))
        if (!item.isDirectory) return

        val location = _state.value.location
        countingJob = viewModelScope.launch {
            val counter = withContext(Dispatchers.IO) { runCatching { sourceFor(location) } }
                .getOrNull() ?: return@launch
            try {
                val total = withContext(Dispatchers.IO) {
                    com.filexplor.app.data.summariseFolder(counter, item.path) { running ->
                        showSummary(item, running)
                    }
                }
                showSummary(item, total)
            } finally {
                if (counter !== app.local) {
                    withContext(Dispatchers.IO) { runCatching { counter.close() } }
                }
            }
        }
    }

    /** Updates the panel, if it is still the one for [item]. */
    private fun showSummary(item: FileItem, summary: com.filexplor.app.data.FolderSummary) {
        _state.update { current ->
            val open = current.dialog as? Dialog.Details ?: return@update current
            if (open.item.path != item.path) return@update current
            current.copy(dialog = open.copy(summary = summary))
        }
    }

    private fun stopCounting() {
        countingJob?.cancel()
        countingJob = null
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun notify(message: String) {
        _state.value = _state.value.copy(notice = message)
    }

    // ---------- viewing and editing in the app ----------

    /**
     * What FileXplor opens itself, and what it hands to another app.
     *
     * The line is drawn at what the app can do better than a hand-off. A
     * picture and a text file are both instant here and a trip through a
     * chooser there; a video or a song is the opposite, because the phone
     * already has a player that does seeking, codecs and a lock-screen control
     * and this app never will.
     */
    fun routeOpen(item: FileItem, external: (FileItem) -> Unit) {
        when (item.kind) {
            FileKind.IMAGE -> openImage(item)
            FileKind.TEXT, FileKind.CODE -> openText(item)
            FileKind.PDF -> openPdf(item)
            else -> external(item)
        }
    }

    /**
     * Opens the picture viewer on [item], with the folder's other pictures
     * either side of it.
     */
    fun openImage(item: FileItem) {
        val pictures = _state.value.visible.filter { it.kind == FileKind.IMAGE }
        val at = pictures.indexOfFirst { it.path == item.path }
        if (at < 0) return
        _state.value = _state.value.copy(
            screen = Screen.VIEW_IMAGE,
            viewer = ViewerState(pictures, at)
        )
    }

    fun viewerPage(index: Int) {
        val viewer = _state.value.viewer ?: return
        if (index == viewer.index || index !in viewer.items.indices) return
        _state.value = _state.value.copy(viewer = viewer.copy(index = index))
    }

    /**
     * Reads a text file into the editor.
     *
     * Capped, and honest about it. A log file can be hundreds of megabytes, and
     * a `TextField` holding that is not slow — it is a dead app. Past the cap
     * the first part is shown read-only, which still answers "what is in here"
     * without pretending an edit could be saved safely.
     */
    fun openText(item: FileItem) {
        val current = source ?: return
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val cap = MAX_EDITABLE_BYTES
                    val truncated = item.size > cap
                    val bytes = current.openRead(item.path).use { stream ->
                        val limit = if (truncated) cap.toInt() else Int.MAX_VALUE
                        stream.readAtMost(limit)
                    }
                    // A file with a zero byte in it is not text, whatever its
                    // extension says, and putting it in an editor would corrupt
                    // it the moment anything was saved.
                    val binary = bytes.take(BINARY_SNIFF).any { it == 0.toByte() }
                    val text = String(bytes, Charsets.UTF_8)
                    EditorState(
                        item = item,
                        text = text,
                        original = text,
                        readOnly = truncated || binary
                    )
                }
            }
            loaded.onSuccess { editor ->
                _state.value = _state.value.copy(
                    screen = Screen.EDIT_TEXT,
                    editor = editor,
                    loading = false,
                    notice = when {
                        editor.readOnly && item.size > MAX_EDITABLE_BYTES ->
                            "Too big to edit — showing the first ${formatSize(MAX_EDITABLE_BYTES)}."
                        editor.readOnly -> "This doesn't look like text, so it's read-only."
                        else -> null
                    }
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    notice = error.message ?: "Couldn't read ${item.name}."
                )
            }
        }
    }

    fun editText(text: String) {
        val editor = _state.value.editor ?: return
        if (editor.readOnly) return
        _state.value = _state.value.copy(editor = editor.copy(text = text))
    }

    /**
     * Writes the editor's contents back where they came from.
     *
     * Through the [FileSource], not through a file path, so saving to a server
     * is the same call as saving to the phone — and the file that comes back
     * down afterwards is the one that was actually written.
     */
    fun saveText() {
        val editor = _state.value.editor ?: return
        val current = source ?: return
        if (editor.readOnly || !editor.dirty) return

        _state.value = _state.value.copy(editor = editor.copy(saving = true))

        // Exactly what is being written, remembered before the write starts.
        // What comes back afterwards has to be compared against this and not
        // against whatever the editor holds by then.
        val saved = editor.text

        viewModelScope.launch {
            val bytes = saved.toByteArray(Charsets.UTF_8)
            val written = withContext(Dispatchers.IO) {
                runCatching { current.writeAtomic(editor.item.path, bytes) }
            }

            // Updated from the live editor rather than from the copy captured
            // above, which is the whole of a bug that quietly ate typing.
            //
            // Saving to a server is a network round trip, and anything typed
            // during it went into state -- and was then overwritten by the
            // stale snapshot this used to write back, with `original` set to
            // the stale text as well. So the characters vanished *and* the
            // editor called itself clean, and the app said "Saved."
            //
            // Setting only `original`, to the text that actually went out,
            // leaves anything typed since in place and correctly still dirty.
            //
            // The listing behind the editor is corrected in the same step. It
            // was listed before the save and nothing re-lists it on the way
            // back, so the folder went on showing the old size -- a file saved
            // at 28 bytes still read 15 -- until the user thought to pull it.
            written.onSuccess {
                val savedAt = System.currentTimeMillis()
                _state.update { current ->
                    val live = current.editor ?: return@update current
                    current.copy(
                        editor = live.copy(original = saved, saving = false),
                        entries = current.entries.map { entry ->
                            if (entry.path == live.item.path) {
                                entry.copy(size = bytes.size.toLong(), lastModified = savedAt)
                            } else {
                                entry
                            }
                        },
                        notice = "Saved ${live.item.name}."
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    val live = current.editor ?: return@update current
                    current.copy(
                        editor = live.copy(saving = false),
                        notice = error.message ?: "Couldn't save ${live.item.name}."
                    )
                }
            }
        }
    }

    /** Opens the built-in PDF viewer, fetching the file first where it is remote. */
    fun openPdf(item: FileItem) {
        if (_state.value.location == Location.Device) {
            _state.value = _state.value.copy(screen = Screen.VIEW_PDF, viewer = ViewerState(listOf(item), 0))
            return
        }
        // PdfRenderer needs a seekable file descriptor, which a socket is not.
        openRemote(item) { cached ->
            _state.value = _state.value.copy(
                screen = Screen.VIEW_PDF,
                viewer = ViewerState(listOf(item.copy(path = cached.absolutePath)), 0)
            )
        }
    }

    /**
     * Leaves a viewer or the editor, back to the folder.
     *
     * Returns false when there is unsaved work, so the screen can ask first
     * rather than this deciding on its own — losing an edit to a stray Back is
     * the one mistake a file manager cannot apologise for.
     */
    fun closeViewer(force: Boolean = false): Boolean {
        val editor = _state.value.editor
        if (!force && editor != null && editor.dirty) return false
        _state.value = _state.value.copy(
            screen = Screen.BROWSE,
            editor = null,
            viewer = null
        )
        return true
    }

    /**
     * One picture at full size, from wherever it lives.
     *
     * Sampled down to something a screen can show rather than decoded whole: a
     * modern phone camera produces images several times larger than the display
     * they are being viewed on, and the extra pixels cost memory to hold and
     * nothing to look at.
     */
    /**
     * One picture read at a time.
     *
     * The viewer's pager is what makes this necessary, and turning its
     * prefetch off for servers is only half the fix: a fast swipe can still
     * start the next page's read while the current one is mid-stream. A server
     * source is one connection, and two reads down it at once is a protocol
     * error on FTP rather than merely slow.
     *
     * Applied on the phone too. Local reads could overlap safely, but two
     * forty-megapixel decodes at once is heap pressure for no gain when only
     * one of them is being looked at.
     */
    private val imageReads = Mutex()

    suspend fun loadFullImage(item: FileItem): android.graphics.Bitmap? {
        val current = source ?: return null
        return imageReads.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = current.openRead(item.path)
                        .use { it.readAtMost(MAX_VIEWABLE_BYTES) }
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 2600 ||
                        bounds.outHeight / sample > 2600
                    ) sample *= 2
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sample
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }.getOrNull()
            }
        }
    }

    private fun formatSize(bytes: Long) = "${bytes / (1024 * 1024)} MB"

    private companion object {

        /**
         * How long the pull indicator stays up once a refresh is asked for.
         *
         * Long enough to read as a refresh and long enough for the indicator
         * to reach its resting state before it is asked to retract; short
         * enough that it never feels like the app is slow. See [refresh].
         */
        const val MIN_REFRESH_VISIBLE_MS = 450L

        /** Past this, a file is shown but not edited. */
        const val MAX_EDITABLE_BYTES = 4L * 1024 * 1024

        /** How far in to look for a zero byte before calling a file binary. */
        const val BINARY_SNIFF = 8000

        /** Past this a picture is not read into memory to be viewed. */
        const val MAX_VIEWABLE_BYTES = 64 * 1024 * 1024

        /** Tries per transfer, the first included. See FileOperations.copy. */
        const val TRANSFER_ATTEMPTS = 4

        /** Multiplied by the attempt number: 2s, then 4s, then 6s. */
        const val RETRY_BACKOFF_MS = 2_000L

        /** Matches what the copy path moves in; the 8 KB default is four times the syscalls. */
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

        /** How often a running download may redraw the bar. */
        const val DOWNLOAD_PROGRESS_MS = 150L

        /**
         * How often the notification may be rewritten.
         *
         * Longer than the on-screen bar's interval on purpose. The system
         * coalesces notification updates that arrive faster than a few per
         * second and simply drops the rest, so posting more often is work for
         * nothing and costs battery on a long copy.
         */
        const val NOTIFICATION_INTERVAL_MS = 500L
    }

    // ---------- lifecycle ----------

    /**
     * Everything [openServer] has to bring back from the IO dispatcher at once.
     *
     * A Triple did until the start path arrived and made it four; naming the
     * fields is worth more than the five lines it saves, since three of them
     * are collections and reading the wrong one positionally would compile.
     */
    private class OpenedServer(
        val source: RemoteFileSource,
        val path: String,
        val entries: List<FileItem>,
        val downloaded: Set<String>
    )

    private fun closeSource() {
        val open = source
        source = null
        if (open != null && open !== app.local) {
            // Fire and forget: closing a socket can block, and nothing waits on
            // the answer. viewModelScope rather than a bare thread so it still
            // dies with the view model.
            viewModelScope.launch(Dispatchers.IO) { runCatching { open.close() } }
        }
    }

    override fun onCleared() {
        // The job died with this view model, so the service holding it up and
        // the notification describing it both go too -- otherwise the shade
        // keeps a progress bar for a copy that stopped when the app did.
        TransferService.stop(app)
        JobNotifications.clear(app)
        closeSource()
        super.onCleared()
    }
}
