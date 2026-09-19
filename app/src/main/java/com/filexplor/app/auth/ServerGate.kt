package com.filexplor.app.auth

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The unlock prompt standing in front of saved servers.
 *
 * What it protects and what it does not is worth being exact about. Server
 * passwords are already encrypted at rest under a non-exportable Keystore key
 * and excluded from every backup, so reading the app's files gets an attacker
 * nothing whether this prompt exists or not. The one thing it does protect is
 * the case that encryption cannot: somebody holding the phone while it is
 * unlocked, opening the app, and walking into a share. That is the whole of
 * its job, and it does it well.
 *
 * It gates the screen rather than the key. Binding the Keystore key itself to
 * user authentication would be the stronger-sounding version and would break
 * the app: listings reconnect on their own, including after an idle timeout,
 * and a key that needs a recent unlock would start failing those in the
 * background with nothing on screen to explain it.
 *
 * Device credential is allowed beside biometrics deliberately — a phone with a
 * PIN and no enrolled fingerprint, or a sensor that has stopped reading, must
 * still be able to reach its own servers.
 */
object ServerGate {

    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether a prompt can actually run right now.
     *
     * Wrapped because some vendor biometric stacks throw out of
     * `canAuthenticate` rather than returning one of its documented error
     * codes, and this is asked on the way into opening a server.
     *
     * A false here means no prompt is shown and the server opens. That is the
     * deliberate choice: the alternative is a file manager that cannot reach
     * the servers it has saved, on a phone that has no way to authenticate.
     */
    fun available(context: Context): Boolean = runCatching {
        BiometricManager.from(context).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /**
     * Whether this phone has a screen lock at all.
     *
     * A different question from [available], and the distinction is one this
     * family of apps has already got wrong once. [available] answers "can a
     * prompt run this second" — and on plenty of devices that is no even with
     * a PIN set, because nothing is enrolled on the fingerprint sensor, or the
     * hardware is busy, or an update is pending. Telling somebody they have no
     * screen lock on the strength of that is how JAuth came to tell people to
     * set a lock they already had.
     *
     * `isDeviceSecure` knows nothing about biometrics and cannot be confused
     * by them. Use it for anything shown to the user; use [available] for the
     * decision to prompt.
     */
    fun deviceSecure(context: Context): Boolean = runCatching {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }.getOrDefault(false)

    /**
     * Asks, then calls back on the main thread.
     *
     * [onFailed] fires when the prompt gives up or the user backs out — not on
     * a single bad fingerprint, which the prompt retries by itself.
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) = onSuccess()

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    onFailed(message.toString())
                }
            }
        )

        // Building this can throw: which combinations of allowed authenticators
        // are legal varies by API level and by vendor. An exception on the way
        // into a server should leave the user on the home screen with a reason,
        // not take the app down.
        val info = runCatching {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock servers")
                .setSubtitle("Confirm it's you before opening a network location.")
                .setAllowedAuthenticators(ALLOWED)
                .build()
        }.getOrElse {
            onFailed("This phone's lock screen could not be used: ${it.message}")
            return
        }

        runCatching { prompt.authenticate(info) }.onFailure {
            onFailed(it.message ?: "The unlock prompt could not be shown.")
        }
    }
}
