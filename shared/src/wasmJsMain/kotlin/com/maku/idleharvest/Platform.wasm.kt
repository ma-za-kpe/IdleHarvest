package com.maku.idleharvest

internal class WasmPlatform : Platform {
    override val name: String = "wasm-js"
}

actual fun getPlatform(): Platform = WasmPlatform()
