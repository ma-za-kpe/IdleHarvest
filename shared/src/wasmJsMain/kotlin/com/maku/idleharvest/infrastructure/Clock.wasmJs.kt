package com.maku.idleharvest.infrastructure

// Temporary wasm implementation: return 0L to avoid JS interop/compiler issues.
// This is safe for compilation and can be replaced with a proper JS interop
// implementation later if accurate timestamps are required.
actual fun currentTimeMillis(): Long = 0L
