package com.hisabak.feature.insights.presentation

import com.hisabak.core.common.Currency
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.core.common.AppConfig
import com.hisabak.feature.insights.domain.InsightType
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.ai.AiInsights
import com.hisabak.feature.insights.domain.ai.GenerateNarrativeUseCase
import com.hisabak.feature.insights.domain.ai.RawNarrativeInsight
import com.hisabak.testutil.FakeNarrativeCache
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class InsightsViewModelTest : MainDispatcherTest() {

    private val june = Instant.parse("2026-06-10T10:00:00Z")
    private val analytics = FakeAnalytics()
    private class ScriptedAiInsights(var reply: List<RawNarrativeInsight>?) : AiInsights {
        var calls = 0
        override fun isAvailable() = true
        override suspend fun narrate(summary: InsightsSummary, language: String): List<RawNarrativeInsight>? {
            calls++
            return reply
        }
    }

    private val cache = FakeNarrativeCache()
    private val ai = ScriptedAiInsights(reply = listOf(RawNarrativeInsight("dining", "Dining leads", "Most of it.", null)))

    private fun config(service: Boolean) = AppConfig(
        seedData = false,
        smsAutoCapture = false,
        isDebug = true,
        versionCode = 1,
        flavor = "prod",
        parseServiceUrl = if (service) "https://parse.example" else "",
        parseServiceToken = if (service) "t" else "",
    )

    private fun viewModel(
        transactions: List<com.hisabak.feature.transaction.domain.Transaction>,
        service: Boolean = false,
    ) = InsightsViewModel(
        getMetrics = metrics(transactions),
        generateNarrative = GenerateNarrativeUseCase(ai, cache, TestClock(), analytics),
        appConfig = config(service),
        analytics = analytics,
        period = SummaryPeriod.CURRENT_MONTH,
        language = "en",
    )

    private val ledger get() = listOf(
        transaction(id = "s", amountMinor = 1_000_00, brandId = "salary", occurredAt = june),
        transaction(id = "d", amountMinor = 300_00, brandId = "cafe", occurredAt = june),
    )

    private fun metrics(transactions: List<com.hisabak.feature.transaction.domain.Transaction>) =
        GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(FakeTransactionRepository(transactions)),
            observeCategories = ObserveCategoriesUseCase(
                FakeCategoryRepository(
                    listOf(
                        category(id = "inc", type = CategoryType.INCOME),
                        category(id = "dining", type = CategoryType.EXPENSES),
                    ),
                ),
            ),
            observeBrands = ObserveBrandsUseCase(
                FakeBrandRepository(
                    listOf(
                        brand(id = "salary", categoryId = CategoryId("inc")),
                        brand(id = "cafe", categoryId = CategoryId("dining")),
                    ),
                ),
            ),
            observeCategoryLimits = ObserveCategoryLimitsUseCase(FakeCategoryLimitRepository()),
            currency = Currency.AED,
            clock = TestClock(),
        )

    @Test
    fun `derives the review for the period it was opened with`() = runTest {
        val vm = viewModel(ledger)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SummaryPeriod.CURRENT_MONTH, state.period)
        assertFalse(state.isLoading)
        assertTrue(state.insights.any { it.type == InsightType.LargestCategory })
        assertTrue(state.insights.any { it.type == InsightType.SavingsRate })
    }

    @Test
    fun `an empty period loads to an empty review rather than a loading spinner`() = runTest {
        val vm = viewModel(emptyList())
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.insights.isEmpty())
    }

    @Test
    fun `logs one opened event with the count and a tap with its type`() = runTest {
        val vm = viewModel(listOf(transaction(id = "d", amountMinor = 300_00, brandId = "cafe", occurredAt = june)))
        advanceUntilIdle()

        val opened = analytics.logged.filterIsInstance<AnalyticsEvent.InsightsOpened>()
        assertEquals(1, opened.size)
        assertEquals(vm.state.value.insights.size, opened.single().params["count"])

        // With no May spend the top finding is NewSpend (Notice outranks Info); assert against
        // whatever leads rather than pinning the rule order into an analytics test.
        val first = vm.state.value.insights.first()
        vm.onIntent(InsightsIntent.Tapped(first))
        val tapped = analytics.logged.filterIsInstance<AnalyticsEvent.InsightTapped>().single()
        assertEquals(first.type.name.lowercase(), tapped.params["type"])
    }

    @Test
    fun `the period chips re-scope the review without leaving the screen`() = runTest {
        val vm = viewModel(ledger, service = true)
        advanceUntilIdle()
        vm.onIntent(InsightsIntent.RequestNarrative)
        advanceUntilIdle()
        assertIs<NarrativeUi.Ready>(vm.state.value.narrative)

        vm.onIntent(InsightsIntent.PeriodChanged(SummaryPeriod.LAST_MONTH))
        advanceUntilIdle()

        assertEquals(SummaryPeriod.LAST_MONTH, vm.state.value.period)
        assertTrue(vm.state.value.insights.isEmpty())           // the June ledger has nothing in May
        assertEquals(NarrativeUi.Ask, vm.state.value.narrative) // other figures: ask again, no send
        assertEquals(1, ai.calls)
    }

    // ── Layer 2: the narrative ────────────────────────────────────────────────

    @Test
    fun `without a service the screen shows no AI at all`() = runTest {
        val vm = viewModel(ledger, service = false)
        advanceUntilIdle()

        assertEquals(NarrativeUi.Hidden, vm.state.value.narrative)
        assertEquals(0, ai.calls)
    }

    @Test
    fun `with a service the screen asks and sends nothing on its own`() = runTest {
        val vm = viewModel(ledger, service = true)
        advanceUntilIdle()

        assertEquals(NarrativeUi.Ask, vm.state.value.narrative)
        assertEquals(0, ai.calls)
    }

    @Test
    fun `the tap is the send`() = runTest {
        val vm = viewModel(ledger, service = true)
        advanceUntilIdle()

        vm.onIntent(InsightsIntent.RequestNarrative)
        advanceUntilIdle()

        val ready = assertIs<NarrativeUi.Ready>(vm.state.value.narrative)
        assertEquals("Dining leads", ready.items.single().headline)
        assertEquals(1, ai.calls)
        assertTrue(analytics.logged.any { it is AnalyticsEvent.InsightsNarrativeRequested })
    }

    @Test
    fun `every visit starts at the ask - a tap for unchanged figures is answered without a send`() = runTest {
        val first = viewModel(ledger, service = true)
        advanceUntilIdle()
        first.onIntent(InsightsIntent.RequestNarrative)
        advanceUntilIdle()

        val second = viewModel(ledger, service = true)
        advanceUntilIdle()
        assertEquals(NarrativeUi.Ask, second.state.value.narrative)

        second.onIntent(InsightsIntent.RequestNarrative)
        advanceUntilIdle()
        assertIs<NarrativeUi.Ready>(second.state.value.narrative)
        assertEquals(1, ai.calls)
    }

    @Test
    fun `an outage leaves the deterministic review intact`() = runTest {
        ai.reply = null
        val vm = viewModel(ledger, service = true)
        advanceUntilIdle()
        vm.onIntent(InsightsIntent.RequestNarrative)
        advanceUntilIdle()

        assertIs<NarrativeUi.Unavailable>(vm.state.value.narrative)
        assertTrue(vm.state.value.insights.isNotEmpty())
    }

    @Test
    fun `see what's shared exposes the exact summary`() = runTest {
        val vm = viewModel(ledger, service = true)
        advanceUntilIdle()

        vm.onIntent(InsightsIntent.ShowShared)

        assertTrue(vm.state.value.showShared)
        assertEquals(1_000_00, vm.state.value.summary!!.incomeMinor)
        vm.onIntent(InsightsIntent.HideShared)
        assertFalse(vm.state.value.showShared)
    }
}
