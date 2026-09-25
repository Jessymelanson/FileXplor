package com.filexplor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import com.filexplor.app.auth.ServerGate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.compose.runtime.collectAsState
import com.filexplor.app.data.Favourite
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.MimeTypes
import com.filexplor.app.data.PinShortcuts
import com.filexplor.app.data.ThemeMode
import com.filexplor.app.ui.FilesViewModel
import com.filexplor.app.ui.Screen
import com.filexplor.app.ui.components.FileXplorDialogHost
import com.filexplor.app.ui.components.JobBar
import com.filexplor.app.ui.screens.BrowseScreen
import com.filexplor.app.ui.screens.ImageViewerScreen
import com.filexplor.app.ui.screens.PdfViewerScreen
import com.filexplor.app.ui.screens.TextEditorScreen
import com.filexplor.app.ui.screens.HomeScreen
import com.filexplor.app.ui.theme.FileXplorTheme
import java.io.File

class MainActivity : FragmentActivity() {

    private val model: FilesViewModel by viewModels()

    /**
     * The one-off ask for permission to post the transfer notification.
     *
     * Registered as a field so it exists before the Activity starts, which is
     * what the result API requires. The result is deliberately ignored: a no
     * costs nothing here, because the notification is a convenience and the bar
     * inside the app is the real report.
     */
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Asks, on API 33 and up, and only where it has not already been answered.
     *
     * Android stops showing the dialog of its own accord once it has been
     * refused twice, so there is no nagging to guard against beyond not
     * asking before there is anything to ask about.
     */
    private fun askAboutNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    /**
     * A folder a pinned shortcut asked for, waiting to be opened.
     *
     * Held as Compose state and acted on from inside the composition rather
     * than straight from onCreate, because opening a folder on a server can
     * raise the unlock prompt and BiometricPrompt needs an Activity that has
     * actually started. Doing it in onCreate works on most phones and fails on
     * the ones where it matters.
     */
    private val pendingFolder = mutableStateOf<Favourite?>(null)

    /**
     * A second launch of an app that is already running.
     *
     * The shortcut's intent carries CLEAR_TOP and SINGLE_TOP, which is what
     * routes it here instead of stacking a second MainActivity on the first.
     * The ordinary launcher tap arrives here too once the task exists, as
     * ACTION_MAIN with no extras, and folderFrom returns null for it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PinShortcuts.folderFrom(intent)?.let {
            pendingFolder.value = it
            PinShortcuts.forget(intent)
        }
    }

    private fun openFolderShortcut(folder: Favourite) {
        if (folder.serverId != null) unlocked { model.openFavourite(folder) }
        else model.openFavourite(folder)
    }

    /**
     * Runs [action] once the user has confirmed who they are.
     *
     * Three ways through, and each is deliberate:
     *
     *  - Already unlocked this visit: straight through. The prompt is once per
     *    foreground session, not once per tap.
     *  - No way to authenticate — no screen lock, nothing enrolled, hardware
     *    unavailable: straight through, and the home screen says so rather
     *    than the app pretending a check happened. A file manager that cannot
     *    open its own saved servers would be a worse outcome than an ungated
     *    one, and there is nothing here to gate against on a phone anybody can
     *    already pick up and unlock.
     *  - Otherwise: ask, and run [action] only on success.
     */
    private fun unlocked(action: () -> Unit) {
        if (model.state.value.serversUnlocked || !ServerGate.available(this)) {
            action()
            return
        }
        ServerGate.prompt(
            activity = this,
            onSuccess = { model.markServersUnlocked(); action() },
            onFailed = { reason -> model.showNotice(reason) }
        )
    }

