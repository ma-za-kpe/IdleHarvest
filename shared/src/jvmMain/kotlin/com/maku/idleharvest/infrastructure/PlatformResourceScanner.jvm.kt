package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ThermalState

actual class PlatformResourceScanner {
    actual suspend fun scanAirtimeBalance(): AirtimeBalance? = null

    actual suspend fun scanDataBundles(): List<DataBundle> = emptyList()

    actual suspend fun scanBandwidth(): Float = 0f

    actual suspend fun scanFreeStorage(): Long = 0L

    actual suspend fun scanIdleCompute(): Int = 0

    actual suspend fun scanBatteryLevel(): Int = -1

    actual suspend fun scanIsCharging(): Boolean = false

    actual suspend fun scanThermalState(): ThermalState = ThermalState.COOL
}
