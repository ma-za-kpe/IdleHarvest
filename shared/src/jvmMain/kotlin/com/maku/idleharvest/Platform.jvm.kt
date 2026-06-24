package com.maku.idleharvest

private data class JvmPlatform(
    override val name: String = "JVM",
) : Platform

actual fun getPlatform(): Platform = JvmPlatform()
