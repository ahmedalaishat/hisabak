package com.hisabak.core.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
