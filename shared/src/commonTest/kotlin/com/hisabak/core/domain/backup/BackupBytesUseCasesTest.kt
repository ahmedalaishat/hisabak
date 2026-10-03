package com.hisabak.core.domain.backup

import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeBackupCrypto
import com.hisabak.testutil.FakeBackupRemote
import com.hisabak.testutil.FakeBackupRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.sampleBackupData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The destination-free half of the backup engine: what a file export writes and an import reads. */
class BackupBytesUseCasesTest {

    private val codec = JsonBackupCodec()
    private val crypto = FakeBackupCrypto()

    private fun build(data: BackupData = sampleBackupData(), schema: Int = 2) =
        BuildBackupBytesUseCase(FakeBackupRepository(data), codec, crypto, TestClock(), appVersionCode = 8, schemaVersion = schema)

    private fun restore(target: FakeBackupRepository, schema: Int = 2) =
        RestoreFromBytesUseCase(target, codec, crypto, schemaVersion = schema)

    private suspend fun bytes(passphrase: String?, schema: Int = 2): ByteArray =
        assertIs<BackupBytesResult.Success>(build(schema = schema).invoke(passphrase)).bytes

    @Test
    fun `an encrypted export imports with its passphrase`() = runTest {
        val file = bytes("pass1234")
        assertTrue(crypto.isEncrypted(file))

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Success(sampleBackupData().totalRecords), restore(target).invoke(file, "pass1234"))
        assertEquals(sampleBackupData(), target.replacedWith)
    }

    @Test
    fun `a plain export imports without a passphrase`() = runTest {
        val file = bytes(passphrase = null)
        assertTrue(!crypto.isEncrypted(file))

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Success(sampleBackupData().totalRecords), restore(target).invoke(file, null))
        assertEquals(sampleBackupData(), target.replacedWith)
    }

    @Test
    fun `an encrypted file asks for the passphrase and writes nothing`() = runTest {
        val target = FakeBackupRepository()
        assertEquals(RestoreResult.PassphraseRequired, restore(target).invoke(bytes("pass1234"), null))
        assertNull(target.replacedWith)
    }

    @Test
    fun `a wrong passphrase fails and writes nothing`() = runTest {
        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Failure(BackupError.WrongPassphrase), restore(target).invoke(bytes("right-one"), "wrong-one"))
        assertNull(target.replacedWith)
    }

    @Test
    fun `bytes that are not a backup fail as corrupt`() = runTest {
        val target = FakeBackupRepository()
        val notABackup = "%PDF-1.7 not a backup".encodeToByteArray()
        assertEquals(RestoreResult.Failure(BackupError.Corrupt), restore(target).invoke(notABackup, null))
        assertNull(target.replacedWith)
    }

    @Test
    fun `a truncated plain backup fails as corrupt`() = runTest {
        val file = bytes(passphrase = null)
        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Failure(BackupError.Corrupt), restore(target).invoke(file.copyOf(file.size / 2), null))
        assertNull(target.replacedWith)
    }

    @Test
    fun `an empty file fails as empty`() = runTest {
        assertEquals(RestoreResult.Failure(BackupError.Empty), restore(FakeBackupRepository()).invoke(ByteArray(0), null))
    }

    @Test
    fun `a file from a newer schema is rejected and writes nothing`() = runTest {
        val target = FakeBackupRepository()
        assertEquals(
            RestoreResult.Failure(BackupError.UnsupportedVersion(3, 2)),
            restore(target, schema = 2).invoke(bytes(null, schema = 3), null),
        )
        assertNull(target.replacedWith)
    }

    @Test
    fun `exporting with no data fails as empty`() = runTest {
        assertEquals(BackupBytesResult.Failure(BackupError.Empty), build(BackupData()).invoke("pass1234"))
    }

    @Test
    fun `the Drive upload is exactly the exported bytes and only it stamps the last backup`() = runTest {
        val prefs = FakeAppPreferences()
        val remote = FakeBackupRemote()
        val clock = TestClock()
        RunBackupUseCase(build(), remote, clock, prefs).invoke(passphrase = null)

        assertTrue(bytes(passphrase = null).contentEquals(remote.stored!!))
        assertEquals(clock.now.toEpochMilliseconds(), prefs.lastBackupAt.first())
    }
}
