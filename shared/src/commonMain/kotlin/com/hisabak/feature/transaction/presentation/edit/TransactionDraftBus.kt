package com.hisabak.feature.transaction.presentation.edit

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryType
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The sheet's typed-but-unsaved input, parked across the brand-editor detour. The detour must
 * close and reopen the sheet (a full screen can't stack on the bottom-sheet entry), which
 * clears its ViewModel — the reopened sheet restores a draft whose [transactionId] matches
 * instead of starting blank (new) or reloading the stored values (edit).
 */
data class TransactionEditDraft(
    val transactionId: String?,
    val amountInput: String,
    val selectedType: CategoryType,
    val isWithdrawal: Boolean,
    val selectedBrandId: BrandId?,
    val noteInput: String,
    val occurredAt: Instant,
    val fromSms: Boolean,
)

class TransactionDraftBus {
    val pending = MutableStateFlow<TransactionEditDraft?>(null)

    fun publish(draft: TransactionEditDraft) {
        pending.value = draft
    }

    fun consume() {
        pending.value = null
    }
}
