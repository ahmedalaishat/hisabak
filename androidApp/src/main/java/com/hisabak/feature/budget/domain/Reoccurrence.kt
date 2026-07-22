package com.hisabak.feature.budget.domain

import kotlinx.datetime.DateTimeUnit

enum class Reoccurrence(val unit: DateTimeUnit.DateBased?) {
    CUSTOM(null),
    DAILY(DateTimeUnit.DAY),
    WEEKLY(DateTimeUnit.WEEK),
    MONTHLY(DateTimeUnit.MONTH),
    YEARLY(DateTimeUnit.YEAR);
}
