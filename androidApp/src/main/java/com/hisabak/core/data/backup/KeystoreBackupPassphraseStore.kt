package com.hisabak.core.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisabak.core.domain.backup.BackupPassphraseStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "hisabak_backup")

/**
 * Stores the backup passphrase encrypted at rest: the secret is wrapped with an AES key held in the
 * Android Keystore (non-exportable), and only the IV + ciphertext are persisted in DataStore. The
 * `isSet` flag is reactive so the UI reflects set/clear immediately.
 */
class KeystoreBackupPassphraseStore(private val context: Context) : BackupPassphraseStore {

    private val cipherTextKey = stringPreferencesKey("backup_passphrase_enc")

    override val isSet: Flow<Boolean> =
        context.backupDataStore.data.map { it[cipherTextKey] != null }

    override suspend fun set(passphrase: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val body = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(1 + cipher.iv.size + body.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(body)
            .array()
        context.backupDataStore.edit { it[cipherTextKey] = Base64.encodeToString(packed, Base64.NO_WRAP) }
    }

    override suspend fun get(): String? {
        val stored = context.backupDataStore.data.first()[cipherTextKey] ?: return null
        return runCatching {
            val buffer = ByteBuffer.wrap(Base64.decode(stored, Base64.NO_WRAP))
            val iv = ByteArray(buffer.get().toInt()).also(buffer::get)
            val body = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(body).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun clear() {
        context.backupDataStore.edit { it.remove(cipherTextKey) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hisabak_backup_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
