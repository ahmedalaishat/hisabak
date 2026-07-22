package com.hisabak.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun now(): Instant
    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = now().atZone(zone).toLocalDate()
}

class SystemClock(private val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun now(): Instant = Instant.now()
    override fun today(zone: ZoneId): LocalDate = Instant.now().atZone(zone).toLocalDate()
}
