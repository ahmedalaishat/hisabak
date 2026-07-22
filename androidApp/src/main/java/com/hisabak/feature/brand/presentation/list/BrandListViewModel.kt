package com.hisabak.feature.brand.presentation.list

import androidx.lifecycle.viewModelScope
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.usecase.DeleteBrandUseCase
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.feature.transaction.domain.usecase.ReassignBrandTransactionsUseCase
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BrandListViewModel(
    private val observeBrands: ObserveBrandsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeTransactions: ObserveTransactionsUseCase,
    private val deleteBrand: DeleteBrandUseCase,
    private val reassignBrandTransactions: ReassignBrandTransactionsUseCase,
    private val analytics: Analytics,
) : BaseViewModel<BrandListIntent, BrandListUiState, BrandListEffect>() {

    override fun initialState() = BrandListUiState()

    init {
        observeRows()
        observeAvailableCategoryOptions()
    }

    override fun onIntent(intent: BrandListIntent) {
        when (intent) {
            is BrandListIntent.SearchChanged ->
                setState { copy(search = intent.query) }
            is BrandListIntent.CategoryFilterChanged ->
                setState { copy(categoryFilter = intent.categoryId) }
            is BrandListIntent.Delete ->
                viewModelScope.launch {
                    if (deleteBrand(intent.id) is DomainResult.Failure) {
                        sendEffect(BrandListEffect.Message("Couldn't delete this brand — it may now have transactions. Merge it instead."))
                    }
                }
            is BrandListIntent.MergeAndDelete ->
                viewModelScope.launch {
                    if (reassignBrandTransactions(intent.sourceId, intent.targetId) is DomainResult.Success) {
                        analytics.log(AnalyticsEvent.BrandMerged)
                        if (deleteBrand(intent.sourceId) is DomainResult.Failure) {
                            sendEffect(BrandListEffect.Message("Moved the transactions, but couldn't delete the brand."))
                        }
                    } else {
                        sendEffect(BrandListEffect.Message("Couldn't move the transactions. Nothing was deleted."))
                    }
                }
            BrandListIntent.ConsumeEffect -> clearEffect()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeRows() {
        val searchFlow = state.map { it.search }.distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        val filterFlow = state.map { it.categoryFilter }.distinctUntilChanged()

        combine(searchFlow, filterFlow) { search, categoryId -> search to categoryId }
            .flatMapLatest { (search, categoryId) ->
                combine(
                    observeBrands(
                        search = search.takeIf { it.isNotBlank() },
                        categoryId = categoryId,
                    ),
                    observeCategories(),
                    observeTransactions(),
                ) { brands, categories, transactions -> buildRows(brands, categories, transactions) }
            }
            .onEach { rows -> setState { copy(rows = rows, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun observeAvailableCategoryOptions() {
        observeCategories()
            .onEach { categories ->
                val options = categories.map {
                    BrandListUiState.CategoryOption(id = it.id, name = it.name, color = it.color)
                }
                setState { copy(availableCategories = options) }
            }
            .launchIn(viewModelScope)
    }

    private fun buildRows(
        brands: List<Brand>,
        categories: List<Category>,
        transactions: List<Transaction>,
    ): List<BrandRow> {
        val byId: Map<CategoryId, Category> = categories.associateBy { it.id }
        val countByBrand = transactions.groupingBy { it.brandId }.eachCount()
        val totalByBrand = transactions
            .groupBy({ it.brandId }) { it.amount.amountMinor }
            .mapValues { (_, amounts) -> amounts.sum() }
        return brands.map { brand ->
            val category = brand.categoryId?.let { byId[it] }
            BrandRow(
                id = brand.id,
                name = brand.name,
                categoryId = brand.categoryId,
                categoryName = category?.name,
                categoryColor = category?.color,
                categoryIcon = category?.icon,
                transactionCount = countByBrand[brand.id] ?: 0,
                totalMinor = totalByBrand[brand.id] ?: 0L,
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
