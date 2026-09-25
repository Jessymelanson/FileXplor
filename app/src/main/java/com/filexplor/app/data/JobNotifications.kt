package com.filexplor.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.filexplor.app.MainActivity
import com.filexplor.app.R

/**
 * The running copy, move, delete or download, shown in the status bar — and
 * what became of it once it stops.
 *
 * A mirror of the bar inside the app rather than a second source of truth:
 * everything here is driven from the one [Progress] the operation already
 * publishes, so the two can never disagree about what is happening.
 *
 * The running notification belongs to [TransferService], which posts it to
 * become a foreground service and drops it when the job ends. This object
 * builds it, refreshes it as progress arrives — an update needs no permission
 * a running service does not already have — and posts the separate, dismissable
 * one that says how it finished.
 *
 * That last one exists because of a specific failure: a long transfer with the
 * phone face down ends while nobody is looking, the snackbar says its piece to
 * an empty room and times out, and what the user comes back to is an app with
 * no job running and no explanation of why it stopped. A result worth reading
 * has to wait to be read.
 */
object JobNotifications {

    private const val CHANNEL_ID = "file_jobs"

    /** The ongoing one. A second job cannot run, so a second id would never be used. */
    const val RUNNING_ID = 4201

    /** The one left behind afterwards, which the user dismisses in their own time. */
    private const val OUTCOME_ID = 4202

    /**
     * Builds the ongoing notification. [TransferService] posts the first one;
     * [show] replaces it as the numbers change.
     *
     * Silent and low importance: this is a thing to glance at, not to be
     * interrupted by, and a copy of four hundred photographs that buzzed once
     * per file would be unusable. [percent] null draws the indeterminate bar,
     * which is the honest picture while the tree is still being walked.
     */
    fun build(context: Context, title: String, current: String, percent: Int?): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle(title)
            .setContentText(current.ifBlank { "Working out what to move…" })
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Ongoing, because it describes something still happening and
            // swiping it away would leave the copy running with nothing to say
            // so. Alert-once, because every update would otherwise re-announce
            // it.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))

        if (percent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    /**
     * Refreshes the running notification in place.
     *
     * Returns quietly where notifications are off. Android refuses the post
     * anyway on API 33 and up without the runtime permission, and a file
     * manager that could not copy a file because it had not been allowed to
     * mention it would be absurd — the service, and so the transfer, is
     * unaffected either way.
     */
    fun show(context: Context, title: String, current: String, percent: Int?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Guarded: SecurityException here is the documented answer when the
        // permission was revoked between the check and this line, and a
        // revoked permission is not a reason to lose the operation it was
        // describing.
        try {
            manager.notify(RUNNING_ID, build(context, title, current, percent))
        } catch (e: SecurityException) {
            // Revoked since the check above.
        } catch (e: RuntimeException) {
            // A notification is never worth failing the job it describes.
        }
    }

    /**
     * What happened, left in the shade to be found later.
     *
     * Posted only when the app was not on screen at the moment the job ended.
     * Somebody watching the copy finish has already read the snackbar, and a
     * notification repeating it would be noise on every two-file paste.
     *
     * Dismissable and auto-cancelling, unlike the running one: it is a record,
     * not a state, and the only thing to do with it is read it.
     */
    fun showOutcome(context: Context, title: String, message: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || message.isBlank()) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transfer)
            .setContentTitle(title)
            .setContentText(message)
            // The long form as well, because the useful ones are the long
            // ones: "3 items copied, 2 failed. Connection reset" does not fit
            // on a collapsed row, and the half that gets cut off is the half
            // worth reading.
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            manager.notify(OUTCOME_ID, notification)
        } catch (e: SecurityException) {
            // Revoked since the check above.
        } catch (e: RuntimeException) {
            // Nothing to be done about a record that could not be posted.
        }
    }

    /** Removes both, for a fresh start with nothing running. */
    fun clear(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).apply {
                cancel(RUNNING_ID)
                cancel(OUTCOME_ID)
            }
        }
    }

    /**
     * Tapping it comes back to the app, to the instance already running.
     *
     * The same three flags a pinned folder shortcut carries, and for the same
     * reason: without them this would stack a second MainActivity on the first,
     * each with its own view model — and the second one would not know about
     * the copy the notification was describing.
     */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Created on demand rather than at startup.
     *
     * Creating a channel is cheap and idempotent, and doing it here means an
     * install that never copies anything never puts a row in the user's
     * notification settings for a thing it does not do.
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "File transfers",
                // LOW, so it appears in the shade and on the lock screen
                // without a sound or a heads-up card. DEFAULT would make a
                // routine copy announce itself over whatever the phone was
                // doing.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress while files are copied, moved or downloaded."
                setShowBadge(false)
            }
        )
    }
}
