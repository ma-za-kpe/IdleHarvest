package com.maku.idleharvest

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
