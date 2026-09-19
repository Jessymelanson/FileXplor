package com.filexplor.app

import android.app.Application
import com.filexplor.app.data.FavouriteStore
import com.filexplor.app.data.FileOperations
import com.filexplor.app.data.PreferencesManager
import com.filexplor.app.data.TransferLog
import com.filexplor.app.data.local.LocalFileSource
import com.filexplor.app.data.remote.RemoteConnector
import com.filexplor.app.data.remote.ServerStore

/**
 * The few long-lived objects, built once.
 *
 * No dependency-injection framework, matching the rest of this family. There
 * are seven things here and one consumer of them; a container would be more code
 * than the thing it contained.
 */
class FileXplorApp : Application() {

    val prefs by lazy { PreferencesManager(this) }
    val servers by lazy { ServerStore(this) }
    val favourites by lazy { FavouriteStore(this) }
    val log by lazy { TransferLog(this) }
    val connector by lazy { RemoteConnector(servers) }
    val local by lazy { LocalFileSource(this) }
    val operations by lazy { FileOperations() }
}
