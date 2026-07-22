package com.hisabak.core.common

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface Clock {
    fun now(): Instant
    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        now().toLocalDateTime(zone).date
}

class SystemClock : Clock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}
