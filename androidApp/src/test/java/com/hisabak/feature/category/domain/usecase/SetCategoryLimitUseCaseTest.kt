package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.effectiveFor
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class SetCategoryLimitUseCaseTest {

    private val repo = FakeCategoryLimitRepository()
    private val clock = TestClock() // 2026-06-17 -> YearMonth 2026-06
    private val useCase = SetCategoryLimitUseCase(repo, clock)

    @Test
    fun `sets a limit effective from the current month`() = runTest {
        useCase(CategoryId("c1"), aed(500_00))

        val limit = repo.current.single()
        assertEquals(YearMonth.of(2026, 6), limit.effectiveFrom)
        assertEquals(aed(500_00), limit.amount)
        assertEquals(aed(500_00), repo.current.effectiveFor(CategoryId("c1"), YearMonth.of(2026, 6)))
    }

    @Test
    fun `clearing a limit records a null amount from the current month`() = runTest {
        useCase(CategoryId("c1"), null)

        assertNull(repo.current.single().amount)
        assertNull(repo.current.effectiveFor(CategoryId("c1"), YearMonth.of(2026, 6)))
    }
}
