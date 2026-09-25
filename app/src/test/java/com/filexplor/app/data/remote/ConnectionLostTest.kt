package com.filexplor.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketException

/**
 * The classifier that decides whether an operation is retried.
 *
 * Worth a test of its own because both of its mistakes are expensive and
 * neither is visible. Too narrow and the "broken pipe" it was written for comes
 * straight back to the user. Too broad and a failure that actually reached the
 * server gets sent a second time — which for a delete or a rename means doing
 * it twice.
 */
class ConnectionLostTest {

    private fun lost(error: Throwable) = RemoteFileSource.isConnectionLost(error)

    @Test
    fun `socket errors are a lost connection`() {
        assertTrue(lost(SocketException("Broken pipe")))
        assertTrue(lost(SocketException("Connection reset")))
        assertTrue(lost(EOFException()))
    }

    @Test
    fun `wrapped socket errors are found through the cause chain`() {
        // What the protocol libraries actually hand back: their own type, with
        // the real cause buried a couple of levels down.
        val wrapped = RemoteException(
            "Couldn't list /photos.",
            IOException("transport failed", SocketException("Broken pipe"))
        )
        assertTrue(lost(wrapped))
    }

    @Test
    fun `libraries that keep only the text are matched on the message`() {
        assertTrue(lost(RemoteException("Session is not connected")))
        assertTrue(lost(IllegalStateException("The socket is closed")))
    }

    @Test
    fun `a missing file is not a lost connection`() {
        assertFalse(lost(FileNotFoundException("/photos/missing.jpg")))
        assertFalse(lost(RemoteException("No such file or directory")))
    }

    @Test
    fun `a refused permission is not a lost connection`() {
        assertFalse(lost(RemoteException("Permission denied")))
        assertFalse(lost(RemoteException("550 Access is denied.")))
    }

    @Test
    fun `a wrong password is not a lost connection`() {
        // This one matters most. Retrying a rejected login is how an app locks
        // somebody out of their own server after three tries.
        assertFalse(lost(RemoteException("Login failed: 530 Not logged in.")))
        assertFalse(lost(RemoteException("Exhausted available authentication methods")))
    }

    @Test
    fun `an FTP session the client dropped itself is reconnected`() {
        // What FtpRemoteClient.ensureOpen throws. commons-net's own answer on
        // a dropped session was a NullPointerException, which is not this.
        assertTrue(lost(org.apache.commons.net.ftp.FTPConnectionClosedException("Connection closed.")))
        assertFalse(lost(NullPointerException("Socket.getInetAddress() on a null object reference")))
    }

    @Test
    fun `a cycle in the cause chain does not hang`() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)
        assertFalse(lost(first))
    }
}
