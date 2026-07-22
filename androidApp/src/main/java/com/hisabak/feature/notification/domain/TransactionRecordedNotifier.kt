package com.hisabak.feature.notification.domain

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.transaction.domain.Transaction

/**
 * Builds and posts the "Transaction recorded" confirmation after the SMS path saves a transaction.
 * Resolves the transaction's brand and (optional) category so the notification can summarize the
 * amount + brand, show the category glyph, and tell the user to categorize an unknown brand.
 *
 * SMS-only on purpose: the broadcast path calls this, while manual in-app edits don't — the user
 * is already looking at the app then and doesn't need a heads-up.
 */
class TransactionRecordedNotifier(
    private val brands: BrandRepository,
    private val categories: CategoryRepository,
    private val notifier: Notifier,
    private val currency: Currency,
    private val strings: NotificationStrings,
) {
    suspend fun notify(transaction: Transaction) {
        val brand = brands.getById(transaction.brandId).getOrNull() ?: return
        val category = brand.categoryId?.let { categories.getById(it).getOrNull() }

        val amount = format(transaction.amount.amountMinor)
        val message = if (category != null) {
            strings.transactionRecorded(amount, brand.name, category.name)
        } else {
            strings.transactionRecordedUncategorized(amount, brand.name)
        }

        notifier.postTransactionRecorded(
            TransactionRecordedAlert(
                transactionId = transaction.id.value,
                title = strings.transactionRecordedTitle(),
                message = message,
                categoryId = category?.id?.value,
                brandId = transaction.brandId.value,
                iconKey = category?.icon,
                colorKey = category?.color,
            ),
        )
    }

    private fun format(amountMinor: Long): String =
        "${currency.code} " + "%,.2f".format(amountMinor / 100.0)
}
