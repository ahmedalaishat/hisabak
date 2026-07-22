package com.hisabak.core.domain.backup

/** Reads/writes the financial data as a logical snapshot. Implemented over Room. */
interface BackupRepository {
    suspend fun snapshot(): BackupData

    /** Replaces ALL financial data with [data] in a single transaction (wipe + insert). */
    suspend fun replaceAll(data: BackupData)
}

/** Serializes the envelope to/from bytes. [decode] throws [BackupException] on malformed input. */
interface BackupCodec {
    fun encode(envelope: BackupEnvelope): ByteArray
    fun decode(bytes: ByteArray): BackupEnvelope
}

/**
 * Encrypts/decrypts backup bytes with a user passphrase. [decrypt] throws
 * [BackupException] ([BackupError.WrongPassphrase] / [BackupError.Corrupt]) on failure.
 * Destination-agnostic — callers decide where the bytes live (local file now, Drive later).
 */
interface BackupCrypto {
    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray
    fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray

    /** Whether [bytes] is an encrypted backup (vs. a plain, unencrypted codec payload). */
    fun isEncrypted(bytes: ByteArray): Boolean
}
