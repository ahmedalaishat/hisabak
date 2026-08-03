package com.hisabak.core.domain.backup

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
}