    /**
     * Puts the gate back up on the way out, but not for a rotation.
     *
     * A configuration change tears this Activity down and builds it again,
     * which reaches onStop exactly as leaving the app does. Re-locking on that
     * would mean turning the phone sideways inside a share throws you back to
     * an unlock prompt — the classic version of this bug.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            model.lockServers()
            // Not on screen any more, so a job that finishes from here on has
            // to leave its result somewhere that waits to be read. A rotation
            // reaches this too and is not leaving, hence the guard.
            model.setForeground(false)
        }
    }

    /**
     * Re-checks the storage permission whenever the app comes forward.
     *
     * `MANAGE_EXTERNAL_STORAGE` is granted on a Settings page, not in a dialog
     * this app can await a result from — the user leaves, toggles it and comes
     * back. Without this the home screen keeps saying it has no access until
     * the process is killed.
     *
     * The Activity's own onStart, not ProcessLifecycleOwner. The distinction
     * matters elsewhere in this family, where leaving the app has to lock it
     * and a rotation must not count as leaving — here the question is only
     * "are we visible again", which is exactly what this answers, and it needs
     * no extra dependency to ask.
     */
    override fun onStart() {
        super.onStart()
        model.setForeground(true)
        model.refreshHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a genuine cold start. getIntent() keeps returning the intent
        // that launched the Activity for as long as it lives, so handling it
        // unconditionally would drag the user back to the shortcut's folder
        // every time they turned the phone sideways. Consumed as it is read,
        // so nothing else can act on it twice either.
        if (savedInstanceState == null) {
            pendingFolder.value = PinShortcuts.folderFrom(intent)
            PinShortcuts.forget(intent)
        }

        setContent {
            val state by model.state.collectAsState()
            val snackbar = remember { SnackbarHostState() }
            // Raised when Back is pressed with unsaved work in the editor. Held
            // here rather than in the view model because it is a question about
            // this screen, not about the file.
            var discarding by remember { mutableStateOf(false) }

            if (discarding) {
                AlertDialog(
                    onDismissRequest = { discarding = false },
                    title = { Text("Discard changes?") },
                    text = {
                        Text(
                            "${state.editor?.item?.name ?: "This file"} has edits that " +
                                "have not been saved. Leaving now loses them."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            discarding = false
                            model.closeViewer(force = true)
                        }) { Text("Discard") }
                    },
                    dismissButton = {
                        TextButton(onClick = { discarding = false }) { Text("Keep editing") }
                    }
                )
            }

            LaunchedEffect(pendingFolder.value) {
                val folder = pendingFolder.value ?: return@LaunchedEffect
                pendingFolder.value = null
                openFolderShortcut(folder)
            }

            // Asked at the first job rather than at launch. A permission prompt
            // on the opening screen, before the app has done anything, is the
            // one people dismiss without reading -- and this one only means
            // something once there is a transfer worth watching from outside.
            var askedAboutNotifications by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.job != null) {
                if (state.job != null && !askedAboutNotifications) {
                    askedAboutNotifications = true
                    askAboutNotifications()
                }
            }

            LaunchedEffect(state.notice) {
                val notice = state.notice ?: return@LaunchedEffect
                snackbar.showSnackbar(notice)
                model.clearNotice()
            }

