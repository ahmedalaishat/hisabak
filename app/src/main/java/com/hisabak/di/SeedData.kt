package com.hisabak.di

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionId
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random

/**
 * Demo seed data for a fresh install. Models an affluent professional (high income, healthy
 * saving/investing, comfortable discretionary spend) so the dashboard, charts, breakdowns, and
 * budget limits all show rich, realistic data out of the box. History runs from January 2024
 * through today so every dashboard period (this/last month, this/last year, all time) has data.
 *
 * Amounts are in minor units (fils) — divide by 100 for the major AED value.
 */
class SeedData(clock: Clock, private val currency: Currency) {
    private val now = clock.now()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.ofInstant(now, zone)

    private val groceries = Category(CategoryId.new(), "Groceries", CategoryType.EXPENSES, color = "green", icon = "cart")
    private val salary = Category(CategoryId.new(), "Salary", CategoryType.INCOME, color = "blue", icon = "briefcase")
    private val transport = Category(CategoryId.new(), "Transport", CategoryType.EXPENSES, color = "orange", icon = "car")
    private val dining = Category(CategoryId.new(), "Dining", CategoryType.EXPENSES, color = "red", icon = "utensils")
    private val shopping = Category(CategoryId.new(), "Shopping", CategoryType.EXPENSES, color = "pink", icon = "gift")
    private val savingsCat = Category(CategoryId.new(), "Savings", CategoryType.SAVINGS, color = "teal", icon = "piggy-bank")
    private val investments = Category(CategoryId.new(), "Investments", CategoryType.INVESTMENT, color = "purple", icon = "wallet")

    val categories: List<Category> = listOf(
        groceries, salary, transport, dining, shopping, savingsCat, investments,
    )

    private val wholeFoods = Brand(BrandId.new(), "Whole Foods", groceries.id)
    private val traderJoes = Brand(BrandId.new(), "Trader Joe's", groceries.id)
    private val acme = Brand(BrandId.new(), "Acme Corp", salary.id)
    private val uber = Brand(BrandId.new(), "Uber", transport.id)
    private val starbucks = Brand(BrandId.new(), "Starbucks", dining.id)
    private val nobu = Brand(BrandId.new(), "Nobu", dining.id)
    private val appleStore = Brand(BrandId.new(), "Apple Store", shopping.id)
    private val vault = Brand(BrandId.new(), "Vault", savingsCat.id)
    private val sarwa = Brand(BrandId.new(), "Sarwa", investments.id)

    val brands: List<Brand> = listOf(
        wholeFoods, traderJoes, acme, uber, starbucks, nobu, appleStore, vault, sarwa,
    )

    val transactions: List<Transaction> = generate()

    val categoryLimits: List<CategoryLimit> = listOf(
        // Groceries: AED 3,000/mo, bumped to 3,500 in Sep 2025 (a stepped limit line).
        CategoryLimit(groceries.id, Money(300_000, currency), YearMonth.of(2024, 1)),
        CategoryLimit(groceries.id, Money(350_000, currency), YearMonth.of(2025, 9)),
        // Dining: AED 2,500/mo, introduced in 2025 (earlier months show a gap).
        CategoryLimit(dining.id, Money(250_000, currency), YearMonth.of(2025, 1)),
        // Shopping: AED 3,000/mo from the start (occasionally exceeded — exercises the alerts).
        CategoryLimit(shopping.id, Money(300_000, currency), YearMonth.of(2024, 1)),
    )

    private fun generate(): List<Transaction> {
        val rnd = Random(seed = 42)
        val out = mutableListOf<Transaction>()
        var month = YearMonth.of(2024, 1)
        val endMonth = YearMonth.from(today)

        fun add(brandId: BrandId, amountMinor: Long, date: LocalDate, note: String?) {
            if (date.isAfter(today)) return
            out += tx(brandId, amountMinor, date, note)
        }

        while (!month.isAfter(endMonth)) {
            val len = month.lengthOfMonth()

            // Salary on the 25th (~AED 55k), plus a quarterly bonus.
            add(acme.id, 5_500_000L + rnd.nextInt(0, 700_000), month.atDay(minOf(25, len)), "Monthly salary")
            if (month.monthValue % 3 == 0) {
                add(acme.id, 2_000_000L + rnd.nextInt(0, 800_000), month.atDay(minOf(25, len)), "Quarterly bonus")
            }

            // Monthly transfer to savings (~AED 12k) and investment contribution (~AED 8k).
            add(vault.id, 1_200_000L + rnd.nextInt(0, 400_000), month.atDay(minOf(26, len)), "Monthly savings")
            add(sarwa.id, 800_000L + rnd.nextInt(0, 500_000), month.atDay(minOf(27, len)), "Investment contribution")

            // Weekly groceries (~AED 300–800).
            for (week in 0..3) {
                val store = if (rnd.nextBoolean()) wholeFoods else traderJoes
                add(store.id, (30_000 + rnd.nextInt(0, 50_000)).toLong(), month.atDay(minOf(3 + week * 7, len)), "Weekly groceries")
            }

            // Coffee a few mornings a week (~AED 25–55).
            for (day in listOf(2, 9, 16, 23)) {
                add(starbucks.id, (2_500 + rnd.nextInt(0, 3_000)).toLong(), month.atDay(minOf(day, len)), "Morning coffee")
            }

            // Dinners out at nicer spots (~AED 350–950).
            for (day in listOf(8, 22)) {
                add(nobu.id, (35_000 + rnd.nextInt(0, 60_000)).toLong(), month.atDay(minOf(day, len)), "Dinner")
            }

            // Rides through the month (~AED 60–300).
            for (day in listOf(5, 12, 19, 27)) {
                add(uber.id, (6_000 + rnd.nextInt(0, 24_000)).toLong(), month.atDay(minOf(day, len)), null)
            }

            // Shopping once or twice a month (~AED 500–3,000).
            add(appleStore.id, (50_000 + rnd.nextInt(0, 250_000)).toLong(), month.atDay(minOf(10, len)), "Shopping")
            if (rnd.nextBoolean()) {
                add(appleStore.id, (40_000 + rnd.nextInt(0, 160_000)).toLong(), month.atDay(minOf(20, len)), "Shopping")
            }

            month = month.plusMonths(1)
        }
        return out
    }

    private fun tx(brandId: BrandId, amountMinor: Long, date: LocalDate, note: String?): Transaction =
        Transaction(
            id = TransactionId.new(),
            amount = Money(amountMinor, currency),
            brandId = brandId,
            note = note,
            occurredAt = date.atTime(12, 0).atZone(zone).toInstant(),
        )
}
