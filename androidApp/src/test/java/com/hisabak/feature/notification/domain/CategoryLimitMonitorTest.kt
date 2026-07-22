package com.hisabak.feature.notification.domain

import com.hisabak.core.common.Currency
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertDao
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import com.hisabak.testutil.category
import com.hisabak.testutil.categoryLimit
import com.hisabak.testutil.transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryLimitMonitorTest {

    private val clock = TestClock() // 2026-06-17
    private val transactions = FakeTransactionRepository()
    private val brands = FakeBrandRepository(listOf(brand(id = "b1", categoryId = CategoryId("c1"))))
    private val categories = FakeCategoryRepository()
    private val limits = FakeCategoryLimitRepository()
    private val notifications = FakeNotificationRepository()
    private val alertDao = FakeCategoryLimitAlertDao()
    private val notifier = RecordingNotifier()

    private fun monitor() = CategoryLimitMonitor(
        transactions = transactions,
        brands = brands,
        categories = categories,
        limits = limits,
        notifications = notifications,
        alertDao = alertDao,
        systemNotifier = notifier,
        currency = Currency.AED,
        clock = clock,
        strings = com.hisabak.testutil.FakeNotificationStrings(),
    )

    /** Runs [block] with the monitor live on an eager dispatcher, then tears the collector down. */
    private fun TestScope.startMonitor(): CoroutineScope =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).also { monitor().start(it) }

    private val expenses = category(id = "c1", name = "Groceries", type = CategoryType.EXPENSES)
    private fun txOf(minor: Long, id: String = "t1") =
        transaction(id = id, amountMinor = minor, brandId = "b1", occurredAt = Instant.parse("2026-06-15T12:00:00Z"))

    @Test
    fun `fires a single alert when spend crosses the fifty percent threshold`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00))) // 100 AED limit
        transactions.emit(listOf(txOf(60_00))) // 60% spent -> level 50

        val scope = startMonitor()

        assertEquals(1, notifier.posted.size)
        assertTrue(notifier.posted.single().title.contains("50%"))
        assertEquals(1, notifications.created.size)
        scope.cancel()
    }

    @Test
    fun `each threshold fires at most once, and dips do not re-alert`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(listOf(txOf(60_00))) // level 50

        val scope = startMonitor()
        assertEquals(1, notifier.posted.size)

        // Stays at level 50 -> no new alert.
        transactions.emit(listOf(txOf(55_00)))
        assertEquals(1, notifier.posted.size)

        // Crosses 80% -> one more alert.
        transactions.emit(listOf(txOf(85_00)))
        assertEquals(2, notifier.posted.size)
        assertTrue(notifier.posted[1].title.contains("80%"))

        // Dips back to level 50 -> still no re-alert.
        transactions.emit(listOf(txOf(60_00)))
        assertEquals(2, notifier.posted.size)
        scope.cancel()
    }

    @Test
    fun `reaching the limit reports a budget-reached alert`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(listOf(txOf(100_00))) // 100% -> level 100

        val scope = startMonitor()

        assertEquals(1, notifier.posted.size)
        assertTrue(notifier.posted.single().title.contains("reached"))
        scope.cancel()
    }

    @Test
    fun `evaluateNow fires an alert without the live collector`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(listOf(txOf(100_00))) // 100% -> level 100

        monitor().evaluateNow()

        assertEquals(1, notifier.posted.size)
        assertTrue(notifier.posted.single().title.contains("reached"))
    }

    @Test
    fun `evaluateNow does not re-alert a threshold already recorded this month`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(listOf(txOf(60_00))) // level 50

        monitor().evaluateNow()
        assertEquals(1, notifier.posted.size)

        // A second capture that stays at level 50 must not produce another alert.
        monitor().evaluateNow()
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `non-expense categories are ignored`() = runTest {
        categories.emit(listOf(category(id = "c1", type = CategoryType.INCOME)))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(listOf(txOf(200_00)))

        val scope = startMonitor()

        assertTrue(notifier.posted.isEmpty())
        scope.cancel()
    }

    @Test
    fun `non-positive limits are ignored`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 0)))
        transactions.emit(listOf(txOf(50_00)))

        val scope = startMonitor()

        assertTrue(notifier.posted.isEmpty())
        scope.cancel()
    }

    @Test
    fun `transactions outside the current month do not count`() = runTest {
        categories.emit(listOf(expenses))
        limits.emit(listOf(categoryLimit("c1", 100_00)))
        transactions.emit(
            listOf(
                transaction(id = "t1", amountMinor = 90_00, brandId = "b1", occurredAt = Instant.parse("2026-05-15T12:00:00Z")),
                transaction(id = "t2", amountMinor = 90_00, brandId = "b1", occurredAt = Instant.parse("2026-07-15T12:00:00Z")),
            ),
        )

        val scope = startMonitor()

        assertTrue(notifier.posted.isEmpty())
        scope.cancel()
    }
}
