package com.filexplor.app.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Keeps the process alive and the network reachable while a transfer runs.
 *
 * It does no work. The copy still lives in the view model, still ends when the
 * app does, and still cannot be started from the background — the design note
 * on RunningJob about not carrying on after the app is closed still holds. What
 * this adds is the case that note did not account for: **the screen being
 * locked**, which is not the same as the app being closed and until now had the
 * same effect on a transfer.
 *
 * Three separate mechanisms were taking a copy down within seconds of the
 * screen going off, and it needs all three answered:
 *
 *  - The CPU suspends when nothing holds a wake lock. A thread blocked on a
 *    socket read holds none, so the process froze, the server timed the idle
 *    connection out, and the read came back as a reset whenever the phone next
 *    woke. Answered by the partial wake lock below.
 *  - Doze suspends network access for background apps. A foreground service is
 *    exempt; nothing else this app could be is.
 *  - A backgrounded process is a candidate for being killed outright under
 *    memory pressure — which is the version with no error message at all,
 *    because nothing survived to write one. A foreground service moves it to
 *    the bucket that is killed last.
 *
 * The notification is not decoration here. It is the price of being a
 * foreground service, and the right price: something running for minutes with
 * the screen off should say so.
 */
class TransferService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground immediately. Android allows a few seconds between arriving
        // here and calling this, and kills the app if it is late, so there is
        // nothing in between.
        val notification = JobNotifications.build(
            context = this,
            title = intent?.getStringExtra(EXTRA_TITLE) ?: "Working",
            current = "",
            percent = null
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    JobNotifications.RUNNING_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(JobNotifications.RUNNING_ID, notification)
            }
        }

        acquireWakeLock()

        // Everything after this notification is published by the view model,
        // straight to the same id. Updating a notification is allowed from
        // anywhere; starting a foreground service is not, and doing it once
        // from the tap that began the job is the only moment it is certainly
        // legal.
        //
        // NOT_STICKY, because if Android does kill this the copy died with the
        // process. Restarting to describe work that is not happening would be
        // worse than nothing.
        return START_NOT_STICKY
    }

    /**
     * Partial: the CPU stays up, the screen does not.
     *
     * Timed out at four hours as a backstop. A wake lock leaked by a crash is a
     * flat battery by morning, and no operation this app starts is still going
     * after four hours — one that could be is on a connection that would have
     * dropped long before.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = runCatching {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()
    }

    override fun onDestroy() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLE = "title"

        private const val WAKE_LOCK_TAG = "FileXplor:transfer"
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000

        /**
         * Called once, from the tap that starts a job.
         *
         * Not from the progress updates: starting a foreground service is only
         * reliably permitted while the app is in the foreground, and by the
         * second update the screen may already be off. The running
         * notification is refreshed by [JobNotifications.show] instead, which
         * needs no such permission.
         *
         * Failures are swallowed. A phone that refuses the service is one where
         * the transfer is less likely to survive a locked screen, not one where
         * it should refuse to start.
         */
        fun start(context: Context, title: String) {
            val intent = Intent(context, TransferService::class.java)
                .putExtra(EXTRA_TITLE, title)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** Ends it, which takes the ongoing notification with it. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TransferService::class.java)) }
        }
    }
}
