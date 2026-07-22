package com.hisabak.feature.budget.domain

import java.time.temporal.ChronoUnit

enum class Reoccurrence(val unit: ChronoUnit?) {
    CUSTOM(null),
    DAILY(ChronoUnit.DAYS),
    WEEKLY(ChronoUnit.WEEKS),
    MONTHLY(ChronoUnit.MONTHS),
    YEARLY(ChronoUnit.YEARS);
}
