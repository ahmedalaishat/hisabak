package com.hisabak.testutil

import com.hisabak.feature.notification.domain.NotificationStrings

/** Mirrors the English notification copy so domain tests can assert exact text without resources. */
class FakeNotificationStrings : NotificationStrings {
    override fun transactionRecordedTitle() = "Transaction recorded"
    override fun transactionRecorded(amount: String, brand: String, category: String) =
        "$amount at $brand · $category"
    override fun transactionRecordedUncategorized(amount: String, brand: String) =
        "$amount at $brand · Uncategorized — tap to add a category"
    override fun budgetReachedTitle(category: String) = "$category budget reached"
    override fun budgetLevelTitle(category: String, level: Int) = "$category at $level% of budget"
    override fun budgetMessage(spent: String, limit: String) = "Spent $spent of $limit this month."
}
