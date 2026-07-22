package com.hisabak.feature.backup.presentation

import app.cash.turbine.test
import com.hisabak.core.data.backup.AesGcmBackupCrypto
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
import com.hisabak.testutil.MainDispatcherRule
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.sampleBackupData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val codec = JsonBackupCodec()
    private val crypto = AesGcmBackupCrypto()

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
        val runBackup = RunBackupUseCase(repo, codec, crypto, remote, TestClock(), 8, 2)
        return BackupViewModel(prefs, passphrase, account, authorizer, runBackup, remote, scheduler, TestClock(), analytics)
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
