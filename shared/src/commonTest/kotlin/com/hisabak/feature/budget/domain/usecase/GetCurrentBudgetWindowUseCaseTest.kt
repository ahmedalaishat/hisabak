package com.hisabak.feature.budget.domain.usecase

import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.Reoccurrence
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.aed
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

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
            budget(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30), Reoccurrence.CUSTOM),
            today = LocalDate(2026, 6, 17),
        )
        assertEquals(LocalDate(2026, 6, 1), window.start)
        assertEquals(LocalDate(2026, 6, 30), window.end)
        assertEquals(30L, window.totalDays)
    }

    @Test
    fun `monthly budget returns the window containing today`() {
        val window = useCase(
            budget(LocalDate(2026, 1, 1), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate(2026, 6, 17),
        )
        assertEquals(LocalDate(2026, 6, 1), window.start)
        assertEquals(LocalDate(2026, 6, 30), window.end)
    }

    @Test
    fun `daily budget window is the single day`() {
        val window = useCase(
            budget(LocalDate(2026, 6, 1), reoccurrence = Reoccurrence.DAILY),
            today = LocalDate(2026, 6, 17),
        )
        assertEquals(LocalDate(2026, 6, 17), window.start)
        assertEquals(LocalDate(2026, 6, 17), window.end)
    }

    @Test
    fun `monthly budget anchored at month-end clamps to shorter months`() {
        val window = useCase(
            budget(LocalDate(2026, 1, 31), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate(2026, 3, 5),
        )
        assertEquals(LocalDate(2026, 2, 28), window.start)
        assertEquals(LocalDate(2026, 3, 27), window.end)
    }

    @Test
    fun `monthly budget anchored at month-end includes the leap day`() {
        val window = useCase(
            budget(LocalDate(2024, 1, 31), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate(2024, 2, 15),
        )
        assertEquals(LocalDate(2024, 1, 31), window.start)
        assertEquals(LocalDate(2024, 2, 28), window.end)
        assertEquals(29L, window.totalDays)
    }

    @Test
    fun `today on a window boundary stays in the earlier window`() {
        val window = useCase(
            budget(LocalDate(2026, 1, 1), reoccurrence = Reoccurrence.MONTHLY),
            today = LocalDate(2026, 1, 1),
        )
        assertEquals(LocalDate(2026, 1, 1), window.start)
        assertEquals(LocalDate(2026, 1, 31), window.end)
    }
}
