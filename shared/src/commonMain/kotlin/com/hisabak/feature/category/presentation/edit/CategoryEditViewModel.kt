package com.hisabak.feature.category.presentation.edit

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.common.sanitizeAmountInput
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.category.domain.CategoryColor
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.effectiveFor
import com.hisabak.feature.category.domain.usecase.CreateCategoryUseCase
import com.hisabak.feature.category.domain.usecase.DeleteCategoryUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.category.domain.usecase.SetCategoryLimitUseCase
import com.hisabak.feature.category.domain.usecase.UpdateCategoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.yearMonth

class CategoryEditViewModel(
    private val categoryId: CategoryId?,
    prefill: CategoryEditPrefill?,
    private val proposedLimitMinor: Long?,
    private val categoryRepository: CategoryRepository,
    private val createCategory: CreateCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
    private val observeCategoryLimits: ObserveCategoryLimitsUseCase,
    private val setCategoryLimit: SetCategoryLimitUseCase,
    private val currency: Currency,
    private val clock: Clock,
    private val analytics: Analytics,
) : BaseViewModel<CategoryEditIntent, CategoryEditUiState, CategoryEditEffect>() {

    private val prefilledColor: String? = prefill?.color

    override fun initialState() = CategoryEditUiState(isNew = categoryId == null)

    init {
        loadColorsInUse()
        if (categoryId != null) {
            loadExisting(categoryId)
        } else if (prefill != null) {
            setState {
                copy(nameInput = prefill.name, type = prefill.type, color = prefill.color, icon = prefill.icon)
            }
        }
    }

    /**
     * The other categories' colors, for the picker's context strip and its clash warning. A new
     * category with no prefill also *starts* on the hue furthest from them, so two categories
     * don't end up as indistinguishable slices of the same donut by accident.
     */
    private fun loadColorsInUse() {
        viewModelScope.launch {
            val others = categoryRepository.observeAll().first().filter { it.id != categoryId }
            val used = others.map { UsedCategoryColor(it.name, it.color, it.icon) }
            setState {
                val pickDefault = categoryId == null && prefilledColor == null
                copy(
                    colorsInUse = used,
                    color = if (pickDefault) {
                        CategoryColor.customKey(
                            CategoryColor.mostDistinctHue(used.mapNotNull { CategoryColor.hueFor(it.colorKey) }),
                        )
                    } else {
                        color
                    },
                )
            }
        }
    }

    override fun onIntent(intent: CategoryEditIntent) {
        when (intent) {
            is CategoryEditIntent.NameChanged ->
                setState { copy(nameInput = intent.value, nameError = null) }
            is CategoryEditIntent.TypeChanged ->
                setState { copy(type = intent.value) }
            is CategoryEditIntent.ColorChanged ->
                setState { copy(color = intent.value) }
            is CategoryEditIntent.IconChanged ->
                setState { copy(icon = intent.value) }
            is CategoryEditIntent.LimitChanged ->
                setState { copy(limitInput = sanitizeAmountInput(intent.value), limitError = null) }
            CategoryEditIntent.Save -> save()
            CategoryEditIntent.Delete -> delete()
            CategoryEditIntent.ConsumeEffect -> clearEffect()
        }
    }

    private fun loadExisting(id: CategoryId) {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = categoryRepository.getById(id)) {
                is DomainResult.Success -> {
                    val c = result.value
                    val limit = observeCategoryLimits().first()
                        .effectiveFor(id, clock.today().yearMonth)
                    setState {
                        copy(
                            isLoading = false,
                            nameInput = c.name,
                            type = c.type,
                            color = c.color,
                            icon = c.icon,
                            // A proposed cap is confirm-first: it fills the field, and Save is the confirmation.
                            limitInput = (proposedLimitMinor ?: limit?.amountMinor)?.let { majorString(it) } ?: "",
                        )
                    }
                }
                is DomainResult.Failure -> setState {
                    copy(isLoading = false, generalError = result.error.message)
                }
            }
        }
    }

    /** Only reachable for an existing category — a new one has nothing to delete. */
    private fun delete() {
        val id = categoryId ?: return
        setState { copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = deleteCategory(id)) {
                is DomainResult.Success -> sendEffect(CategoryEditEffect.Deleted)
                is DomainResult.Failure -> {
                    setState { copy(isDeleting = false) }
                    sendEffect(CategoryEditEffect.DeleteFailed(result.error.message))
                }
            }
        }
    }

    private fun save() {
        val s = state.value
        val name = s.nameInput.trim()
        if (name.isEmpty()) {
            setState { copy(nameError = "Name is required") }
            return
        }

        val limit: Money?
        if (s.showLimit && s.limitInput.isNotBlank()) {
            val parsed = Money.parseMajor(s.limitInput, currency)
            if (parsed == null || !parsed.isPositive) {
                setState { copy(limitError = "Enter a valid amount") }
                return
            }
            limit = parsed
        } else {
            limit = null
        }

        setState { copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            val saved: DomainResult<CategoryId> = if (categoryId == null) {
                createCategory(name = name, type = s.type, color = s.color, icon = s.icon).map { it.id }
            } else {
                when (val existing = categoryRepository.getById(categoryId)) {
                    is DomainResult.Success -> updateCategory(
                        existing.value.copy(name = name, type = s.type, color = s.color, icon = s.icon),
                    ).map { categoryId }
                    is DomainResult.Failure -> DomainResult.Failure(existing.error)
                }
            }

            when (saved) {
                is DomainResult.Success -> {
                    // Persist the limit only for expense categories; clears (null) when blank.
                    if (s.showLimit) setCategoryLimit(saved.value, limit)
                    if (categoryId == null) {
                        analytics.log(
                            AnalyticsEvent.CategoryCreated(type = s.type.name.lowercase(), hasLimit = limit != null),
                        )
                    }
                    setState { copy(isSaving = false) }
                    sendEffect(CategoryEditEffect.Saved(saved.value))
                }
                is DomainResult.Failure -> setState {
                    copy(isSaving = false, generalError = saved.error.message)
                }
            }
        }
    }

    private fun majorString(amountMinor: Long): String {
        val major = amountMinor / 100.0
        return if (major % 1.0 == 0.0) major.toLong().toString() else major.toString()
    }
}
