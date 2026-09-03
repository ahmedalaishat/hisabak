package com.hisabak.feature.brand.domain.usecase

import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.brand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LearnBrandAliasUseCaseTest {

    private val repo = FakeBrandRepository()
    private val analytics = FakeAnalytics()
    private val useCase = LearnBrandAliasUseCase(repo, ResolveBrandUseCase(repo), analytics)

    @Test
    fun `learns the mapping when the raw text resolves to nothing`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Youtube video", categoryId = CategoryId("c1"))))

        useCase("GOOGLEYOUTUBE,US", BrandId("b1"))

        assertEquals("b1", repo.findByAlias("GOOGLEYOUTUBE,US")?.id?.value)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.BrandAliasLearned), analytics.logged)
    }

    /** Matching is case-insensitive, so the bank shouting the merchant is not a second alias. */
    @Test
    fun `resolves the learned alias regardless of case`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Youtube video", categoryId = CategoryId("c1"))))

        useCase("GOOGLEYOUTUBE,US", BrandId("b1"))

        assertEquals("b1", repo.findByAlias("googleyoutube,us")?.id?.value)
    }

    /** Nothing to learn: the name rules already land on this brand, so an alias would be noise. */
    @Test
    fun `skips a raw text that already resolves to the same brand`() = runTest {
        repo.emit(listOf(brand(id = "b1", name = "Carrefour", categoryId = CategoryId("c1"))))

        useCase("CARREFOUR", BrandId("b1"))

        assertNull(repo.findByAlias("CARREFOUR"))
        assertTrue(analytics.logged.isEmpty())
    }

    /**
     * A merchant string that resolves to the *wrong* brand is exactly the case worth pinning:
     * containment sends "GOOGLEYOUTUBE,US" to "Google", and the user linked YouTube.
     */
    @Test
    fun `overrides a name rule that resolves to a different brand`() = runTest {
        repo.emit(
            listOf(
                brand(id = "b1", name = "Google", categoryId = CategoryId("c1")),
                brand(id = "b2", name = "Youtube video", categoryId = CategoryId("c1")),
            ),
        )

        useCase("GOOGLEYOUTUBE,US", BrandId("b2"))

        assertEquals("b2", repo.findByAlias("GOOGLEYOUTUBE,US")?.id?.value)
    }

    @Test
    fun `ignores a null or blank raw text`() = runTest {
        useCase(null, BrandId("b1"))
        useCase("   ", BrandId("b1"))

        assertTrue(analytics.logged.isEmpty())
    }
}
