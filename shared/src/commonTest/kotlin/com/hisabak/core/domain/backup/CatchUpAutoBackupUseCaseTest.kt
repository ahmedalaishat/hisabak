package com.hisabak.core.domain.backup

import com.hisabak.core.data.backup.JsonBackupCodec
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeBackupCrypto
import com.hisabak.testutil.FakeBackupPassphraseStore
import com.hisabak.testutil.FakeBackupRemote
import com.hisabak.testutil.FakeBackupRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.sampleBackupData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CatchUpAutoBackupUseCaseTest {

    private val clock = TestClock()
    private val crypto = FakeBackupCrypto()

    private fun useCase(
        prefs: FakeAppPreferences,
        remote: FakeBackupRemote = FakeBackupRemote(),
        passphraseStore: FakeBackupPassphraseStore = FakeBackupPassphraseStore(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ): CatchUpAutoBackupUseCase {
        val runBackup = RunBackupUseCase(
            FakeBackupRepository(sampleBackupData()), JsonBackupCodec(), crypto, remote, clock, prefs,
            appVersionCode = 8, schemaVersion = 2,
        )
        return CatchUpAutoBackupUseCase(prefs, passphraseStore, runBackup, clock, analytics)
    }

    private suspend fun dailyPrefs(lastBackupAgoMillis: Long? = null) = FakeAppPreferences().apply {
        setBackupEnabled(true)
        setAutoBackupPeriod(AutoBackupPeriod.DAILY)
        if (lastBackupAgoMillis != null) {
            setLastBackupAt(clock.now.toEpochMilliseconds() - lastBackupAgoMillis)
        }
    }

    @Test
    fun `runs and stamps when the last backup is older than the period`() = runTest {
        val prefs = dailyPrefs(lastBackupAgoMillis = 2.days.inWholeMilliseconds)
        val remote = FakeBackupRemote()
        val analytics = FakeAnalytics()

        useCase(prefs, remote, analytics = analytics).invoke()

        assertNotNull(remote.stored)
        assertEquals(clock.now.toEpochMilliseconds(), prefs.lastBackupAt.first())
        assertTrue(analytics.names().contains("backup_run_completed"))
    }

    @Test
    fun `runs when no backup was ever recorded`() = runTest {
        val remote = FakeBackupRemote()
        useCase(dailyPrefs(), remote).invoke()
        assertNotNull(remote.stored)
    }

    @Test
    fun `skips when the last backup is recent`() = runTest {
        val remote = FakeBackupRemote()
        useCase(dailyPrefs(lastBackupAgoMillis = 2.hours.inWholeMilliseconds), remote).invoke()
        assertNull(remote.stored)
    }

    @Test
    fun `skips when backup is disabled`() = runTest {
        val prefs = dailyPrefs().apply { setBackupEnabled(false) }
        val remote = FakeBackupRemote()
        useCase(prefs, remote).invoke()
        assertNull(remote.stored)
    }

    @Test
    fun `skips when the period is NEVER`() = runTest {
        val prefs = dailyPrefs().apply { setAutoBackupPeriod(AutoBackupPeriod.NEVER) }
        val remote = FakeBackupRemote()
        useCase(prefs, remote).invoke()
        assertNull(remote.stored)
    }

    @Test
    fun `skips when encryption is on but no passphrase is stored`() = runTest {
        val prefs = dailyPrefs().apply { setBackupEncryptionEnabled(true) }
        val remote = FakeBackupRemote()
        useCase(prefs, remote).invoke()
        assertNull(remote.stored)
    }

    @Test
    fun `encrypts with the stored passphrase`() = runTest {
        val prefs = dailyPrefs().apply { setBackupEncryptionEnabled(true) }
        val remote = FakeBackupRemote()
        val store = FakeBackupPassphraseStore().apply { set("pass1234") }

        useCase(prefs, remote, passphraseStore = store).invoke()

        assertTrue(crypto.isEncrypted(remote.stored!!))
    }

    @Test
    fun `a completed catch-up makes the next call a no-op`() = runTest {
        val prefs = dailyPrefs(lastBackupAgoMillis = 2.days.inWholeMilliseconds)
        val analytics = FakeAnalytics()
        val useCase = useCase(prefs, analytics = analytics)

        useCase()
        useCase()

        assertEquals(1, analytics.names().count { it == "backup_run_completed" })
    }

    @Test
    fun `a failed upload does not stamp lastBackupAt`() = runTest {
        val prefs = dailyPrefs()
        val remote = FakeBackupRemote().apply { failWith = BackupError.Network }

        useCase(prefs, remote).invoke()

        assertEquals(0L, prefs.lastBackupAt.first())
    }
}
