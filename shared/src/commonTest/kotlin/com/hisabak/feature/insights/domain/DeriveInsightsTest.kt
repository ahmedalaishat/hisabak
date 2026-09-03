package com.hisabak.feature.insights.domain

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeriveInsightsTest {

    private fun spend(
        id: String,
        spent: Long,
        prior: Long? = null,
        limit: Long? = null,
        expenseTotal: Long,
    ) = CategorySpend(
        id = CategoryId(id),
        name = "Category $id",
        color = "blue",
        icon = "wallet",
        spentMinor = spent,
        priorMinor = prior,
        limitMinor = limit,
        shareOfExpense = if (expenseTotal > 0) spent.toDouble() / expenseTotal else 0.0,
    )

    private fun summary(
        categories: List<CategorySpend>,
        income: Long = 0,
        priorIncome: Long? = null,
        priorExpense: Long? = null,
        uncategorized: Long = 0,
        uncategorizedCount: Int = 0,
        period: SummaryPeriod = SummaryPeriod.CURRENT_MONTH,
    ) = InsightsSummary(
        period = period,
        incomeMinor = income,
        expenseMinor = categories.sumOf { it.spentMinor },
        priorIncomeMinor = priorIncome,
        priorExpenseMinor = priorExpense,
        categories = categories,
        uncategorizedMinor = uncategorized,
        uncategorizedCount = uncategorizedCount,
    )

    private fun List<Insight>.of(type: InsightType) = filter { it.type == type }
    private fun List<Insight>.single(type: InsightType) = of(type).single()

    // ── AC 1: ordering ──────────────────────────────────────────────────────

    @Test
    fun `over limit leads, then by severity and magnitude`() {
        val total = 1_000_00L
        val insights = deriveInsights(
            summary(
                listOf(
                    spend("dining", spent = 600_00, prior = 400_00, limit = 500_00, expenseTotal = total),
                    spend("fuel", spent = 400_00, prior = 400_00, expenseTotal = total),
                ),
            ),
        )
        assertEquals(InsightType.OverLimit, insights.first().type)
        val severities = insights.map { it.severity.ordinal }
        assertEquals(severities.sortedDescending(), severities)
    }

    // ── AC 2: limit thresholds ──────────────────────────────────────────────

    @Test
    fun `near limit from 80 percent, over limit past 100, never both`() {
        val total = 1_000_00L
        fun at(spent: Long) = deriveInsights(summary(listOf(spend("c", spent = spent, limit = 1_000_00, expenseTotal = total))))

        assertTrue(at(790_00).of(InsightType.NearLimit).isEmpty())
        assertTrue(at(790_00).of(InsightType.OverLimit).isEmpty())

        val near = at(800_00).single(InsightType.NearLimit)
        assertEquals(200_00, near.amountMinor) // room left
        assertTrue(at(800_00).of(InsightType.OverLimit).isEmpty())

        val exact = at(1_000_00)
        assertEquals(1, exact.of(InsightType.NearLimit).size)
        assertTrue(exact.of(InsightType.OverLimit).isEmpty())

        val over = at(1_250_00)
        assertEquals(250_00, over.single(InsightType.OverLimit).amountMinor) // overage
        assertEquals(Severity.Warning, over.single(InsightType.OverLimit).severity)
        assertTrue(over.of(InsightType.NearLimit).isEmpty())
    }

    @Test
    fun `a missing or zero limit yields no limit insight`() {
        val total = 500_00L
        val none = deriveInsights(summary(listOf(spend("c", spent = 500_00, limit = null, expenseTotal = total))))
        val zero = deriveInsights(summary(listOf(spend("c", spent = 500_00, limit = 0, expenseTotal = total))))
        assertTrue(none.none { it.type == InsightType.OverLimit || it.type == InsightType.NearLimit })
        assertTrue(zero.none { it.type == InsightType.OverLimit || it.type == InsightType.NearLimit })
    }

    // ── AC 3: changes ───────────────────────────────────────────────────────

    @Test
    fun `a 25 percent rise is reported with its signed percentage`() {
        val insights = deriveInsights(summary(listOf(spend("c", spent = 500_00, prior = 400_00, expenseTotal = 500_00))))
        val up = insights.single(InsightType.SpendUp)
        assertEquals(25.0, up.deltaPct!!, 0.001)
        assertEquals(100_00, up.amountMinor)
        assertEquals(Severity.Notice, up.severity)
    }

    @Test
    fun `a 25 percent fall is informational`() {
        val insights = deriveInsights(summary(listOf(spend("c", spent = 300_00, prior = 400_00, expenseTotal = 300_00))))
        val down = insights.single(InsightType.SpendDown)
        assertEquals(-25.0, down.deltaPct!!, 0.001)
        assertEquals(Severity.Info, down.severity)
    }

    @Test
    fun `a move under the threshold is not reported`() {
        val insights = deriveInsights(summary(listOf(spend("c", spent = 480_00, prior = 400_00, expenseTotal = 480_00))))
        assertTrue(insights.none { it.type == InsightType.SpendUp || it.type == InsightType.SpendDown })
    }

    /** A 300% rise on a two-dirham base is noise; the floor is 5% of total expense in both periods. */
    @Test
    fun `an immaterial base is ignored even on a huge percentage`() {
        val total = 10_000_00L
        val insights = deriveInsights(
            summary(
                listOf(
                    spend("big", spent = 9_992_00, prior = 9_992_00, expenseTotal = total),
                    spend("tiny", spent = 8_00, prior = 2_00, expenseTotal = total),
                ),
            ),
        )
        assertTrue(insights.none { it.category?.id == CategoryId("tiny") && it.type == InsightType.SpendUp })
    }

    @Test
    fun `spend on a category with no prior spend is new spend, not an infinite percentage`() {
        val insights = deriveInsights(summary(listOf(spend("c", spent = 300_00, prior = 0, expenseTotal = 300_00))))
        val fresh = insights.single(InsightType.NewSpend)
        assertNull(fresh.deltaPct)
        assertEquals(300_00, fresh.amountMinor)
        assertTrue(insights.of(InsightType.SpendUp).isEmpty())
    }

    /** No prior period at all (e.g. ALL): the change rules degrade to absent, the rest still fire. */
    @Test
    fun `without a prior period there are no change insights but the review is not empty`() {
        val insights = deriveInsights(
            summary(listOf(spend("c", spent = 300_00, prior = null, expenseTotal = 300_00)), period = SummaryPeriod.ALL),
        )
        assertTrue(insights.none { it.type in setOf(InsightType.SpendUp, InsightType.SpendDown, InsightType.NewSpend) })
        assertEquals(1, insights.of(InsightType.LargestCategory).size)
    }

    // ── AC 4: largest category ──────────────────────────────────────────────

    @Test
    fun `largest expense category carries its share`() {
        val total = 1_000_00L
        val insights = deriveInsights(
            summary(
                listOf(
                    spend("a", spent = 700_00, expenseTotal = total),
                    spend("b", spent = 300_00, expenseTotal = total),
                ),
            ),
        )
        val largest = insights.single(InsightType.LargestCategory)
        assertEquals(CategoryId("a"), largest.category?.id)
        assertEquals(0.7, largest.share!!, 0.001)
    }

    @Test
    fun `no expense means no largest category`() {
        val insights = deriveInsights(summary(listOf(spend("a", spent = 0, expenseTotal = 0)), income = 1_000_00))
        assertTrue(insights.of(InsightType.LargestCategory).isEmpty())
    }

    // ── AC 5: savings rate ──────────────────────────────────────────────────

    @Test
    fun `savings rate is income minus expense over income, with the change in points`() {
        val insights = deriveInsights(
            summary(
                listOf(spend("c", spent = 600_00, expenseTotal = 600_00)),
                income = 1_000_00,
                priorIncome = 1_000_00,
                priorExpense = 700_00,
            ),
        )
        val savings = insights.single(InsightType.SavingsRate)
        assertEquals(0.4, savings.share!!, 0.001)
        assertEquals(10.0, savings.deltaPct!!, 0.001) // 40% now vs 30% before
        assertEquals(400_00, savings.amountMinor)
        assertEquals(Severity.Info, savings.severity)
    }

    @Test
    fun `savings rate is absent with no income and has no delta without a derivable prior`() {
        val noIncome = deriveInsights(summary(listOf(spend("c", spent = 100_00, expenseTotal = 100_00)), income = 0))
        assertTrue(noIncome.of(InsightType.SavingsRate).isEmpty())

        val noPrior = deriveInsights(summary(listOf(spend("c", spent = 100_00, expenseTotal = 100_00)), income = 500_00))
        assertNull(noPrior.single(InsightType.SavingsRate).deltaPct)
    }

    @Test
    fun `spending more than earned is a warning`() {
        val insights = deriveInsights(summary(listOf(spend("c", spent = 1_200_00, expenseTotal = 1_200_00)), income = 1_000_00))
        val savings = insights.single(InsightType.SavingsRate)
        assertEquals(Severity.Warning, savings.severity)
        assertTrue(savings.share!! < 0)
    }

    // ── AC 6: uncategorized ─────────────────────────────────────────────────

    @Test
    fun `uncategorized fires only when there is something uncategorized`() {
        val none = deriveInsights(summary(emptyList(), uncategorized = 0, uncategorizedCount = 0))
        assertTrue(none.of(InsightType.Uncategorized).isEmpty())

        val some = deriveInsights(summary(emptyList(), uncategorized = 250_00, uncategorizedCount = 3))
        val insight = some.single(InsightType.Uncategorized)
        assertEquals(3, insight.count)
        assertEquals(250_00, insight.amountMinor)
        assertEquals(Severity.Notice, insight.severity)
    }

    // ── AC 7: empty ─────────────────────────────────────────────────────────

    @Test
    fun `an empty period yields an empty review`() {
        assertTrue(deriveInsights(summary(emptyList())).isEmpty())
    }

    @Test
    fun `ids are stable and unique across a review`() {
        val insights = deriveInsights(
            summary(
                listOf(spend("c", spent = 600_00, prior = 400_00, limit = 500_00, expenseTotal = 600_00)),
                income = 1_000_00,
                uncategorized = 1_00,
                uncategorizedCount = 1,
            ),
        )
        assertEquals(insights.size, insights.map { it.id }.toSet().size)
        assertEquals("OverLimit:c", insights.single(InsightType.OverLimit).id)
    }
}
