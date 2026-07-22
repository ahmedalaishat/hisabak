package com.hisabak.feature.brand.domain.usecase

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class FindOrCreateBrandUseCaseTest {

    private val repo = FakeBrandRepository()
    private val useCase = FindOrCreateBrandUseCase(repo)

    @Test
    fun `creates a new brand when none matches`() = runTest {
        val result = useCase("Carrefour")

        assertTrue(result is DomainResult.Success)
        assertEquals("Carrefour", (result as DomainResult.Success).value.name)
        assertEquals(1, repo.current.size)
    }

    @Test
    fun `trims surrounding whitespace`() = runTest {
        val result = useCase("  Lulu  ")

        assertEquals("Lulu", (result as DomainResult.Success).value.name)
    }

    @Test
    fun `reuses an existing brand by case-insensitive name`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Spinneys", categoryId = CategoryId("c1"))))

        val result = useCase("spinneys")

        assertEquals("b1", (result as DomainResult.Success).value.id.value)
        assertEquals(1, repo.current.size)
    }

    @Test
    fun `blank name is rejected`() = runTest {
        val result = useCase("   ")

        assertTrue((result as DomainResult.Failure).error is DomainError.ValidationFailed)
    }
}
