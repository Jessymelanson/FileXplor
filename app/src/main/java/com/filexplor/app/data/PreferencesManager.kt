package com.filexplor.app.data

import android.content.Context

/**
 * The handful of things the app remembers between runs.
 *
 * All of it is presentation: which theme, how a folder is drawn, how it is
 * sorted, whether dotfiles show. Nothing about the files themselves is stored —
 * a file manager's state is the filesystem, and keeping a second copy of it
 * would only be a second copy to get wrong.
 */
class PreferencesManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("filexplor_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var themePalette: ThemePalette
        get() = runCatching {
            ThemePalette.valueOf(prefs.getString(KEY_THEME_PALETTE, ThemePalette.PAPER.name)!!)
        }.getOrDefault(ThemePalette.PAPER)
        set(value) = prefs.edit().putString(KEY_THEME_PALETTE, value.name).apply()

    var viewMode: ViewMode
        get() = runCatching {
            ViewMode.valueOf(prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.name)!!)
        }.getOrDefault(ViewMode.LIST)
        set(value) = prefs.edit().putString(KEY_VIEW_MODE, value.name).apply()

    var sortOrder: SortOrder
        get() = runCatching {
            SortOrder.valueOf(prefs.getString(KEY_SORT, SortOrder.NAME.name)!!)
        }.getOrDefault(SortOrder.NAME)
        set(value) = prefs.edit().putString(KEY_SORT, value.name).apply()

    /**
     * Off by default.
     *
     * A phone's storage root is mostly dotfolders that no one put there on
     * purpose, and showing them first thing makes the app look like it opened
     * on the wrong place.
     */
    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_PALETTE = "theme_palette"
        const val KEY_VIEW_MODE = "view_mode"
        const val KEY_SORT = "sort_order"
        const val KEY_SHOW_HIDDEN = "show_hidden"
    }
}
