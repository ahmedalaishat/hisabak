package com.hisabak.feature.transaction.presentation.edit

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryType
import java.time.Instant

data class TransactionEditUiState(
    val amountInput: String = "",
    val selectedBrandId: BrandId? = null,
    val brandOptions: List<BrandOption> = emptyList(),
    val noteInput: String = "",
    val occurredAt: Instant = Instant.EPOCH,
    val selectedType: CategoryType = CategoryType.EXPENSES,
    val showDatePicker: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val amountError: String? = null,
    val brandError: String? = null,
    val generalError: String? = null,
) : ViewState {
    data class BrandOption(
        val id: BrandId,
        val name: String,
        val categoryColor: String?,
    )

    val canSave: Boolean
        get() = !isSaving && amountInput.isNotBlank() && selectedBrandId != null
}

sealed interface TransactionEditIntent : ViewIntent {
    data class AmountChanged(val value: String) : TransactionEditIntent
    data class BrandSelected(val brandId: BrandId) : TransactionEditIntent
    data class NoteChanged(val value: String) : TransactionEditIntent
    data class DateChanged(val instant: Instant) : TransactionEditIntent
    data class TypeSelected(val type: CategoryType) : TransactionEditIntent
    data object DatePickerOpened : TransactionEditIntent
    data object DatePickerDismissed : TransactionEditIntent
    data object Save : TransactionEditIntent
    data object ConsumeEffect : TransactionEditIntent
}

sealed interface TransactionEditEffect : ViewEffect {
    data object Saved : TransactionEditEffect
}
