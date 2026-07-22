package com.hisabak.testutil

import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler

class FakeAutoBackupScheduler : AutoBackupScheduler {
    val calls = mutableListOf<Pair<AutoBackupPeriod, Boolean>>()
    override fun schedule(period: AutoBackupPeriod, enabled: Boolean) {
        calls += period to enabled
    }
}
