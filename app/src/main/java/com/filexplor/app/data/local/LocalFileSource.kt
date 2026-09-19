package com.filexplor.app.data.local

import android.content.Context
import android.os.Environment
import com.filexplor.app.data.FileItem
import com.filexplor.app.data.FileSource
import com.filexplor.app.data.joinPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** Matches what the three remote clients copy in. See write(). */
private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * The phone's own filesystem, through `java.io.File`.
 *
 * Plain File rather than the Storage Access Framework, and that is the decision
 * the whole app rests on. SAF hands back an opaque tree the user picked in a
 * system dialog; it cannot list a path by name, cannot follow a folder the user
 * did not pre-authorise, and turns every listing into a ContentResolver query.
 * None of that is a file explorer. With `MANAGE_EXTERNAL_STORAGE` granted, File
 * sees what the user sees, which is the only thing worth showing them.
 *
 * Without that permission this still works — it just sees very little, and the
 * screen says so rather than showing an empty folder that looks broken.
 */
class LocalFileSource(context: Context) : FileSource {

    private val app = context.applicationContext

    override val root: String = Environment.getExternalStorageDirectory().absolutePath

    override val label: String = "This phone"

    override fun list(path: String): List<FileItem> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        // listFiles returns null for a directory that exists but cannot be
        // read, which is a different thing from an empty one and the usual
        // shape of "the permission was never granted".
        val children = dir.listFiles() ?: throw IOException(
            "Can't read $path. FileXplor may not have permission to see inside it."
        )
        return children.map { it.toItem() }
    }

    override fun stat(path: String): FileItem? {
        val file = File(path)
        return if (file.exists()) file.toItem() else null
    }

    override fun openRead(path: String): InputStream = FileInputStream(File(path))

    override fun write(path: String, source: InputStream, length: Long) {
        val file = File(path)
        file.parentFile?.mkdirs()
        // 64 KB rather than Kotlin's 8 KB default, matching what the three
        // remote clients copy in. Eight kilobytes is four syscalls per block
        // on modern storage and it shows on a folder of video.
        //
        // The stream is not closed here on purpose: FileOperations opened it
        // and closes it, and a destination that closed the source as well is
        // the double-close that used to break server-to-server copies.
        FileOutputStream(file).use { out -> source.copyTo(out, COPY_BUFFER_BYTES) }
    }

    /**
     * Written beside the target and renamed over it.
     *
     * rename(2) within one directory replaces atomically, so at every instant
     * the path holds either the whole old file or the whole new one. Saving an
     * edit used to go straight through [write], which truncates on open -- so a
     * phone that ran out of space part way through a save turned the file into
     * an empty one, and the editor then reported the failure over a document
     * that no longer existed.
     *
     * The temporary name is a dotfile in the same directory, because it has to
     * be on the same filesystem for the rename to be a rename rather than a
     * copy, and it is removed on every path out of here.
     */
    override fun writeAtomic(path: String, bytes: ByteArray) {
        val target = File(path)
        val parent = target.parentFile
        parent?.mkdirs()
        val temp = File(parent, ".${target.name}.filexplor-save")
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                // Flushed to the disk itself, not just handed to the page
                // cache: a rename that lands before the data does would leave
                // the new name pointing at nothing after a sudden power loss.
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                throw IOException("Couldn't save ${target.name}.")
            }
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    override fun makeDirectory(path: String) {
        val dir = File(path)
        if (dir.isDirectory) return
        if (!dir.mkdirs()) throw IOException("Couldn't create ${dir.name}.")
    }

    override fun delete(path: String, isDirectory: Boolean) {
        val file = File(path)
        if (!file.exists()) return
        if (!file.delete()) throw IOException("Couldn't delete ${file.name}.")
    }

    override fun rename(path: String, newName: String) {
        val file = File(path)
        val target = File(file.parentFile, newName)
        if (target.exists()) throw IOException("${newName} already exists here.")
        if (!file.renameTo(target)) throw IOException("Couldn't rename ${file.name}.")
    }

    override fun exists(path: String): Boolean = File(path).exists()

    /**
     * The places worth offering as a starting point.
     *
     * Only the ones that actually exist on this phone. Listing Movies on a
     * device that has never had one is an entry that leads to an error, and a
     * shortcut that might not work is worse than no shortcut.
     */
    fun shortcuts(): List<Shortcut> {
        val named = listOf(
            "Download" to Environment.DIRECTORY_DOWNLOADS,
            "Pictures" to Environment.DIRECTORY_PICTURES,
            "Camera" to Environment.DIRECTORY_DCIM,
            "Music" to Environment.DIRECTORY_MUSIC,
            "Movies" to Environment.DIRECTORY_MOVIES,
            "Documents" to Environment.DIRECTORY_DOCUMENTS
        ).mapNotNull { (label, type) ->
            val dir = Environment.getExternalStoragePublicDirectory(type)
            if (dir?.isDirectory == true) Shortcut(label, dir.absolutePath) else null
        }
        return listOf(Shortcut("Internal storage", root)) + named + sdCards()
    }

    /**
     * Removable storage, where the phone has any.
     *
     * `getExternalFilesDirs` answers with this app's own folder on each volume,
     * which is not where anybody wants to be taken — but walking up four levels
     * from it lands on the volume root, and it is the only route to that path
     * that does not need a permission the app cannot get.
     */
    private fun sdCards(): List<Shortcut> =
        app.getExternalFilesDirs(null)
            .filterNotNull()
            .drop(1)
            .mapNotNull { appDir ->
                val volume = appDir.parentFile?.parentFile?.parentFile?.parentFile
                if (volume?.isDirectory == true) Shortcut("SD card", volume.absolutePath) else null
            }

    private fun File.toItem() = FileItem(
        name = name,
        path = absolutePath,
        isDirectory = isDirectory,
        size = if (isDirectory) -1L else length(),
        lastModified = lastModified()
    )

    /** One entry in the places list on the home screen. */
    data class Shortcut(val label: String, val path: String)
}
