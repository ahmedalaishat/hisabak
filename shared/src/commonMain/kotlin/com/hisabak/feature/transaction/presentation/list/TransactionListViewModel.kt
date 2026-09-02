package com.hisabak.feature.transaction.presentation.list

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TransactionListViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeBrands: ObserveBrandsUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val clock: Clock,
    private val filterBus: TransactionListFilterBus,
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
            // Arriving from a brand or category row: clear every other filter, or the list could
            // land empty for reasons the user set on a different screen and can no longer see.
            is TransactionListFilterRequest.ByBrand -> setState {
                copy(
                    brandFilter = request.id,
                    categoryFilter = null,
                    dateRange = DateRangeFilter.ALL,
                    search = "",
                )
            }
            is TransactionListFilterRequest.ByCategory -> setState {
                copy(
                    categoryFilter = request.id,
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
                // Choosing a category re-scopes the brand list, so a brand held over from the old
                // scope would usually filter everything away — an empty list with two pills lit
                // and no obvious culprit. Clearing to "All brands" is the predictable reading of
                // "show me this category". Widening back to All keeps whatever brand was set.
                setState {
                    copy(
                        categoryFilter = intent.id,
                        brandFilter = if (intent.id == null) brandFilter else null,
                    )
                }
            is TransactionListIntent.DateRangeChanged ->
                setState { copy(dateRange = intent.range) }
            TransactionListIntent.ClearFilters ->
                setState { copy(brandFilter = null, categoryFilter = null, dateRange = DateRangeFilter.ALL) }
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
        val totalCount: Int,
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
                        totalCount = derived.totalCount,
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
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        val today = now.toLocalDateTime(zone).date
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
                (tx.occurredAt >= periodRange.first && tx.occurredAt < periodRange.second)
            if (inPeriod) when (categoryOf(tx)?.type) {
                CategoryType.INCOME -> income += abs(tx.amount.amountMinor)
                CategoryType.EXPENSES -> expenses += abs(tx.amount.amountMinor)
                else -> Unit
            }
        }

        // List: search + brand + category + rolling date range.
        val list = filters.list

        val from = list.dateRange.days?.let { now - it.days }
        val listTxs = txs
            .filter { tx ->
                val brand = brandsById[tx.brandId]
                val matchesCategory = brand != null && brandMatchesCategory(brand, list.categoryFilter)
                (list.brandFilter == null || tx.brandId == list.brandFilter) &&
                    matchesCategory &&
                    (from == null || tx.occurredAt >= from) &&
                    (filters.search.isBlank() ||
                        brand?.name?.contains(filters.search, ignoreCase = true) == true ||
                        tx.note?.contains(filters.search, ignoreCase = true) == true)
            }
            .sortedByDescending { it.occurredAt }

        val categoryOptions = buildList {
            categories.sortedBy { it.name.lowercase() }
                .forEach { add(CategoryFilterOption(it.id, it.name, it.color, it.icon)) }
            if (txs.any { categoryOf(it) == null }) {
                add(CategoryFilterOption(UncategorizedCategoryId, "Uncategorized", "gray", null))
            }
        }

        return Derived(
            rows = buildRows(listTxs, brandsById, categoriesById),
            summaryIncome = income,
            summaryExpenses = expenses,
            totalCount = txs.size,
            // Cascade: with a category chosen, only its brands are offerable — every other brand
            // would filter to an empty list. Not the reverse (a brand has one category, so
            // scoping categories to it would collapse that list to a single row).
            brandOptions = brands
                .filter { brandMatchesCategory(it, list.categoryFilter) }
                .sortedBy { it.name.lowercase() }
                .map { brand ->
                    val category = brand.categoryId?.let(categoriesById::get)
                    BrandFilterOption(brand.id, brand.name, category?.color, category?.icon)
                },
            categoryOptions = categoryOptions,
        )
    }

    private fun brandMatchesCategory(brand: Brand, categoryFilter: CategoryId?): Boolean =
        when (categoryFilter) {
            null -> true
            UncategorizedCategoryId -> brand.categoryId == null
            else -> brand.categoryId == categoryFilter
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
