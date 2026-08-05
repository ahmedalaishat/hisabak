package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException

/**
 * Pure-Kotlin stand-in for the JVM-only `AesGcmBackupCrypto`, matching its observable contract:
 * the same `HSBK` magic (so [isEncrypted] agrees with real backups), round-trips with the right
 * passphrase, [BackupError.WrongPassphrase] with a wrong one, [BackupError.Corrupt] for non-backup
 * bytes. The "ciphertext" is just magic + passphrase + payload — fine for ViewModel tests; the
 * real cipher is covered by `AesGcmBackupCryptoTest` in androidApp.
 */
class FakeBackupCrypto : BackupCrypto {

    override fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val pass = passphrase.encodeToByteArray()
        return magic + byteArrayOf(pass.size.toByte()) + pass + plaintext
    }

    override fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray {
        if (!isEncrypted(ciphertext)) throw BackupException(BackupError.Corrupt)
        val length = ciphertext[magic.size].toInt()
        val stored = ciphertext.copyOfRange(magic.size + 1, magic.size + 1 + length).decodeToString()
        if (stored != passphrase) throw BackupException(BackupError.WrongPassphrase)
        return ciphertext.copyOfRange(magic.size + 1 + length, ciphertext.size)
    }

    override fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > magic.size && bytes.copyOf(magic.size).contentEquals(magic)

    private companion object {
        val magic = "HSBK".encodeToByteArray()
    }
}
