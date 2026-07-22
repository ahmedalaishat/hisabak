package com.hisabak.core.data.local

import androidx.room.withTransaction
import com.hisabak.core.common.Currency
import com.hisabak.di.SeedData
import com.hisabak.feature.brand.data.local.toEntity
import com.hisabak.feature.category.data.local.toEntity
import com.hisabak.feature.transaction.data.local.toEntity

class DatabaseSeeder(
    private val db: HisabakDatabase,
    private val seed: SeedData,
    private val starters: StarterData,
    private val currency: Currency,
) {
    /** Full demo dataset (categories, brands, transactions, limits) for development/staging. */
    suspend fun seedIfEmpty() {
        if (db.categoryDao().count() > 0) return
        db.withTransaction {
            db.categoryDao().upsertAll(seed.categories.map { it.toEntity() })
            db.brandDao().upsertAll(seed.brands.map { it.toEntity() })
            db.transactionDao().upsertAll(seed.transactions.map { it.toEntity() })
            db.categoryLimitDao().upsertAll(seed.categoryLimits.map { it.toEntity(currency) })
        }
    }

    /** Production first-run: just the starter categories so the app is usable, no demo data. */
    suspend fun seedStartersIfEmpty() {
        if (db.categoryDao().count() > 0) return
        db.categoryDao().upsertAll(starters.categories.map { it.toEntity() })
    }
}
