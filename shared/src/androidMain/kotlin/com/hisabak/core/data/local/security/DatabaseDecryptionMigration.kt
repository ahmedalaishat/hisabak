package com.hisabak.core.data.local.security

import android.content.Context
import com.hisabak.core.data.local.HisabakDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * One-time, transparent migration of a legacy SQLCipher-encrypted `hisabak.db` back to a plaintext
 * SQLite database. Versions up to 1.8.x encrypted the database at rest; that layer was removed
 * (Android's file-based encryption already protects app-private storage, and the key was never
 * auth-gated), but installs upgrading from those versions still carry an encrypted file that plain
 * Room cannot open. Runs before Room opens. Keys off the on-disk file header, not a stored flag,
 * so a crash mid-migration just retries next launch; fresh installs and already-plaintext files
 * skip it without touching SQLCipher at all.
 */
object DatabaseDecryptionMigration {

    // Every plaintext SQLite file starts with the magic string "SQLite format 3 "; an encrypted
    // SQLCipher file does not (its header bytes are ciphertext). Matching the leading visible run is
    // a sufficient, unambiguous discriminator.
    private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    /** Pure decision: do these leading bytes mark an unencrypted SQLite database? */
    fun isPlaintextSqlite(header: ByteArray): Boolean =
        header.size >= SQLITE_MAGIC.size &&
            SQLITE_MAGIC.indices.all { header[it] == SQLITE_MAGIC[it] }

    fun migrateIfEncrypted(context: Context, keyStore: KeystoreDatabaseKeyStore) {
        val dbFile = context.getDatabasePath(HisabakDatabase.NAME)
        if (!dbFile.exists() || dbFile.length() < SQLITE_MAGIC.size) return
        val header = ByteArray(SQLITE_MAGIC.size)
        FileInputStream(dbFile).use { it.read(header) }
        if (isPlaintextSqlite(header)) return
        val passphrase = keyStore.existingPassphrase() ?: return
        System.loadLibrary("sqlcipher")
        if (decrypt(dbFile, String(passphrase, Charsets.UTF_8))) {
            keyStore.clear()
        }
    }

    private fun decrypt(dbFile: File, passphrase: String): Boolean {
        val tmp = File(dbFile.parentFile, "${dbFile.name}.dec.tmp")
        tmp.delete()

        // Opening the encrypted database reads through its WAL, so the export below captures any
        // pages still sitting there — the side-files can simply be deleted after the swap.
        val encrypted = SQLiteDatabase.openOrCreateDatabase(dbFile, passphrase, null, null)
        val userVersion: Int
        try {
            // The path is interpolated (ATTACH can't bind it) — it's an app-private file path, never
            // user-controlled, so there's no injection surface. An empty KEY makes the copy plaintext.
            encrypted.rawExecSQL("ATTACH DATABASE '${tmp.absolutePath}' AS plaintext KEY ''")
            encrypted.rawExecSQL("SELECT sqlcipher_export('plaintext')")
            encrypted.rawExecSQL("DETACH DATABASE plaintext")
            userVersion = encrypted.version
        } finally {
            encrypted.close()
        }

        // sqlcipher_export copies schema + data but not the user_version Room relies on; carry it
        // over, then prove the plaintext copy actually opens and is sound before we trust it.
        val plaintext = SQLiteDatabase.openOrCreateDatabase(tmp, "", null, null)
        val verified = try {
            plaintext.version = userVersion
            plaintext.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        } finally {
            plaintext.close()
        }
        if (!verified) {
            tmp.delete()
            return false // leave the encrypted file untouched; migration retries on the next launch
        }

        // The old file and its side-files are ciphertext — nothing to scrub, just swap the verified
        // plaintext copy in.
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-journal").delete()
        File("${dbFile.path}-shm").delete()
        File("${tmp.path}-wal").delete()
        File("${tmp.path}-shm").delete()
        if (!tmp.renameTo(dbFile)) {
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
        }
        return true
    }
}
