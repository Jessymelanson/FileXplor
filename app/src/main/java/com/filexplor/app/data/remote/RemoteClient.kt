package com.filexplor.app.data.remote

import java.io.Closeable
import java.io.InputStream

data class RemoteEntry(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    /**
     * Bytes, or -1 where the protocol did not say.
     *
     * Carried because a mirror decides what to skip by comparing what is
     * already up there, and a name alone cannot tell a finished upload from one
     * that died half way — which would then be skipped forever as present.
     */
    val size: Long = -1L
)

/**
 * The file operations a mirror needs from a network location, narrowed to the
 * smallest set that covers them.
 *
 * Shared verbatim by every app in this family. They mirror different things —
 * notes, events, contacts and transcripts, photographs, encrypted vault
 * backups — but all of them want the same six verbs against FTP, SFTP and SMB,
 * and keeping one copy is what stops a fix landing in three apps and not the
 * other two.
 *
 * A client owns a live connection and is [Closeable]; callers are expected to
 * wrap a whole batch of work in one `use { }` block rather than reconnecting
 * per file. That matters more than it looks: a sync is often hundreds of tiny
 * operations — one file per note, per event, per contact — and paying a fresh
 * TCP handshake and auth round-trip for each would be unbearable on a real
 * server.
 */
interface RemoteClient : Closeable {
    fun list(path: String): List<RemoteEntry>
    fun read(path: String): ByteArray

    /**
     * The file as a stream, for when its size is unknown or large.
     *
     * [read] was enough for the apps this interface came from, which move notes
     * and calendar events — documents measured in kilobytes. A file manager is
     * pointed at whatever is there, and reading a film into a ByteArray to copy
     * it somewhere is how an app dies on a file it had no business holding.
     *
     * The returned stream owns protocol state and must be closed. Closing it
     * before the client is closed is the caller's job; FTP in particular cannot
     * issue another command until this one is finished with.
     */
    fun openRead(path: String): InputStream

    fun write(path: String, bytes: ByteArray)

    /**
     * Writes [length] bytes read from [source], without holding them.
     *
     * The array form above is fine for the small documents this interface was
     * first written for. A photograph is a thousand times larger and a video a
     * hundred thousand, and reading one whole into a ByteArray to hand it over
     * is how an upload runs the heap out — taking not just that file but
     * whatever else was being allocated beside it.
     *
     * [length] is for progress and for protocols that want the size up front;
     * implementations that do not need it may ignore it.
     */
    fun write(path: String, source: InputStream, length: Long)
    /**
     * Removes one entry.
     *
     * [isDirectory] is not a hint. Every one of the three protocols has two
     * different commands here and refuses the wrong one: FTP answers DELE on a
     * folder with 550, sshj's rm throws, and smbj's rm is documented for files.
     * Without it, deleting any folder on any server failed -- and a folder
     * moved from a server copied across and then would not leave, so the
     * "move" quietly became a copy.
     */
    fun delete(path: String, isDirectory: Boolean)
    fun exists(path: String): Boolean
    fun rename(fromPath: String, toPath: String)

    /**
     * Creates one directory, whose parent is expected to exist. Implementations
     * must treat "already there" as success — a sync calls this before every
     * push rather than tracking whether it has run before.
     */
    fun makeDirectory(path: String)
}

/** Thrown with a message fit to show the user — hosts, auth and paths all fail
 *  in ways people can usually act on, so the reason should not be swallowed. */
class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Creates every missing level of [path], so a freshly configured server does
 *  not need the folders laid out by hand first. */
fun RemoteClient.makeDirectories(path: String) {
    val parts = path.trim('/').split('/').filter { it.isNotBlank() }
    var current = ""
    parts.forEach { part ->
        current = "$current/$part"
        if (!exists(current)) makeDirectory(current)
    }
}

internal fun String.parentPath(): String {
    val trimmed = trimEnd('/')
    val cut = trimmed.lastIndexOf('/')
    return if (cut <= 0) "/" else trimmed.substring(0, cut)
}

internal fun joinPath(base: String, name: String): String =
    base.trimEnd('/') + "/" + name.trimStart('/')
