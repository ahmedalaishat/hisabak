package com.hisabak.feature.notification.domain

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryLimitRepository
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.domain.effectiveFor
import com.hisabak.feature.notification.data.local.CategoryLimitAlertDao
import com.hisabak.feature.notification.data.local.CategoryLimitAlertEntity
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.time.ZoneId

/**
 * Watches transactions and limits and, when an expense category's current-month spend crosses a
 * new threshold (50/80/100% of its limit), records an in-app notification and posts a system
 * notification. Reacting to the observed data covers manual, edited, and SMS-imported
 * transactions uniformly — no transaction write path needs to call us. Each threshold fires at
 * most once per category per month (tracked in [alertDao]); we never re-alert when spend dips.
 */
class CategoryLimitMonitor(
    private val transactions: TransactionRepository,
    private val brands: BrandRepository,
    private val categories: CategoryRepository,
    private val limits: CategoryLimitRepository,
    private val notifications: NotificationRepository,
    private val alertDao: CategoryLimitAlertDao,
    private val systemNotifier: Notifier,
    private val currency: Currency,
    private val clock: Clock,
    private val strings: NotificationStrings,
) {
    private val evalMutex = Mutex()

    fun start(scope: CoroutineScope) {
        combine(
            transactions.observe(),
            brands.observeAll(),
            categories.observeAll(),
            limits.observeAll(),
        ) { txs, brandList, categoryList, limitList ->
            Inputs(txs, brandList, categoryList, limitList)
        }
            .onEach { runEvaluate(it) }
            .launchIn(scope)
    }

    /**
     * Evaluate once against the current data and return. Used by the SMS path so a transaction
     * captured in the background (process may die right after the broadcast) still produces its
     * budget alert without depending on the long-lived [start] collector running to completion.
     * Idempotent: the per-month [alertDao] dedup means re-evaluating never double-alerts.
     */
    suspend fun evaluateNow() {
        runEvaluate(
            Inputs(
                transactions = transactions.observe().first(),
                brands = brands.observeAll().first(),
                categories = categories.observeAll().first(),
                limits = limits.observeAll().first(),
            ),
        )
    }

    /** Serialize the [start] collector and [evaluateNow] so they can't race the alert-level read-modify-write. */
    private suspend fun runEvaluate(inputs: Inputs) = evalMutex.withLock { evaluate(inputs) }

    private suspend fun evaluate(inputs: Inputs) {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.from(clock.today(zone))
        val periodMonth = month.year * 100 + month.monthValue
        val monthStart = month.atDay(1).atStartOfDay(zone).toInstant()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

        val categoryOfBrand = inputs.brands.associate { it.id to it.categoryId }
        val spentByCategory = HashMap<CategoryId, Long>()
        for (tx in inputs.transactions) {
            if (tx.occurredAt.isBefore(monthStart) || !tx.occurredAt.isBefore(monthEnd)) continue
            val categoryId = categoryOfBrand[tx.brandId] ?: continue
            spentByCategory[categoryId] = (spentByCategory[categoryId] ?: 0L) + tx.amount.amountMinor
        }

        for (category in inputs.categories) {
            if (category.type != CategoryType.EXPENSES) continue
            val limit = inputs.limits.effectiveFor(category.id, month)?.amountMinor ?: continue
            if (limit <= 0L) continue
            val spent = spentByCategory[category.id] ?: 0L
            val level = levelFor(spent, limit)
            if (level == 0) continue
            val lastLevel = alertDao.getLevel(category.id.value, periodMonth) ?: 0
            if (level <= lastLevel) continue

            fire(category, spent, limit, level)
            alertDao.upsert(CategoryLimitAlertEntity(category.id.value, periodMonth, level))
        }
    }

    private suspend fun fire(category: Category, spent: Long, limit: Long, level: Int) {
        val title = if (level >= 100) {
            strings.budgetReachedTitle(category.name)
        } else {
            strings.budgetLevelTitle(category.name, level)
        }
        val message = strings.budgetMessage(format(spent), format(limit))
        val notification = Notification(
            id = NotificationId.new(),
            title = title,
            message = message,
            type = Notification.TYPE_CATEGORY_LIMIT,
            categoryId = category.id.value,
            createdAt = clock.now(),
            isRead = false,
        )
        notifications.create(notification)
        systemNotifier.post(notification)
    }

    /** Highest threshold reached, in whole-percent terms, using integer math. */
    private fun levelFor(spent: Long, limit: Long): Int = when {
        spent >= limit -> 100
        spent * 100 >= limit * 80 -> 80
        spent * 100 >= limit * 50 -> 50
        else -> 0
    }

    private fun format(amountMinor: Long): String =
        "${currency.code} " + "%,.2f".format(amountMinor / 100.0)

    private data class Inputs(
        val transactions: List<Transaction>,
        val brands: List<Brand>,
        val categories: List<Category>,
        val limits: List<CategoryLimit>,
    )
}
