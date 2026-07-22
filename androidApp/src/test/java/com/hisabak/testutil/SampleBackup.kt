package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupData
import com.hisabak.core.domain.backup.BrandRecord
import com.hisabak.core.domain.backup.CategoryLimitRecord
import com.hisabak.core.domain.backup.CategoryRecord
import com.hisabak.core.domain.backup.SmsMessageRecord
import com.hisabak.core.domain.backup.TransactionRecord

/** A small, fully-populated snapshot (one row per table) for backup tests. */
fun sampleBackupData() = BackupData(
    categories = listOf(
        CategoryRecord("c1", "Food", "EXPENSES", "orange", "cart"),
        CategoryRecord("c2", "Old", "EXPENSES", "gray", "wallet"),
    ),
    categoryLimits = listOf(CategoryLimitRecord("c1", 202606, 5000, "AED")),
    brands = listOf(BrandRecord("b1", "Cafe", "c1")),
    transactions = listOf(
        TransactionRecord("t1", 1234, "AED", "b1", "lunch", 2L, null),
    ),
    smsMessages = listOf(
        SmsMessageRecord("s1", "Purchase of AED 12.34", 3L, "t1", "Cafe", 1234, "AED", 2L),
    ),
)
