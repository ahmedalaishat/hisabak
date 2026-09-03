package com.hisabak.feature.brand.domain.usecase

import com.hisabak.core.common.DomainError
import com.hisabak.feature.brand.domain.usecase.ResolveBrandUseCase
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.BrandId
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
    private val useCase = FindOrCreateBrandUseCase(repo, ResolveBrandUseCase(repo))

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

    /**
     * The regression that motivated the alias table: a learned template captures the merchant as
     * the bank writes it, and "GOOGLEYOUTUBE,US" shares no containment with "Youtube video" — the
     * extra word breaks it both ways — so every later message minted another brand.
     */
    @Test
    fun `resolves a learned alias that no name rule would match`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Youtube video", categoryId = CategoryId("c1"))))
        repo.linkAlias("GOOGLEYOUTUBE,US", BrandId("b1"))

        val result = useCase("GOOGLEYOUTUBE,US")

        assertEquals("b1", (result as DomainResult.Success).value.id.value)
        assertEquals(1, repo.current.size)
    }

    @Test
    fun `matches a merchant string that contains an existing brand`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Youtube", categoryId = CategoryId("c1"))))

        val result = useCase("GOOGLEYOUTUBE,US")

        assertEquals("b1", (result as DomainResult.Success).value.id.value)
    }

    /**
     * Containment admits several candidates, so the winner has to be the most-used rather than
     * whatever row the database returned first. The fake's namesByUsage preserves order, which is
     * what the DAO's ORDER BY COUNT(t.id) DESC produces.
     */
    @Test
    fun `prefers the most-used brand when several contain the string`() = runTest {
        repo.emit(
            listOf(
                brand(id = "b1", name = "Youtube", categoryId = CategoryId("c1")),
                brand(id = "b2", name = "Google", categoryId = CategoryId("c1")),
            ),
        )

        val result = useCase("GOOGLEYOUTUBE,US")

        assertEquals("b1", (result as DomainResult.Success).value.id.value)
    }

    /** An exact name must not be swallowed by a longer brand that merely contains it. */
    @Test
    fun `an exact name wins over a brand that contains it`() = runTest {
        repo.emit(
            listOf(
                brand(id = "b1", name = "Youtube video", categoryId = CategoryId("c1")),
                brand(id = "b2", name = "Youtube", categoryId = CategoryId("c1")),
            ),
        )

        val result = useCase("Youtube")

        assertEquals("b2", (result as DomainResult.Success).value.id.value)
        assertEquals(2, repo.current.size)
    }

    @Test
    fun `blank name is rejected`() = runTest {
        val result = useCase("   ")

        assertTrue((result as DomainResult.Failure).error is DomainError.ValidationFailed)
    }
}
