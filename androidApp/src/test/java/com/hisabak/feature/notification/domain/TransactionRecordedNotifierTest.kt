package com.hisabak.feature.notification.domain

import com.hisabak.core.common.Currency
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.transaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRecordedNotifierTest {

    private val notifier = RecordingNotifier()

    private fun sut(
        brands: FakeBrandRepository,
        categories: FakeCategoryRepository = FakeCategoryRepository(),
    ) = TransactionRecordedNotifier(
        brands = brands,
        categories = categories,
        notifier = notifier,
        currency = Currency.AED,
        strings = com.hisabak.testutil.FakeNotificationStrings(),
    )

    @Test
    fun `categorized brand posts amount, brand, and category with glyph and category deep-link`() = runTest {
        val brands = FakeBrandRepository(listOf(brand(id = "b1", name = "Carrefour", categoryId = CategoryId("c1"))))
        val categories = FakeCategoryRepository(
            listOf(category(id = "c1", name = "Groceries", color = "teal", icon = "cart")),
        )

        sut(brands, categories).notify(transaction(amountMinor = 15_000, brandId = "b1"))

        val alert = notifier.recorded.single()
        assertEquals("Transaction recorded", alert.title)
        assertEquals("AED 150.00 at Carrefour · Groceries", alert.message)
        assertEquals("c1", alert.categoryId)
        assertEquals("b1", alert.brandId)
        assertEquals("cart", alert.iconKey)
        assertEquals("teal", alert.colorKey)
    }

    @Test
    fun `uncategorized brand mentions it, carries no glyph, and deep-links to the brand`() = runTest {
        val brands = FakeBrandRepository(listOf(brand(id = "b9", name = "Unknown Shop", categoryId = null)))

        sut(brands).notify(transaction(amountMinor = 4_250, brandId = "b9"))

        val alert = notifier.recorded.single()
        assertTrue(alert.message.contains("AED 42.50 at Unknown Shop"))
        assertTrue(alert.message.contains("Uncategorized"))
        assertNull(alert.categoryId)
        assertEquals("b9", alert.brandId)
        assertNull(alert.iconKey)
        assertNull(alert.colorKey)
    }

    @Test
    fun `missing brand posts nothing`() = runTest {
        sut(FakeBrandRepository()).notify(transaction(brandId = "ghost"))

        assertTrue(notifier.recorded.isEmpty())
    }
}
