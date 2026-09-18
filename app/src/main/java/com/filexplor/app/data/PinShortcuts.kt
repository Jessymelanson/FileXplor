package com.filexplor.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.filexplor.app.MainActivity
import com.filexplor.app.R

/**
 * Folders pinned to the phone's own home screen, beside the apps.
 *
 * The launcher owns these once they are placed: nothing here can move one,
 * remove one or ask whether one exists. All this side can do is hand Android a
 * description and an intent and let the user decide — which is why every
 * function here reports what it managed rather than promising an outcome.
 */
object PinShortcuts {

    /**
     * Private to this app, not a public one.
     *
     * The intent names [MainActivity] explicitly, so the action is only ever a
     * label this app reads back off its own intent. Nothing else can reach it,
     * and it is deliberately not in the manifest: an exported filter would let
     * any app on the phone ask FileXplor to open an arbitrary path.
     */
    private const val ACTION_OPEN_FOLDER = "com.filexplor.app.action.OPEN_FOLDER"
    private const val EXTRA_PATH = "com.filexplor.app.extra.PATH"
    private const val EXTRA_SERVER = "com.filexplor.app.extra.SERVER_ID"

    /**
     * Whether this launcher takes pinned shortcuts at all.
     *
     * Most do; some of the lighter third-party ones do not, and a few answer
     * by throwing. Asked before the option is offered rather than after it is
     * chosen, so the checkbox can say so instead of the app failing quietly.
     */
    fun supported(context: Context): Boolean =
        runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }
            .getOrDefault(false)

    /**
     * Asks Android to place [favourite] on the home screen.
     *
     * True means the request went out, not that an icon appeared: what happens
     * next is a system dialog the user can decline, or on some launchers a
     * silent placement. There is no callback worth waiting for and no way to
     * ask afterwards, so the caller should say what it did rather than what
     * happened.
     */
    fun request(context: Context, favourite: Favourite): Boolean {
        if (!supported(context)) return false
        val label = favourite.label.trim()
            .ifBlank { favourite.path.trimEnd('/').substringAfterLast('/') }
            .ifBlank { "Folder" }
        val info = ShortcutInfoCompat.Builder(context, "folder:${favourite.key}")
            // Short labels are what a home screen has room for; long ones show
            // in the launcher's own lists. Both are set because a launcher that
            // wants the long one and finds none falls back to the id.
            .setShortLabel(label)
            .setLongLabel(label)
            .setIntent(intentFor(context, favourite))
            .apply { icon(context)?.let { setIcon(it) } }
            .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }
            .getOrDefault(false)
    }

    /**
     * The intent a pinned folder carries.
     *
     * The three flags are what keep one FileXplor. Without them, tapping a
     * pinned folder while the app is already open starts a *second*
     * MainActivity on top of the first, each with its own view model and its
     * own idea of where the user is, and Back walks down through the pile.
     * `CLEAR_TOP` with `SINGLE_TOP` is the documented pair that hands the
     * intent to the instance already running instead, through onNewIntent.
     *
     * Done here rather than by setting a launchMode in the manifest, which
     * would change how the app behaves when its ordinary icon is tapped too —
     * a much wider change than this needs.
     */
    private fun intentFor(context: Context, favourite: Favourite): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_FOLDER
            putExtra(EXTRA_PATH, favourite.path)
            favourite.serverId?.let { putExtra(EXTRA_SERVER, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /**
     * The folder an incoming intent is asking for, or null if it is not one of
     * ours.
     *
     * The action is checked first, so the plain launcher tap — which arrives
     * here too, as ACTION_MAIN with no extras — is ignored rather than opening
     * whatever the last shortcut pointed at.
     */
    fun folderFrom(intent: Intent?): Favourite? {
        if (intent == null || intent.action != ACTION_OPEN_FOLDER) return null
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf { it.isNotBlank() } ?: return null
        return Favourite(
            label = path.trimEnd('/').substringAfterLast('/').ifBlank { path },
            path = path,
            serverId = intent.getStringExtra(EXTRA_SERVER)?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Strips the request out of an intent once it has been acted on.
     *
     * getIntent() keeps returning the intent that started the Activity for as
     * long as the Activity lives, so an unconsumed one gets handled again by
     * anything that re-reads it. Clearing the action is enough, and is what
     * [folderFrom] checks.
     */
    fun forget(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_FOLDER) return
        intent.action = null
        intent.removeExtra(EXTRA_PATH)
        intent.removeExtra(EXTRA_SERVER)
    }

    /**
     * The icon, rendered from the vector at the size the launcher will want.
     *
     * An adaptive bitmap rather than a resource: a plain drawable handed to a
     * launcher is treated as a legacy icon and gets shrunk onto a white tile,
     * while an adaptive one is masked to whatever shape the phone uses and sits
     * correctly beside the app icons. 108 device pixels per 108 vector units
     * matches the canvas one to one.
     */
    private fun icon(context: Context): IconCompat? {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_shortcut_folder) ?: return null
        val size = (108 * context.resources.displayMetrics.density).toInt().coerceIn(108, 432)
        return runCatching {
            IconCompat.createWithAdaptiveBitmap(drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888))
        }.getOrNull()
    }
}
