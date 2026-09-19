package com.filexplor.app.data.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts server passwords at rest under a Keystore-held AES key.
 *
 * This key deliberately does *not* require user authentication: the app has
 * to be able to reconnect to a share whenever it refreshes, including in the
 * background, so gating it behind a biometric prompt would make network
 * folders unusable. The protection it does buy is real though — the key
 * material never leaves the Keystore, so the stored passwords are useless to
 * anything that merely reads the app's preferences file (a backup
 * extraction, an adb pull off a rooted device).
 *
 * What this protects is the credential, not the content. Everything the app
 * mirrors lives on the device and leaves it only as the copies this credential
 * reaches; losing the credential is not losing the data.
 */
internal object CredentialCipher {

    private const val KEY_ALIAS = "filexplor_credential_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    /**
     * The key, loaded once and kept.
     *
     * `KeyStore.getInstance(...).load(null)` is a round trip to the keystore
     * daemon, and it was being made on every encrypt and every decrypt. The key
     * itself does not change for the life of the install, so there is nothing
     * to re-read: holding it turns a repeated few-hundred-millisecond call into
     * one. Volatile because a background upload and the main thread can both
     * reach this, and a torn reference would be a crash rather than a slow
     * screen.
     *
     * Nothing is cached on failure. If the key is genuinely gone — cleared app
     * data, a restored backup — the next call retries and then fails honestly,
     * which is what the callers already expect.
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }

        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
        cachedKey = key
        return key
    }

    fun encrypt(plaintext: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
