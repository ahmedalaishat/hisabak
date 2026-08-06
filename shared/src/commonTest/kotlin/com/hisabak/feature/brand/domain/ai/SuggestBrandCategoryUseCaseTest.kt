package com.hisabak.feature.brand.domain.ai

import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAiCategorySuggester
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SuggestBrandCategoryUseCaseTest {

    private val suggester = FakeAiCategorySuggester()
    private val catRepo = FakeCategoryRepository(listOf(category(id = "c1", name = "Groceries")))
    private val analytics = FakeAnalytics()
    private val useCase = SuggestBrandCategoryUseCase(suggester, catRepo, analytics)

    @Test
    fun `unavailable model suggests nothing and never runs inference`() = runTest {
        suggester.ready = false

        assertNull(useCase("Carrefour"))
        assertTrue(suggester.suggestedBrands.isEmpty())
        assertEquals("ai_category_failed", analytics.logged.single().name)
    }

    @Test
    fun `an existing-category answer is snapped and reported`() = runTest {
        suggester.result = AiRawCategorySuggestion("groceries", null, null, null, null)

        val result = useCase("  Carrefour ")

        assertEquals("c1", (result as CategorySuggestion.Existing).category.id.value)
        assertEquals(listOf("Carrefour"), suggester.suggestedBrands) // trimmed
        assertEquals(listOf(AiCategoryOption("Groceries", "expenses")), suggester.lastCategories)
        val event = analytics.logged.single() as AnalyticsEvent.AiCategorySuggested
        assertEquals("existing", event.params["kind"])
        // PII guard: neither the brand nor the category name reaches analytics.
        assertTrue(event.params.values.none { it == "Carrefour" || it == "Groceries" })
    }

    @Test
    fun `a new-category answer is sanitized and reported`() = runTest {
        suggester.result = AiRawCategorySuggestion(null, "Pharmacy", "expenses", "teal", "heart")

        val result = useCase("Life Pharmacy")

        assertEquals(
            CategorySuggestion.New("Pharmacy", com.hisabak.feature.category.domain.CategoryType.EXPENSES, "teal", "heart"),
            result,
        )
        assertEquals("new", (analytics.logged.single() as AnalyticsEvent.AiCategorySuggested).params["kind"])
    }

    @Test
    fun `junk model output suggests nothing`() = runTest {
        suggester.result = null

        assertNull(useCase("Carrefour"))
        assertEquals("ai_category_failed", analytics.logged.single().name)
    }
}
