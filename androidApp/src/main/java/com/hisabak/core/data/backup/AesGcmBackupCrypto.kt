package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based AES-256-GCM encryption for backup bytes. The passphrase is the portable root of
 * trust (works across devices); there is no recovery if it's lost — by design. File layout is a
 * plaintext header (magic + format + KDF iterations + salt + IV) followed by the GCM ciphertext,
 * whose auth tag detects both a wrong passphrase and tampering. The header is bound as GCM
 * associated data (format 2+), so the parameters it carries are tamper-evident too; format-1 files
 * (no AAD) written by older builds still decrypt.
 */
class AesGcmBackupCrypto(
    private val random: SecureRandom = SecureRandom(),
) : BackupCrypto {

    override fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC)
            .put(FORMAT.toByte())
            .putInt(ITERATIONS)
            .put(salt)
            .put(iv)
            .array()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header)
        }
        val body = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(header.size + body.size).put(header).put(body).array()
    }

    override fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(ciphertext)
            val magic = ByteArray(MAGIC.size).also(buffer::get)
            val format = buffer.get().toInt()
            if (!magic.contentEquals(MAGIC) || (format != FORMAT && format != FORMAT_LEGACY)) {
                throw BackupException(BackupError.Corrupt)
            }
            val iterations = buffer.int
            val salt = ByteArray(SALT_LEN).also(buffer::get)
            val iv = ByteArray(IV_LEN).also(buffer::get)
            val body = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
                // Format 2 binds the header as AAD; format 1 (legacy) wrote none.
                if (format == FORMAT) updateAAD(ciphertext.copyOf(HEADER_LEN))
            }
            return cipher.doFinal(body)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupError.WrongPassphrase)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupError.Corrupt)
        }
    }

    override fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOf(MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        val MAGIC = "HSBK".toByteArray(Charsets.US_ASCII)
        const val FORMAT = 2
        const val FORMAT_LEGACY = 1
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + IV_LEN // magic + format + iterations + salt + iv
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000
        const val KDF = "PBKDF2WithHmacSHA256"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
