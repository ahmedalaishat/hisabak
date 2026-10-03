package com.hisabak.core.domain.backup

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupFileNameTest {

    @Test
    fun `prod keeps the historical file name`() {
        assertEquals("hisabak-backup.bak", backupFileName("prod"))
    }

    @Test
    fun `other flavors get their own file`() {
        assertEquals("hisabak-backup-staging.bak", backupFileName("staging"))
    }

    @Test
    fun `an exported file is dated and keeps prod unmarked`() {
        assertEquals("hisabak-backup-2026-10-02.bak", exportFileName("prod", LocalDate(2026, 10, 2)))
    }

    @Test
    fun `an exported file from another flavor carries its name`() {
        assertEquals("hisabak-backup-staging-2026-01-05.bak", exportFileName("staging", LocalDate(2026, 1, 5)))
    }
}
