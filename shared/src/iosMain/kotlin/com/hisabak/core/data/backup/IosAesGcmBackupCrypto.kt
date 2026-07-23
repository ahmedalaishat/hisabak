package com.hisabak.core.data.backup

import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
// platform.CoreCrypto is Kotlin/Native's platform lib for CommonCrypto's headers.
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

/**
 * iOS [BackupCrypto] with the exact on-disk format of androidApp's `AesGcmBackupCrypto`:
 * plaintext header ("HSBK" + format 2 + big-endian iteration count + 16-byte salt + 12-byte IV)
 * followed by AES-256-GCM ciphertext||tag, with the header bound as associated data (format 2;
 * format-1 files without AAD still decrypt). Key derivation is PBKDF2-HMAC-SHA256 via
 * CommonCrypto; the GCM itself runs through the injected [GcmCipher] (CryptoKit).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAesGcmBackupCrypto(private val gcm: GcmCipher) : BackupCrypto {

    override fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = secureRandom(SALT_LEN)
        val iv = secureRandom(IV_LEN)
        val header = MAGIC + byteArrayOf(FORMAT.toByte()) + intBe(ITERATIONS) + salt + iv
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val body = gcm.seal(key.toNSData(), iv.toNSData(), header.toNSData(), plaintext.toNSData())
        return header + body.toByteArray()
    }

    override fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray {
        val header = parseHeader(ciphertext)
        val key = deriveKey(passphrase, header.salt, header.iterations)
        val body = ciphertext.copyOfRange(HEADER_LEN, ciphertext.size)
        // Format 2 binds the header as AAD; format 1 (legacy) wrote none.
        val aad = if (header.format == FORMAT) ciphertext.copyOf(HEADER_LEN).toNSData() else null
        val plain = gcm.open(key.toNSData(), header.iv.toNSData(), aad, body.toNSData())
            ?: throw BackupException(BackupError.WrongPassphrase)
        return plain.toByteArray()
    }

    override fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOf(MAGIC.size).contentEquals(MAGIC)

    private class Header(val format: Int, val iterations: Int, val salt: ByteArray, val iv: ByteArray)

    private fun parseHeader(ciphertext: ByteArray): Header {
        if (ciphertext.size < HEADER_LEN) throw BackupException(BackupError.Corrupt)
        val magic = ciphertext.copyOf(MAGIC.size)
        val format = ciphertext[MAGIC.size].toInt()
        if (!magic.contentEquals(MAGIC) || (format != FORMAT && format != FORMAT_LEGACY)) {
            throw BackupException(BackupError.Corrupt)
        }
        var i = MAGIC.size + 1
        val iterations = ((ciphertext[i].toInt() and 0xFF) shl 24) or
            ((ciphertext[i + 1].toInt() and 0xFF) shl 16) or
            ((ciphertext[i + 2].toInt() and 0xFF) shl 8) or
            (ciphertext[i + 3].toInt() and 0xFF)
        i += 4
        val salt = ciphertext.copyOfRange(i, i + SALT_LEN)
        i += SALT_LEN
        val iv = ciphertext.copyOfRange(i, i + IV_LEN)
        if (iterations <= 0) throw BackupException(BackupError.Corrupt)
        return Header(format, iterations, salt, iv)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val passLen = passphrase.encodeToByteArray().size
        val key = ByteArray(KEY_BITS / 8)
        val status = key.usePinned { keyPinned ->
            salt.usePinned { saltPinned ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2,
                    passphrase, passLen.toULong(),
                    saltPinned.addressOf(0).reinterpret(), salt.size.toULong(),
                    kCCPRFHmacAlgSHA256,
                    iterations.toUInt(),
                    keyPinned.addressOf(0).reinterpret(), key.size.toULong(),
                )
            }
        }
        if (status != 0) throw BackupException(BackupError.Corrupt)
        return key
    }

    private fun secureRandom(length: Int): ByteArray {
        val bytes = ByteArray(length)
        val status = bytes.usePinned {
            SecRandomCopyBytes(kSecRandomDefault, length.toULong(), it.addressOf(0))
        }
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
        return bytes
    }

    private fun intBe(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
    )

    private companion object {
        val MAGIC = "HSBK".encodeToByteArray()
        const val FORMAT = 2
        const val FORMAT_LEGACY = 1
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + IV_LEN
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    memScoped { NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong()) }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
