package com.hisabak.testutil

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.budget.domain.Budget
import com.hisabak.feature.budget.domain.BudgetId
import com.hisabak.feature.budget.domain.BudgetRepository
import com.hisabak.feature.budget.domain.BudgetWindow
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryLimitRepository
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.notification.data.local.CategoryLimitAlertDao
import com.hisabak.feature.notification.data.local.CategoryLimitAlertEntity
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationId
import com.hisabak.feature.notification.domain.NotificationRepository
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.domain.TransactionRecordedAlert
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.transaction.domain.PagedTransactions
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionFilter
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    private val items = MutableStateFlow(initial)
    val current: List<Transaction> get() = items.value

    fun emit(transactions: List<Transaction>) { items.value = transactions }

    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> = items.map { list ->
        list.filter { tx ->
            (filter.brandId == null || tx.brandId == filter.brandId) &&
                (filter.search == null || tx.note?.contains(filter.search, ignoreCase = true) == true) &&
                (filter.dateFrom == null || !tx.occurredAt.isBefore(filter.dateFrom)) &&
                (filter.dateTo == null || tx.occurredAt.isBefore(filter.dateTo))
        }
    }

    override suspend fun getPage(filter: TransactionFilter, page: Int, perPage: Int): DomainResult<PagedTransactions> {
        val all = items.value
        val from = (page - 1) * perPage
        val slice = all.drop(from).take(perPage)
        return DomainResult.Success(PagedTransactions(slice, page, perPage, all.size.toLong()))
    }

    override suspend fun getById(id: TransactionId): DomainResult<Transaction> =
        items.value.firstOrNull { it.id == id }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Transaction", id.value))

    override suspend fun upsert(transaction: Transaction): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == transaction.id } + transaction
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: TransactionId): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }

    override suspend fun reassignBrand(fromBrandId: BrandId, toBrandId: BrandId): DomainResult<Unit> {
        items.value = items.value.map {
            if (it.brandId == fromBrandId) it.copy(brandId = toBrandId) else it
        }
        return DomainResult.Success(Unit)
    }
}

class FakeBrandRepository(initial: List<Brand> = emptyList()) : BrandRepository {
    private val items = MutableStateFlow(initial)
    val current: List<Brand> get() = items.value

    fun emit(brands: List<Brand>) { items.value = brands }

    /** When set to a Failure, [delete] returns it without removing — mimics an FK RESTRICT. */
    var deleteResult: DomainResult<Unit> = DomainResult.Success(Unit)

    override fun observeAll(search: String?, categoryId: CategoryId?): Flow<List<Brand>> = items.map { list ->
        list.filter { brand ->
            (search == null || brand.name.contains(search, ignoreCase = true)) &&
                (categoryId == null || brand.categoryId == categoryId)
        }
    }

    override suspend fun getById(id: BrandId): DomainResult<Brand> =
        items.value.firstOrNull { it.id == id }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Brand", id.value))

    override suspend fun findByNameLike(name: String): Brand? =
        items.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun upsert(brand: Brand): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == brand.id } + brand
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: BrandId): DomainResult<Unit> {
        if (deleteResult is DomainResult.Failure) return deleteResult
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }

    override suspend fun countTransactions(id: BrandId): Long = 0L
}

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val items = MutableStateFlow(initial)
    val current: List<Category> get() = items.value

    fun emit(categories: List<Category>) { items.value = categories }

    override fun observeAll(type: CategoryType?, search: String?): Flow<List<Category>> = items.map { list ->
        list.filter { category ->
            (type == null || category.type == type) &&
                (search == null || category.name.contains(search, ignoreCase = true))
        }
    }

    override suspend fun getById(id: CategoryId): DomainResult<Category> =
        items.value.firstOrNull { it.id == id }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Category", id.value))

    override suspend fun upsert(category: Category): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == category.id } + category
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: CategoryId): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }
}

class FakeCategoryLimitRepository(initial: List<CategoryLimit> = emptyList()) : CategoryLimitRepository {
    private val items = MutableStateFlow(initial)
    val current: List<CategoryLimit> get() = items.value

    fun emit(limits: List<CategoryLimit>) { items.value = limits }

    override fun observeAll(): Flow<List<CategoryLimit>> = items

    override suspend fun setLimit(categoryId: CategoryId, amount: Money?, effectiveFrom: YearMonth) {
        items.value = items.value.filterNot {
            it.categoryId == categoryId && it.effectiveFrom == effectiveFrom
        } + CategoryLimit(categoryId, amount, effectiveFrom)
    }
}

class FakeBudgetRepository(
    initial: List<Budget> = emptyList(),
    var sumInWindow: Money = aed(0),
) : BudgetRepository {
    private val items = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Budget>> = items

    override suspend fun getById(id: BudgetId): DomainResult<Budget> =
        items.value.firstOrNull { it.id == id }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Budget", id.value))

    override suspend fun upsert(budget: Budget): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == budget.id } + budget
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: BudgetId): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }

    override suspend fun sumTransactionsIn(budget: Budget, window: BudgetWindow): Money = sumInWindow
}

class FakeNotificationRepository : NotificationRepository {
    private val items = MutableStateFlow<List<Notification>>(emptyList())
    val created: List<Notification> get() = items.value

    override fun observeAll(): Flow<List<Notification>> = items
    override fun observeUnreadCount(): Flow<Int> = items.map { list -> list.count { !it.isRead } }

    override suspend fun create(notification: Notification) {
        items.value = items.value + notification
    }

    override suspend fun markRead(id: NotificationId) {
        items.value = items.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    override suspend fun markAllRead() {
        items.value = items.value.map { it.copy(isRead = true) }
    }

    override suspend fun delete(id: NotificationId) {
        items.value = items.value.filterNot { it.id == id }
    }
}

class FakeSmsRepository(initial: List<SmsMessage> = emptyList()) : SmsRepository {
    private val items = MutableStateFlow(initial)
    val current: List<SmsMessage> get() = items.value

    override fun observeAll(search: String?): Flow<List<SmsMessage>> = items.map { list ->
        if (search == null) list else list.filter { it.body.contains(search, ignoreCase = true) }
    }

    override suspend fun getById(id: SmsMessageId): DomainResult<SmsMessage> =
        items.value.firstOrNull { it.id == id }
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Sms", id.value))

    override suspend fun upsert(message: SmsMessage): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == message.id } + message
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: SmsMessageId): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }

    override suspend fun existsByContent(body: String, receivedAt: Instant): Boolean =
        items.value.any { it.body == body && it.receivedAt == receivedAt }
}

/** Records every posted notification so tests can assert what fired (and how many times). */
class RecordingNotifier : Notifier {
    val posted = mutableListOf<Notification>()
    val recorded = mutableListOf<TransactionRecordedAlert>()
    override fun post(notification: Notification) { posted += notification }
    override fun postTransactionRecorded(alert: TransactionRecordedAlert) { recorded += alert }
}

/** In-memory stand-in for the Room DAO that tracks the highest alert level per category/month. */
class FakeCategoryLimitAlertDao : CategoryLimitAlertDao {
    private val levels = mutableMapOf<Pair<String, Int>, Int>()

    override suspend fun getLevel(categoryId: String, periodMonth: Int): Int? =
        levels[categoryId to periodMonth]

    override suspend fun upsert(entity: CategoryLimitAlertEntity) {
        levels[entity.categoryId to entity.periodMonth] = entity.lastLevel
    }
}
