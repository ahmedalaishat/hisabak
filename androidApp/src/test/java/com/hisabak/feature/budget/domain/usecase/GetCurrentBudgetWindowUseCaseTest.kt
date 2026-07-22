package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.aed
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GetCurrentBudgetWindowUseCaseTest {

    private val useCase = GetCurrentBudgetWindowUseCase(clock = com.hisabak.testutil.TestClock())

    private fun budget(
        start: LocalDate,
        end: LocalDate? = null,
        reoccurrence: Reoccurrence,
        period: Int = 1,
    ) = Budget(
        id = BudgetId("bg1"),
        name = "Budget",
        amount = aed(100_00),
        startAt = start,
        endAt = end,
        reoccurrence = reoccurrence,
        period = period,
        categoryIds = setOf(CategoryId("c1")),
    )

    @Test
    fun `custom budget uses its explicit start and end`() {
        val window = useCase(
            budget(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), Reoccurrence.CUSTOM),
            today = LocalDate.of(2026, 6, 17),
        )
        assertEquals(LocalDate.of(2026, 6, 1), window.start)
        assertEquals(LocalDate.of(2026, 6, 30), window.end)
        assertEquals(30L, window.totalDays)
    }

    @Test
    fun `monthly budget returns the window containing today`() {
        val window = useCase(
            budget(LocalDate.of(2026, 1, 1), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate.of(2026, 6, 17),
        )
        assertEquals(LocalDate.of(2026, 6, 1), window.start)
        assertEquals(LocalDate.of(2026, 6, 30), window.end)
    }

    @Test
    fun `daily budget window is the single day`() {
        val window = useCase(
            budget(LocalDate.of(2026, 6, 1), reoccurrence = Reoccurrence.DAILY),
            today = LocalDate.of(2026, 6, 17),
        )
        assertEquals(LocalDate.of(2026, 6, 17), window.start)
        assertEquals(LocalDate.of(2026, 6, 17), window.end)
    }

    @Test
    fun `today on a window boundary stays in the earlier window`() {
        val window = useCase(
            budget(LocalDate.of(2026, 1, 1), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate.of(2026, 1, 1),
        )
        assertEquals(LocalDate.of(2026, 1, 1), window.start)
        assertEquals(LocalDate.of(2026, 1, 31), window.end)
    }
}