            val dark = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FileXplorTheme(darkTheme = dark, palette = state.themePalette) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The browse screen hosts the snackbar itself, because it
                    // is the only screen with bars along the bottom for one to
                    // land on. Two hosts bound to one state would show the same
                    // message twice, so exactly one of them is composed.
                    Scaffold(
                        snackbarHost = {
                            if (state.screen != Screen.BROWSE) SnackbarHost(snackbar)
                        },
                        // Below every screen, not just the browser.
                        //
                        // A copy takes minutes, and in those minutes people
                        // open the file they were looking at, go home to find
                        // the other folder, or read something — all of which
                        // left the browse screen and took the only sign that
                        // anything was happening with it. Hosted here it is
                        // visible, and stoppable, from anywhere in the app.
                        bottomBar = {
                            state.job?.let { job ->
                                JobBar(
                                    title = job.title,
                                    progress = job.progress,
                                    onCancel = model::cancelJob
                                )
                            }
                        }
                    ) { padding ->
                        // Back means "up a folder" for as long as there is one
                        // to go up to, which is what the on-screen arrow does
                        // too. Only at the top does it leave the app.
                        // Back means "up a folder" while browsing, and "leave
                        // this viewer" while in one. The editor is the exception:
                        // it asks first if there is unsaved work, because a Back
                        // gesture is far too easy to make by accident.
                        BackHandler(enabled = state.screen != Screen.HOME) {
                            when (state.screen) {
                                Screen.BROWSE -> model.up()
                                Screen.EDIT_TEXT -> if (!model.closeViewer()) discarding = true
                                else -> model.closeViewer(force = true)
                            }
                        }

                        when (state.screen) {
                            Screen.HOME -> HomeScreen(
                                state = state,
                                onOpenPath = model::openDevice,
                                // A saved folder on a server is behind the same
                                // gate the server itself is: the shortcut is a
                                // faster way in, not a way around.
                                onOpenFavourite = ::openFolderShortcut,
                                onRenameFavourite = model::requestRenameFavourite,
                                onRemoveFavourite = model::removeFavourite,
                                onMoveFavourite = model::moveFavourite,
                                onPinFavourite = model::pinFavourite,
                                onOpenServer = { server ->
                                    unlocked { model.openServer(server) }
                                },
                                onAddServer = model::requestAddServer,
                                onEditServer = { server ->
                                    // Gated as well as opening: the edit dialog
                                    // shows the saved password back to whoever
                                    // opened it.
                                    unlocked { model.requestEditServer(server) }
                                },
                                onDeleteServer = model::requestDeleteServer,
                                onGrantStorage = ::requestStorageAccess,
                                onOpenAppearance = { model.showDialog(com.filexplor.app.ui.Dialog.Appearance) },
                                onOpenLog = model::showTransferLog,
                                onOpenAbout = { model.showDialog(com.filexplor.app.ui.Dialog.About) },
                                modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                            )

                            Screen.BROWSE -> BrowseScreen(
                                snackbarHost = snackbar,
                                state = state,
                                onUp = { model.up() },
                                onHome = model::goHome,
                                onNavigate = model::navigate,
                                onOpen = { item -> model.routeOpen(item, ::openItem) },
                                onToggle = model::toggle,
                                onSelectAll = model::selectAll,
                                onClearSelection = model::clearSelection,
                                onQueryChange = model::setQuery,
                                onRefresh = model::refresh,
                                onCut = model::cut,
                                onCopy = model::copy,
                                onPaste = model::paste,
                                onClearClipboard = model::clearClipboard,
                                onDelete = model::requestDelete,
                                onRename = model::requestRename,
                                onDetails = { model.showDetails(it) },
                                onShare = ::shareItem,
                                onNewFolder = model::requestNewFolder,
                                onNewFile = model::requestNewFile,
                                onAddShortcut = model::requestAddShortcut,
                                onOpenLog = model::showTransferLog,
                                onSetViewMode = model::setViewMode,
                                onOpenSort = { model.showDialog(com.filexplor.app.ui.Dialog.Sort) },
                                onToggleHidden = model::setShowHidden,
                                modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                            )
                            Screen.VIEW_IMAGE -> state.viewer?.let { viewer ->
                                ImageViewerScreen(
                                    viewer = viewer,
                                    location = state.location,
                                    loadFull = { item -> model.loadFullImage(item) },
                                    onPage = model::viewerPage,
                                    onBack = { model.closeViewer(force = true) },
                                    modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                                )
                            }

                            Screen.EDIT_TEXT -> state.editor?.let { editor ->
                                TextEditorScreen(
                                    editor = editor,
                                    onTextChange = model::editText,
                                    onSave = model::saveText,
                                    onBack = { if (!model.closeViewer()) discarding = true },
                                    modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                                )
                            }

                            Screen.VIEW_PDF -> state.viewer?.current?.let { pdf ->
                                PdfViewerScreen(
                                    item = pdf,
                                    onOpenExternally = { launchViewer(File(pdf.path), pdf.name, pdf.extension) },
                                    onBack = { model.closeViewer(force = true) },
                                    modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                                )
                            }
                        }

                        FileXplorDialogHost(
                            dialog = state.dialog,
                            themeMode = state.themeMode,
                            themePalette = state.themePalette,
                            isDarkTheme = dark,
                            onDismiss = model::dismissDialog,
                            onThemeMode = model::setThemeMode,
                            onThemePalette = model::setThemePalette,
                            onCreateFolder = model::createFolder,
                            onCreateFile = model::createFile,
                            onRename = model::rename,
                            onConfirmDelete = model::confirmDelete,
                            onSortOrder = model::setSortOrder,
                            sortOrder = state.sortOrder,
                            onChooseProtocol = model::chooseProtocol,
                            onSaveServer = model::saveServer,
                            onConfirmDeleteServer = model::confirmDeleteServer,
                            onOpenItem = ::openItem,
                            onShareItem = ::shareItem,
                            onAddShortcut = model::addShortcut,
                            onRenameFavourite = model::renameFavourite,
                            onClearLog = model::clearTransferLog
                        )
                    }
                }
            }
        }
    }

    /**
     * Sends the user to the page where all-files access is granted.
     *
     * There is no runtime dialog for this one. It is a Settings screen per app,
     * reached by an intent carrying the package — and on the handful of builds
     * that do not carry that screen, the general list is better than a crash.
     */
    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(direct) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            .onFailure { model.notify("Couldn't open Android's storage settings.") }
    }

    /**
     * Hands a file to whatever else on the phone opens that kind of thing.
     *
     * Only for files on the device. A file on a server has no local path to
     * share, and copying it down first would be a download the user did not ask
     * for — so the browse screen offers this only where it can work.
     */
    private fun openItem(item: FileItem) {
        if (item.isDirectory) return
        // A file on a server has no path anything else can read, so it comes
        // down to the cache first and the copy is what gets opened.
        if (model.state.value.location != com.filexplor.app.data.Location.Device) {
            model.openRemote(item) { cached -> launchViewer(cached, item.name, item.extension) }
            return
        }
        launchViewer(File(item.path), item.name, item.extension)
    }

    /**
     * Hands one real file to whatever else on the phone opens that kind.
     *
     * An APK is the exception and gets its own intent. `ACTION_VIEW` on a
     * package archive lands in a chooser where the installer is one option
     * among the archive viewers; `ACTION_INSTALL_PACKAGE` goes straight there,
     * which is the only thing anybody taps an APK for.
     */
    private fun launchViewer(file: File, name: String, extension: String) {
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.files", file)
        }.getOrNull()
        if (uri == null) {
            model.notify("Couldn't hand $name to another app.")
            return
        }

        val type = MimeTypes.of(extension)
        val intent = if (MimeTypes.isInstallable(extension)) {
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val opened = runCatching { startActivity(intent) }.isSuccess
        if (opened) return

        // Nothing claimed the specific type. Offering the file as "anything"
        // usually finds something, and a chooser is a better answer than a
        // refusal — the first attempt was the specific one precisely so this is
        // the fallback and not the default.
        val anything = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MimeTypes.WILDCARD)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(anything, "Open $name")) }
            .onFailure { model.notify("Nothing on this phone opens $name.") }
    }

    private fun shareItem(item: FileItem) {
        // Same rule as opening: a server file has to come down before anything
        // else can be handed it.
        if (model.state.value.location != com.filexplor.app.data.Location.Device) {
            model.openRemote(item) { cached -> sendFile(cached, item.name, item.extension) }
            return
        }
        sendFile(File(item.path), item.name, item.extension)
    }

    private fun sendFile(file: File, name: String, extension: String) {
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.files", file)
        }.getOrNull()
        if (uri == null) {
            model.notify("Couldn't share $name.")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypes.of(extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Share $name")) }
            .onFailure { model.notify("Couldn't share that.") }
    }

}
