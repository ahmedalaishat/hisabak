package com.hisabak.feature.category.presentation.list

import androidx.lifecycle.viewModelScope
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.usecase.DeleteCategoryUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CategoryListViewModel(
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeBrands: ObserveBrandsUseCase,
    private val observeTransactions: ObserveTransactionsUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : BaseViewModel<CategoryListIntent, CategoryListUiState, CategoryListEffect>() {

    override fun initialState() = CategoryListUiState()

    init {
        observeRowsBasedOnSearchAndFilter()
    }

    override fun onIntent(intent: CategoryListIntent) {
        when (intent) {
            is CategoryListIntent.SearchChanged ->
                setState { copy(search = intent.query) }
            is CategoryListIntent.TypeFilterChanged ->
                setState { copy(typeFilter = intent.type) }
            is CategoryListIntent.Delete ->
                viewModelScope.launch { deleteCategory(intent.id) }
            CategoryListIntent.ConsumeEffect -> clearEffect()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeRowsBasedOnSearchAndFilter() {
        val searchFlow = state.map { it.search }.distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        val filterFlow = state.map { it.typeFilter }.distinctUntilChanged()

        combine(searchFlow, filterFlow) { search, type -> search to type }
            .flatMapLatest { (search, type) ->
                combine(
                    observeCategories(
                        type = type,
                        search = search.takeIf { it.isNotBlank() },
                    ),
                    observeBrands(),
                    observeTransactions(),
                ) { categories, brands, transactions -> buildRows(categories, brands, transactions) }
            }
            .onEach { rows -> setState { copy(rows = rows, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun buildRows(
        categories: List<Category>,
        brands: List<Brand>,
        transactions: List<Transaction>,
    ): List<CategoryRow> {
        val categoryByBrand = brands.associate { it.id to it.categoryId }
        val withCategory = transactions.mapNotNull { tx ->
            categoryByBrand[tx.brandId]?.let { it to tx }
        }
        val countByCategory = withCategory.groupingBy { it.first }.eachCount()
        val totalByCategory = withCategory
            .groupBy({ it.first }) { it.second.amount.amountMinor }
            .mapValues { (_, amounts) -> amounts.sum() }
        return categories.map {
            CategoryRow(
                id = it.id,
                name = it.name,
                type = it.type,
                color = it.color,
                icon = it.icon,
                transactionCount = countByCategory[it.id] ?: 0,
                totalMinor = totalByCategory[it.id] ?: 0L,
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
