package com.hisabak.testutil

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationId
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionId
import java.time.Instant
import java.time.YearMonth

/** Fixed defaults so test entities are terse to build and deterministic. */
val TEST_CURRENCY = Currency.AED
val FIXED_INSTANT: Instant = Instant.parse("2026-06-17T10:00:00Z")

fun aed(minor: Long): Money = Money(minor, TEST_CURRENCY)

fun brand(
    id: String = "b1",
    name: String = "Brand $id",
    categoryId: CategoryId? = null,
): Brand = Brand(
    id = BrandId(id),
    name = name,
    categoryId = categoryId,
)

fun category(
    id: String = "c1",
    name: String = "Category $id",
    type: CategoryType = CategoryType.EXPENSES,
    color: String = "gray",
    icon: String = "wallet",
): Category = Category(
    id = CategoryId(id),
    name = name,
    type = type,
    color = color,
    icon = icon,
)

fun categoryLimit(
    categoryId: String,
    amountMinor: Long?,
    effectiveFrom: YearMonth = YearMonth.of(2026, 6),
): CategoryLimit = CategoryLimit(
    categoryId = CategoryId(categoryId),
    amount = amountMinor?.let(::aed),
    effectiveFrom = effectiveFrom,
)

fun transaction(
    id: String = "t1",
    amountMinor: Long = 1_000L,
    brandId: String = "b1",
    note: String? = null,
    occurredAt: Instant = FIXED_INSTANT,
    sourceSmsId: String? = null,
): Transaction = Transaction(
    id = TransactionId(id),
    amount = aed(amountMinor),
    brandId = BrandId(brandId),
    note = note,
    occurredAt = occurredAt,
    sourceSmsId = sourceSmsId,
)

fun smsMessage(
    id: String = "s1",
    body: String,
    receivedAt: Instant = FIXED_INSTANT,
): SmsMessage = SmsMessage(
    id = SmsMessageId(id),
    body = body,
    receivedAt = receivedAt,
)

fun notification(
    id: String = "n1",
    title: String = "Title",
    message: String = "Message",
    type: String = Notification.TYPE_CATEGORY_LIMIT,
    isRead: Boolean = false,
    categoryId: String? = null,
): Notification = Notification(
    id = NotificationId(id),
    title = title,
    message = message,
    type = type,
    categoryId = categoryId,
    createdAt = FIXED_INSTANT,
    isRead = isRead,
)
