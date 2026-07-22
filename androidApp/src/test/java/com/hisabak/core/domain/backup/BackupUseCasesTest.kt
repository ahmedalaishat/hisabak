package com.hisabak.core.domain.backup

import com.hisabak.core.data.backup.AesGcmBackupCrypto
import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.testutil.FakeBackupRemote
import com.hisabak.testutil.FakeBackupRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.sampleBackupData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUseCasesTest {

    private val codec = JsonBackupCodec()
    private val crypto = AesGcmBackupCrypto()

    private fun runBackup(repo: FakeBackupRepository, remote: FakeBackupRemote, schema: Int = 2) =
        RunBackupUseCase(repo, codec, crypto, remote, TestClock(), appVersionCode = 8, schemaVersion = schema)

    private fun restore(repo: FakeBackupRepository, remote: FakeBackupRemote, schema: Int = 2) =
        RestoreFromRemoteUseCase(repo, codec, crypto, remote, schemaVersion = schema)

    @Test
    fun `encrypted backup round-trips through the remote`() = runTest {
        val remote = FakeBackupRemote()
        assertEquals(BackupRunResult.Success, runBackup(FakeBackupRepository(sampleBackupData()), remote).invoke("pass1234"))
        assertTrue(crypto.isEncrypted(remote.stored!!))

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Success(sampleBackupData().totalRecords), restore(target, remote).invoke("pass1234"))
        assertEquals(sampleBackupData(), target.replacedWith)
    }

    @Test
    fun `unencrypted backup round-trips without a passphrase`() = runTest {
        val remote = FakeBackupRemote()
        runBackup(FakeBackupRepository(sampleBackupData()), remote).invoke(passphrase = null)
        assertTrue(!crypto.isEncrypted(remote.stored!!))

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Success(sampleBackupData().totalRecords), restore(target, remote).invoke(null))
    }

    @Test
    fun `restoring an encrypted backup without a passphrase asks for one`() = runTest {
        val remote = FakeBackupRemote()
        runBackup(FakeBackupRepository(sampleBackupData()), remote).invoke("pass1234")

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.PassphraseRequired, restore(target, remote).invoke(null))
        assertNull(target.replacedWith)
    }

    @Test
    fun `wrong passphrase fails the restore`() = runTest {
        val remote = FakeBackupRemote()
        runBackup(FakeBackupRepository(sampleBackupData()), remote).invoke("right-one")

        val target = FakeBackupRepository()
        assertEquals(RestoreResult.Failure(BackupError.WrongPassphrase), restore(target, remote).invoke("wrong-one"))
    }

    @Test
    fun `backing up with no data fails as Empty and uploads nothing`() = runTest {
        val remote = FakeBackupRemote()
        assertEquals(BackupRunResult.Failure(BackupError.Empty), runBackup(FakeBackupRepository(BackupData()), remote).invoke("pass1234"))
        assertNull(remote.stored)
    }

    @Test
    fun `restore with no remote backup reports nothing to restore`() = runTest {
        assertEquals(RestoreResult.NothingToRestore, restore(FakeBackupRepository(), FakeBackupRemote()).invoke(null))
    }

    @Test
    fun `restore rejects a newer schema`() = runTest {
        val remote = FakeBackupRemote()
        runBackup(FakeBackupRepository(sampleBackupData()), remote, schema = 3).invoke(null)

        val target = FakeBackupRepository()
        assertEquals(
            RestoreResult.Failure(BackupError.UnsupportedVersion(3, 2)),
            restore(target, remote, schema = 2).invoke(null),
        )
        assertNull(target.replacedWith)
    }

    @Test
    fun `upload failure surfaces the remote error`() = runTest {
        val remote = FakeBackupRemote().apply { failWith = BackupError.Network }
        assertEquals(
            BackupRunResult.Failure(BackupError.Network),
            runBackup(FakeBackupRepository(sampleBackupData()), remote).invoke(null),
        )
    }
}
