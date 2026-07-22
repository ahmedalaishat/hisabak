package com.hisabak.core.data.local.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseDecryptionMigrationTest {

    @Test
    fun `plaintext sqlite header is detected`() {
        val header = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        assertTrue(DatabaseDecryptionMigration.isPlaintextSqlite(header))
    }

    @Test
    fun `plaintext header with trailing page bytes is still detected`() {
        val header = ("SQLite format 3" + " page data").toByteArray(Charsets.US_ASCII)
        assertTrue(DatabaseDecryptionMigration.isPlaintextSqlite(header))
    }

    @Test
    fun `encrypted database header is not plaintext`() {
        // SQLCipher writes a random (ciphertext) first page, so the magic string is absent.
        val header = byteArrayOf(0x1F, 0x2A.toByte(), 0x00, 0x7E, 0x55, 0x11, 0x3C, 0x09, 0x42, 0x7B, 0x10, 0x33, 0x6D, 0x01, 0x58, 0x29)
        assertFalse(DatabaseDecryptionMigration.isPlaintextSqlite(header))
    }

    @Test
    fun `too-short header is not plaintext`() {
        val header = "SQLite".toByteArray(Charsets.US_ASCII)
        assertFalse(DatabaseDecryptionMigration.isPlaintextSqlite(header))
    }

    @Test
    fun `empty header is not plaintext`() {
        assertFalse(DatabaseDecryptionMigration.isPlaintextSqlite(ByteArray(0)))
    }
}
