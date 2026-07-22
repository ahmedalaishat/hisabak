package com.hisabak.core.data.local.security

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Legacy reader for the passphrase that used to encrypt the SQLCipher database at rest (removed in
 * 1.9.0). The passphrase was a random secret wrapped with a non-exportable Android Keystore AES key,
 * with only the IV + ciphertext persisted. This class no longer creates keys — it only unwraps a
 * previously stored passphrase so [DatabaseDecryptionMigration] can unlock and decrypt a legacy
 * database, then [clear]s the stored material once the migration succeeds.
 */
class KeystoreDatabaseKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the stored database passphrase, or null if none was ever created. */
    fun existingPassphrase(): ByteArray? {
        val stored = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return unwrap(stored)
    }

    /** Drops the wrapped passphrase and its Keystore key; nothing needs them after decryption. */
    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).apply()
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun unwrap(stored: String): ByteArray {
        val buffer = ByteBuffer.wrap(Base64.decode(stored, Base64.NO_WRAP))
        val iv = ByteArray(buffer.get().toInt()).also(buffer::get)
        val body = ByteArray(buffer.remaining()).also(buffer::get)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = (keyStore.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(body)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hisabak_database_key"
        const val PREFS_NAME = "hisabak_db_key"
        const val KEY_CIPHERTEXT = "db_passphrase_enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
