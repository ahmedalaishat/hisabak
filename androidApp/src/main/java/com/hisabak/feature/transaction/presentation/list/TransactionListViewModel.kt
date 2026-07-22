package com.hisabak.feature.transaction.presentation.list

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.usecase.DeleteTransactionUseCase
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class TransactionListViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeBrands: ObserveBrandsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val clock: Clock,
    private val filterBus: TransactionListFilterBus,
    private val analytics: Analytics,
) : BaseViewModel<TransactionListIntent, TransactionListUiState, TransactionListEffect>() {

    override fun initialState() = TransactionListUiState()

    init {
        observeRows()
        // Apply filter requests routed from elsewhere (e.g. the dashboard uncategorized card).
        filterBus.pending
            .filterNotNull()
            .onEach { request ->
                applyRequest(request)
                filterBus.consume()
            }
            .launchIn(viewModelScope)
    }

    private fun applyRequest(request: TransactionListFilterRequest) {
        when (request) {
            TransactionListFilterRequest.Uncategorized -> setState {
                copy(
                    categoryFilter = UncategorizedCategoryId,
                    brandFilter = null,
                    dateRange = DateRangeFilter.ALL,
                    search = "",
                )
            }
        }
    }

    override fun onIntent(intent: TransactionListIntent) {
        when (intent) {
            is TransactionListIntent.SearchChanged ->
                setState { copy(search = intent.query) }
            is TransactionListIntent.PeriodChanged ->
                setState { copy(period = intent.period) }
            is TransactionListIntent.BrandFilterChanged ->
                setState { copy(brandFilter = intent.id) }
            is TransactionListIntent.CategoryFilterChanged ->
                setState { copy(categoryFilter = intent.id) }
            is TransactionListIntent.DateRangeChanged ->
                setState { copy(dateRange = intent.range) }
            TransactionListIntent.ClearFilters ->
                setState { copy(brandFilter = null, categoryFilter = null, dateRange = DateRangeFilter.ALL) }
            is TransactionListIntent.Delete ->
                viewModelScope.launch {
                    deleteTransaction(intent.id)
                    analytics.log(AnalyticsEvent.TransactionDeleted)
                }
            TransactionListIntent.ConsumeEffect -> clearEffect()
        }
    }

    private data class ListFilters(
        val period: SummaryPeriod,
        val brandFilter: BrandId?,
        val categoryFilter: CategoryId?,
        val dateRange: DateRangeFilter,
    )

    private data class Filters(val search: String, val list: ListFilters)

    private class Derived(
        val rows: List<TransactionRow>,
        val summaryIncome: Long,
        val summaryExpenses: Long,
        val brandOptions: List<BrandFilterOption>,
        val categoryOptions: List<CategoryFilterOption>,
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeRows() {
        val searchFlow = state
            .map { it.search }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        val listFiltersFlow = state
            .map { ListFilters(it.period, it.brandFilter, it.categoryFilter, it.dateRange) }
            .distinctUntilChanged()

        val filtersFlow = combine(searchFlow, listFiltersFlow) { search, list -> Filters(search, list) }

        combine(
            observeTransactions(),
            observeBrands(),
            observeCategories(),
            filtersFlow,
        ) { txs, brands, categories, filters ->
            compute(txs, brands, categories, filters)
        }
            .onEach { derived ->
                setState {
                    copy(
                        rows = derived.rows,
                        summaryIncome = derived.summaryIncome,
                        summaryExpenses = derived.summaryExpenses,
                        brandOptions = derived.brandOptions,
                        categoryOptions = derived.categoryOptions,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun compute(
        txs: List<Transaction>,
        brands: List<Brand>,
        categories: List<Category>,
        filters: Filters,
    ): Derived {
        val zone = ZoneId.systemDefault()
        val now = clock.now()
        val today = LocalDate.ofInstant(now, zone)
        val brandsById = brands.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        fun categoryOf(tx: Transaction): Category? =
            brandsById[tx.brandId]?.categoryId?.let { categoriesById[it] }

        // Summary: scoped only by the period, over all transactions, by type.
        val periodRange = filters.list.period.instantRange(today, zone)
        var income = 0L
        var expenses = 0L
        txs.forEach { tx ->
            val inPeriod = periodRange == null ||
                (!tx.occurredAt.isBefore(periodRange.first) && tx.occurredAt.isBefore(periodRange.second))
            if (inPeriod) when (categoryOf(tx)?.type) {
                CategoryType.INCOME -> income += abs(tx.amount.amountMinor)
                CategoryType.EXPENSES -> expenses += abs(tx.amount.amountMinor)
                else -> Unit
            }
        }

        // List: search + brand + category + rolling date range.
        val list = filters.list
        val from = list.dateRange.days?.let { now.minus(it, ChronoUnit.DAYS) }
        val listTxs = txs
            .filter { tx ->
                val brand = brandsById[tx.brandId]
                val matchesCategory = when (list.categoryFilter) {
                    null -> true
                    UncategorizedCategoryId -> brand?.categoryId == null
                    else -> brand?.categoryId == list.categoryFilter
                }
                (list.brandFilter == null || tx.brandId == list.brandFilter) &&
                    matchesCategory &&
                    (from == null || !tx.occurredAt.isBefore(from)) &&
                    (filters.search.isBlank() ||
                        brand?.name?.contains(filters.search, ignoreCase = true) == true ||
                        tx.note?.contains(filters.search, ignoreCase = true) == true)
            }
            .sortedByDescending { it.occurredAt }

        val categoryOptions = buildList {
            categories.sortedBy { it.name.lowercase() }
                .forEach { add(CategoryFilterOption(it.id, it.name, it.color)) }
            if (txs.any { categoryOf(it) == null }) {
                add(CategoryFilterOption(UncategorizedCategoryId, "Uncategorized", "gray"))
            }
        }

        return Derived(
            rows = buildRows(listTxs, brandsById, categoriesById),
            summaryIncome = income,
            summaryExpenses = expenses,
            brandOptions = brands.sortedBy { it.name.lowercase() }.map { BrandFilterOption(it.id, it.name) },
            categoryOptions = categoryOptions,
        )
    }

    private fun buildRows(
        txs: List<Transaction>,
        brandsById: Map<BrandId, Brand>,
        categoriesById: Map<CategoryId, Category>,
    ): List<TransactionRow> = txs.map { tx ->
        val brand = brandsById[tx.brandId]
        val category = brand?.categoryId?.let { categoriesById[it] }
        TransactionRow(
            id = tx.id,
            amount = tx.amount,
            brandName = brand?.name ?: "Unknown",
            categoryName = category?.name,
            categoryType = category?.type,
            categoryColor = category?.color,
            categoryIcon = category?.icon,
            note = tx.note,
            occurredAt = tx.occurredAt,
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
