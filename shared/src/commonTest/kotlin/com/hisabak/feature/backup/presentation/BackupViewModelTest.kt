package com.hisabak.feature.backup.presentation

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.launch
import com.hisabak.core.domain.backup.RestoreResult
import com.hisabak.core.domain.backup.RestoreFromBytesUseCase
import com.hisabak.core.domain.backup.BuildBackupBytesUseCase
import com.hisabak.core.domain.backup.BackupData
import com.hisabak.core.domain.backup.BackupBytesResult
import app.cash.turbine.test
import com.hisabak.testutil.FakeBackupCrypto
import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.RunBackupUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeAutoBackupScheduler
import com.hisabak.testutil.FakeBackupAccountStore
import com.hisabak.testutil.FakeBackupPassphraseStore
import com.hisabak.testutil.FakeBackupRemote
import com.hisabak.testutil.FakeBackupRepository
import com.hisabak.testutil.FakeDriveAuthorizer
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.sampleBackupData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest : MainDispatcherTest() {

    private val codec = JsonBackupCodec()
    private val crypto = FakeBackupCrypto()

    private fun viewModel(
        prefs: FakeAppPreferences = FakeAppPreferences(),
        passphrase: FakeBackupPassphraseStore = FakeBackupPassphraseStore(),
        account: FakeBackupAccountStore = FakeBackupAccountStore(),
        authorizer: FakeDriveAuthorizer = FakeDriveAuthorizer(),
        remote: FakeBackupRemote = FakeBackupRemote(),
        repo: FakeBackupRepository = FakeBackupRepository(sampleBackupData()),
        scheduler: FakeAutoBackupScheduler = FakeAutoBackupScheduler(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ): BackupViewModel {
        val buildBytes = BuildBackupBytesUseCase(repo, codec, crypto, TestClock(), 8, 2)
        val runBackup = RunBackupUseCase(buildBytes, remote, TestClock(), prefs)
        val restoreBytes = RestoreFromBytesUseCase(repo, codec, crypto, schemaVersion = 2)
        return BackupViewModel(
            prefs, passphrase, account, authorizer, runBackup, buildBytes, restoreBytes, remote, scheduler,
            TestClock(), flavor = "prod", analytics = analytics,
        )
    }

    /** Keeps the WhileSubscribed state hot so tests can read `state.value` after acting. */
    private fun TestScope.observe(vm: BackupViewModel): BackupViewModel {
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    /** Runs an export through to the platform saver, capturing what it was handed. */
    private fun TestScope.export(vm: BackupViewModel, outcome: (ByteArray) -> Boolean = { true }): ByteArray? {
        var handed: ByteArray? = null
        vm.exportFile()
        advanceUntilIdle()
        vm.saveExport { bytes -> handed = bytes; outcome(bytes) }
        advanceUntilIdle()
        return handed
    }

    private suspend fun fileBytes(passphrase: String?, schema: Int = 2): ByteArray {
        val built = BuildBackupBytesUseCase(FakeBackupRepository(sampleBackupData()), codec, crypto, TestClock(), 8, schema)
            .invoke(passphrase)
        return (built as BackupBytesResult.Success).bytes
    }

    private fun TestScope.pick(vm: BackupViewModel, bytes: ByteArray) {
        vm.onImportFilePicked { bytes }
        advanceUntilIdle()
    }

    @Test
    fun `export asks the platform to save a dated file`() = runTest {
        val vm = observe(viewModel())
        vm.exportFile()
        advanceUntilIdle()

        assertEquals(BackupEffect.SaveFile("hisabak-backup-2026-06-17.bak"), vm.effect.value)
        assertTrue(vm.state.value.exporting)
    }

    @Test
    fun `export works with backup off and no account and writes a plain file`() = runTest {
        val analytics = FakeAnalytics()
        val vm = observe(viewModel(analytics = analytics)) // backup disabled, nothing connected

        val handed = export(vm)

        assertTrue(handed != null && !crypto.isEncrypted(handed))
        assertEquals(SyncKind.Export, vm.state.value.syncKind)
        assertEquals(SyncPhase.Done(), vm.state.value.sync)
        assertEquals(false, vm.state.value.exporting)
        val event = analytics.logged.single { it.name == "backup_file_exported" }
        assertEquals(mapOf("success" to true, "encrypted" to false, "error" to null), event.params)
    }

    @Test
    fun `export encrypts with the stored passphrase when encryption is on`() = runTest {
        val prefs = FakeAppPreferences().apply { setBackupEnabled(true); setBackupEncryptionEnabled(true) }
        val passphrase = FakeBackupPassphraseStore().apply { set("secret123") }
        val vm = observe(viewModel(prefs = prefs, passphrase = passphrase))

        val handed = export(vm)!!

        assertTrue(crypto.isEncrypted(handed))
        val target = FakeBackupRepository()
        assertEquals(
            RestoreResult.Success(sampleBackupData().totalRecords),
            RestoreFromBytesUseCase(target, codec, crypto, 2).invoke(handed, "secret123"),
        )
    }

    @Test
    fun `export never stamps the last Drive backup`() = runTest {
        val prefs = FakeAppPreferences()
        val vm = observe(viewModel(prefs = prefs))

        export(vm)

        assertEquals(0L, prefs.lastBackupAt.first())
    }

    @Test
    fun `a cancelled save goes quietly back to the settings`() = runTest {
        val analytics = FakeAnalytics()
        val vm = observe(viewModel(analytics = analytics))

        export(vm) { false }

        assertEquals(null, vm.state.value.sync)
        assertEquals(false, vm.state.value.exporting)
        assertTrue("backup_file_exported" !in analytics.names())
    }

    @Test
    fun `a failed write reports a file error`() = runTest {
        val analytics = FakeAnalytics()
        val vm = observe(viewModel(analytics = analytics))

        export(vm) { error("disk full") }

        assertEquals(SyncPhase.Failed(BackupError.FileAccess), vm.state.value.sync)
        assertEquals(SyncKind.Export, vm.state.value.syncKind)
        assertEquals("file_access", analytics.logged.single { it.name == "backup_file_exported" }.params["error"])
    }

    @Test
    fun `exporting with no data shows the empty error and saves nothing`() = runTest {
        val vm = observe(viewModel(repo = FakeBackupRepository(BackupData())))
        vm.exportFile()
        advanceUntilIdle()

        assertEquals(BackupError.Empty, vm.state.value.error)
        assertEquals(null, vm.effect.value)
        assertEquals(false, vm.state.value.exporting)
    }

    @Test
    fun `a picked file asks for confirmation before replacing anything`() = runTest {
        val repo = FakeBackupRepository()
        val vm = observe(viewModel(repo = repo))

        pick(vm, fileBytes(null))

        assertEquals(ImportStep.Confirm, vm.state.value.importStep)
        assertEquals(null, repo.replacedWith)
    }

    @Test
    fun `confirming a plain file restores it and reports the count`() = runTest {
        val repo = FakeBackupRepository()
        val analytics = FakeAnalytics()
        val vm = observe(viewModel(repo = repo, analytics = analytics))

        pick(vm, fileBytes(null))
        vm.confirmImport()
        advanceUntilIdle()

        assertEquals(sampleBackupData(), repo.replacedWith)
        assertEquals(SyncKind.Import, vm.state.value.syncKind)
        assertEquals(SyncPhase.Done(sampleBackupData().totalRecords), vm.state.value.sync)
        assertEquals(null, vm.state.value.importStep)
        assertEquals(mapOf("success" to true, "error" to null), analytics.logged.single { it.name == "backup_file_imported" }.params)
    }

    @Test
    fun `cancelling the confirmation keeps the data`() = runTest {
        val repo = FakeBackupRepository()
        val vm = observe(viewModel(repo = repo))

        pick(vm, fileBytes(null))
        vm.cancelImport()
        advanceUntilIdle()
        vm.confirmImport() // the file was dropped with the cancel
        advanceUntilIdle()

        assertEquals(null, vm.state.value.importStep)
        assertEquals(null, repo.replacedWith)
    }

    @Test
    fun `an encrypted file asks for the passphrase then retries a wrong one`() = runTest {
        val repo = FakeBackupRepository()
        val vm = observe(viewModel(repo = repo))

        pick(vm, fileBytes("right-one"))
        vm.confirmImport()
        advanceUntilIdle()
        assertEquals(ImportStep.Passphrase(), vm.state.value.importStep)
        assertEquals(null, vm.state.value.sync)

        vm.submitImportPassphrase("wrong-one")
        advanceUntilIdle()
        assertEquals(ImportStep.Passphrase(BackupError.WrongPassphrase), vm.state.value.importStep)
        assertEquals(null, repo.replacedWith)

        vm.submitImportPassphrase("right-one")
        advanceUntilIdle()
        assertEquals(SyncPhase.Done(sampleBackupData().totalRecords), vm.state.value.sync)
        assertEquals(sampleBackupData(), repo.replacedWith)
    }

    @Test
    fun `a file that is not a backup fails as corrupt`() = runTest {
        val repo = FakeBackupRepository()
        val analytics = FakeAnalytics()
        val vm = observe(viewModel(repo = repo, analytics = analytics))

        pick(vm, "hello".encodeToByteArray())
        vm.confirmImport()
        advanceUntilIdle()

        assertEquals(SyncPhase.Failed(BackupError.Corrupt), vm.state.value.sync)
        assertEquals(null, repo.replacedWith)
        assertEquals("corrupt", analytics.logged.single { it.name == "backup_file_imported" }.params["error"])
    }

    @Test
    fun `a file from a newer app is rejected`() = runTest {
        val repo = FakeBackupRepository()
        val vm = observe(viewModel(repo = repo))

        pick(vm, fileBytes(null, schema = 3))
        vm.confirmImport()
        advanceUntilIdle()

        assertEquals(SyncPhase.Failed(BackupError.UnsupportedVersion(3, 2)), vm.state.value.sync)
        assertEquals(null, repo.replacedWith)
    }

    @Test
    fun `an unreadable file shows a file error without asking to confirm`() = runTest {
        val vm = observe(viewModel())

        vm.onImportFilePicked { error("permission revoked") }
        advanceUntilIdle()

        assertEquals(BackupError.FileAccess, vm.state.value.error)
        assertEquals(null, vm.state.value.importStep)
    }

    @Test
    fun `disabling clears the passphrase`() = runTest {
        val passphrase = FakeBackupPassphraseStore().apply { set("secret123") }
        val prefs = FakeAppPreferences().apply { setBackupEnabled(true) }
        viewModel(prefs = prefs, passphrase = passphrase).setEnabled(false)
        advanceUntilIdle()
        assertEquals(null, passphrase.get())
    }

    @Test
    fun `connecting a granted account stores it and logs`() = runTest {
        val account = FakeBackupAccountStore()
        val analytics = FakeAnalytics()
        val vm = viewModel(account = account, analytics = analytics)

        vm.connect(onNeedConsent = {})
        advanceUntilIdle()

        assertEquals(BackupAccount("user@example.com"), account.account.first())
        assertTrue(analytics.names().contains("backup_account_connected"))
    }

    @Test
    fun `backupNow uploads and reports success`() = runTest {
        val remote = FakeBackupRemote()
        val account = FakeBackupAccountStore().apply { set(BackupAccount("user@example.com")) }
        val prefs = FakeAppPreferences().apply { setBackupEncryptionEnabled(false) }
        val analytics = FakeAnalytics()
        val vm = viewModel(prefs = prefs, account = account, remote = remote, analytics = analytics)

        vm.state.test {
            vm.backupNow()
            advanceUntilIdle()
            assertEquals(SyncPhase.Done(), expectMostRecentItem().sync)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(remote.stored != null)
        assertTrue(analytics.names().contains("backup_run_completed"))
    }

    @Test
    fun `backupNow with dead authorization disconnects the account`() = runTest {
        val remote = FakeBackupRemote().apply { failWith = BackupError.AuthRequired }
        val account = FakeBackupAccountStore().apply { set(BackupAccount("user@example.com")) }
        val vm = viewModel(account = account, remote = remote)

        vm.state.test {
            vm.backupNow()
            advanceUntilIdle()
            assertEquals(SyncPhase.Failed(BackupError.AuthRequired), expectMostRecentItem().sync)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, account.account.first())
    }

    @Test
    fun `backupNow keeps the account on a network failure`() = runTest {
        val remote = FakeBackupRemote().apply { failWith = BackupError.Network }
        val account = FakeBackupAccountStore().apply { set(BackupAccount("user@example.com")) }
        val vm = viewModel(account = account, remote = remote)

        vm.backupNow()
        advanceUntilIdle()

        assertEquals(BackupAccount("user@example.com"), account.account.first())
    }

    @Test
    fun `backupNow without an account asks to connect`() = runTest {
        val vm = viewModel() // no account set
        vm.state.test {
            vm.backupNow()
            advanceUntilIdle()
            assertEquals(BackupError.AuthRequired, expectMostRecentItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAutoBackupPeriod persists and logs`() = runTest {
        val prefs = FakeAppPreferences()
        val analytics = FakeAnalytics()
        val vm = viewModel(prefs = prefs, analytics = analytics)

        vm.state.test {
            awaitItem() // initial
            vm.setAutoBackupPeriod(AutoBackupPeriod.DAILY)
            advanceUntilIdle()
            assertEquals(AutoBackupPeriod.DAILY, prefs.autoBackupPeriod.first())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(analytics.names().contains("auto_backup_period_set"))
    }

    @Test
    fun `setAutoBackupPeriod schedules with the current enabled state`() = runTest {
        val prefs = FakeAppPreferences().apply { setBackupEnabled(true) }
        val scheduler = FakeAutoBackupScheduler()
        val vm = viewModel(prefs = prefs, scheduler = scheduler)

        vm.setAutoBackupPeriod(AutoBackupPeriod.WEEKLY)
        advanceUntilIdle()

        assertTrue(scheduler.calls.contains(AutoBackupPeriod.WEEKLY to true))
    }

    @Test
    fun `disabling backup cancels the schedule`() = runTest {
        val prefs = FakeAppPreferences().apply { setBackupEnabled(true) }
        val scheduler = FakeAutoBackupScheduler()
        val vm = viewModel(prefs = prefs, scheduler = scheduler)

        vm.setEnabled(false)
        advanceUntilIdle()

        assertTrue(scheduler.calls.any { !it.second }) // scheduled with enabled = false
    }
}
