package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class AesGcmBackupCryptoTest {

    private val crypto = AesGcmBackupCrypto()
    private val plaintext = "the quick brown fox spends AED 1,234".toByteArray()

    @Test
    fun `encrypt then decrypt round-trips`() {
        val out = crypto.decrypt(crypto.encrypt(plaintext, "correct horse"), "correct horse")
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val cipher = crypto.encrypt(plaintext, "pw")
        assertFalse(cipher.toString(Charsets.ISO_8859_1).contains("brown fox"))
    }

    @Test
    fun `wrong passphrase fails with WrongPassphrase`() {
        val cipher = crypto.encrypt(plaintext, "right")
        val e = assertThrows(BackupException::class.java) { crypto.decrypt(cipher, "wrong") }
        assertEquals(BackupError.WrongPassphrase, e.error)
    }

    @Test
    fun `garbage input fails with Corrupt`() {
        val e = assertThrows(BackupException::class.java) { crypto.decrypt(byteArrayOf(1, 2, 3), "pw") }
        assertEquals(BackupError.Corrupt, e.error)
    }

    @Test
    fun `tampering with the header is detected`() {
        val cipher = crypto.encrypt(plaintext, "pw")
        // Flip a bit inside the header (the iterations/salt region) — now bound as GCM AAD, so the
        // auth tag must reject it instead of silently deriving a different key.
        cipher[7] = (cipher[7].toInt() xor 0x01).toByte()
        assertThrows(BackupException::class.java) { crypto.decrypt(cipher, "pw") }
    }

    @Test
    fun `legacy format-1 backup without AAD still decrypts`() {
        val legacy = encryptFormat1(plaintext, "old pw")
        assertArrayEquals(plaintext, crypto.decrypt(legacy, "old pw"))
    }

    /** Reproduces the pre-AAD (format 1) on-disk layout an older app build would have written. */
    private fun encryptFormat1(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { it.indices.forEach { i -> it[i] = i.toByte() } }
        val iv = ByteArray(12).also { it.indices.forEach { i -> it[i] = (i + 1).toByte() } }
        val iterations = 210_000
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        }
        val body = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(4 + 1 + 4 + salt.size + iv.size + body.size)
            .put("HSBK".toByteArray(Charsets.US_ASCII))
            .put(1.toByte())
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .put(body)
            .array()
    }
}
