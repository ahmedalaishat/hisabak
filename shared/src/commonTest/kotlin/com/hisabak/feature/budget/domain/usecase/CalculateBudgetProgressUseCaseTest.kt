package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.FakeBudgetRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class CalculateBudgetProgressUseCaseTest {

    private val clock = TestClock()
    private val window = GetCurrentBudgetWindowUseCase(clock)

    private fun budget() = Budget(
        id = BudgetId("bg1"),
        name = "Groceries",
        amount = aed(1_000_00),
        startAt = LocalDate(2026, 6, 1),
        endAt = LocalDate(2026, 6, 30),
        reoccurrence = Reoccurrence.CUSTOM,
        categoryIds = setOf(CategoryId("c1")),
    )

    @Test
    fun `reports spent and the elapsed-days percentage`() = runTest {
        val repo = FakeBudgetRepository(sumInWindow = aed(400_00))
        val useCase = CalculateBudgetProgressUseCase(repo, window, clock)

        // June window is 30 days; June 16 is the 15th elapsed day -> 50%.
        val progress = useCase(budget(), today = LocalDate(2026, 6, 16))

        assertEquals(aed(400_00), progress.spent)
        assertEquals(aed(1_000_00), progress.limit)
        assertEquals(50.0, progress.elapsedDaysPercentage, 0.001)
    }

    @Test
    fun `elapsed percentage is clamped to 100 after the window ends`() = runTest {
        val repo = FakeBudgetRepository(sumInWindow = aed(0))
        val useCase = CalculateBudgetProgressUseCase(repo, window, clock)

        val progress = useCase(budget(), today = LocalDate(2026, 12, 31))

        assertEquals(100.0, progress.elapsedDaysPercentage, 0.001)
        assertEquals(0L, progress.remainingDays)
    }

    @Test
    fun `elapsed percentage is zero before the window starts`() = runTest {
        val repo = FakeBudgetRepository(sumInWindow = aed(0))
        val useCase = CalculateBudgetProgressUseCase(repo, window, clock)

        val progress = useCase(budget(), today = LocalDate(2026, 1, 1))

        assertEquals(0.0, progress.elapsedDaysPercentage, 0.001)
    }
}
