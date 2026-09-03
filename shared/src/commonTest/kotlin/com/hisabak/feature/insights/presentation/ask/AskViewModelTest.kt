package com.hisabak.feature.insights.presentation.ask

import com.hisabak.core.common.Currency
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoryLimitsUseCase
import com.hisabak.feature.dashboard.domain.usecase.GetDashboardMetricsUseCase
import com.hisabak.feature.insights.domain.ai.ASK_DAILY_ALLOWANCE
import com.hisabak.feature.insights.domain.ai.AskInsightUseCase
import com.hisabak.feature.insights.domain.ai.AskResponseDto
import com.hisabak.feature.insights.domain.ai.AskRole
import com.hisabak.feature.insights.domain.ai.MAX_QUESTION_LENGTH
import com.hisabak.feature.transaction.domain.usecase.ObserveTransactionsUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeRemoteInsightsClient
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AskViewModelTest : MainDispatcherTest() {

    private val june = Instant.parse("2026-06-10T10:00:00Z")
    private val client = FakeRemoteInsightsClient()
    private val prefs = FakeAppPreferences()
    private val analytics = FakeAnalytics()

    private val ledger = listOf(
        transaction(id = "s", amountMinor = 1_000_00, brandId = "salary", occurredAt = june),
        transaction(id = "d", amountMinor = 300_00, brandId = "cafe", occurredAt = june),
    )

    private fun viewModel(initialQuestion: String? = null) = AskViewModel(
        getMetrics = GetDashboardMetricsUseCase(
            observeTransactions = ObserveTransactionsUseCase(FakeTransactionRepository(ledger)),
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
        ),
        askInsight = AskInsightUseCase(client, prefs, Currency.AED, TestClock(), analytics),
        period = SummaryPeriod.CURRENT_MONTH,
        language = "en",
        initialQuestion = initialQuestion,
    )

    @Test
    fun `the question that opened the screen is sent on arrival`() = runTest {
        val vm = viewModel(initialQuestion = "Why is dining up?")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(AskRole.User, AskRole.Assistant), state.turns.map { it.role })
        assertEquals("Dining drove it.", state.turns.last().text)
        assertEquals(ASK_DAILY_ALLOWANCE - 1, state.remaining)
        assertEquals("Why is dining up?", client.asks.single().first.question)
    }

    @Test
    fun `opening with no question sends nothing and offers suggestions`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(client.asks.isEmpty())
        assertTrue(vm.state.value.isEmpty)
        assertTrue(vm.state.value.suggestions.isNotEmpty())
        assertEquals(ASK_DAILY_ALLOWANCE, vm.state.value.remaining)
    }

    @Test
    fun `the draft is capped and sent on submit with the earlier turns as history`() = runTest {
        val vm = viewModel(initialQuestion = "Why is dining up?")
        advanceUntilIdle()

        vm.onIntent(AskIntent.DraftChanged("x".repeat(MAX_QUESTION_LENGTH + 100)))
        assertEquals(MAX_QUESTION_LENGTH, vm.state.value.draft.length)
        vm.onIntent(AskIntent.DraftChanged("And fuel?"))
        vm.onIntent(AskIntent.Submitted)
        advanceUntilIdle()

        assertEquals("", vm.state.value.draft)
        val second = client.asks[1].first
        assertEquals("And fuel?", second.question)
        assertEquals(listOf("Why is dining up?", "Dining drove it."), second.history.map { it.text })
    }

    @Test
    fun `a blank draft is not sent`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(AskIntent.DraftChanged("   "))
        vm.onIntent(AskIntent.Submitted)
        advanceUntilIdle()

        assertTrue(client.asks.isEmpty())
    }

    @Test
    fun `an outage keeps the question on screen and spends no allowance`() = runTest {
        client.answer = null
        val vm = viewModel(initialQuestion = "Why is dining up?")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AskNotice.Unavailable, state.notice)
        assertEquals(listOf(AskRole.User), state.turns.map { it.role })
        assertEquals(ASK_DAILY_ALLOWANCE, state.remaining)
    }

    @Test
    fun `a refusal is shown like any other answer`() = runTest {
        client.answer = AskResponseDto(answer = "I can only discuss this review.", onTopic = false)
        val vm = viewModel(initialQuestion = "write me a poem")
        advanceUntilIdle()

        assertEquals("I can only discuss this review.", vm.state.value.turns.last().text)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `a tapped suggestion is sent verbatim`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(AskIntent.QuestionTapped("What should I focus on?"))
        advanceUntilIdle()

        assertEquals("What should I focus on?", client.asks.single().first.question)
    }
}
