package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.testutil.FakeRemoteInsightsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject

class RemoteAiInsightsTest {

    private val client = FakeRemoteInsightsClient(
        result = InsightsResponseDto(items = listOf(NarrativeItemDto("dining", "Over", "Detail", 1_600_00))),
    )
    private val ai = RemoteAiInsights(client, Currency.AED)

    @Test
    fun `a request reaches the service and returns raw items`() = runTest {
        val items = ai.narrate(summary(), "ar")!!

        assertEquals(listOf(RawNarrativeInsight("dining", "Over", "Detail", 1_600_00)), items)
        val request = client.requests.single()
        assertEquals("ar", request.language)
        assertEquals("AED", request.currency)
        assertEquals("CURRENT_MONTH", request.period)
    }

    @Test
    fun `an unconfigured service is unavailable and never called`() = runTest {
        client.isConfigured = false

        assertFalse(ai.isAvailable())
        assertNull(ai.narrate(summary(), "en"))
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `the payload carries figures only - the privacy boundary is its field list`() {
        val json = Json.encodeToString(InsightsRequestDto.serializer(), summary().toRequestDto("en", Currency.AED))
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals(
            setOf(
                "period", "currency", "language", "income_minor", "expense_minor", "prior_income_minor",
                "prior_expense_minor", "categories", "uncategorized_minor", "uncategorized_count",
            ),
            root.keys,
        )
        val category = (root["categories"] as JsonArray)[0].jsonObject
        assertEquals(setOf("id", "name", "spent_minor", "prior_minor", "limit_minor"), category.keys)
    }

    @Test
    fun `categories are sent largest first and capped`() {
        val many = List(70) { spend("c$it", spent = (70 - it) * 100L) }

        val dto = summary(categories = many, uncategorized = 0, uncategorizedCount = 0).toRequestDto("en", Currency.AED)

        assertEquals(MAX_REQUEST_CATEGORIES, dto.categories.size)
        assertEquals("c0", dto.categories.first().id)
        assertEquals(7_000, dto.categories.first().spentMinor)
    }
}
