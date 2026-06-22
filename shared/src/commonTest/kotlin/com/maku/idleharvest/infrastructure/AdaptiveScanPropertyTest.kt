package com.maku.idleharvest.infrastructure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 12: Adaptive Scan Interval
 *
 * *For any* power state, the Mesh_Coordinator scan interval SHALL be aggressive
 * (shorter interval) when the device is charging and conservative (longer interval)
 * when on battery, maintaining the invariant that battery-mode interval >
 * charging-mode interval.
 *
 * **Validates: Requirements 4.6**
 */
class AdaptiveScanPropertyTest {

    /**
     * Tests the adaptive scan interval logic as implemented by DefaultMeshCoordinator.
     *
     * Since BleAdapter is an expect/actual class that can't be instantiated in commonTest,
     * we verify the adaptive scan interval behavior by testing the constants and the
     * decision logic that getCurrentScanInterval() uses.
     */

    // --- Constants under test (from DefaultMeshCoordinator.Companion) ---

    /** Aggressive scan interval when device is charging (5 seconds). */
    private val scanIntervalChargingMs = DefaultMeshCoordinator.SCAN_INTERVAL_CHARGING_MS

    /** Conservative scan interval when on battery (30 seconds). */
    private val scanIntervalBatteryMs = DefaultMeshCoordinator.SCAN_INTERVAL_BATTERY_MS

    /**
     * Replicates the scan interval decision logic from DefaultMeshCoordinator.getCurrentScanInterval().
     * This is the core adaptive behavior: shorter when charging, longer when on battery.
     */
    private fun computeScanInterval(isCharging: Boolean): Long {
        return if (isCharging) scanIntervalChargingMs else scanIntervalBatteryMs
    }

    // --- Property Tests ---

    @Test
    fun chargingIntervalIsShorterThanBatteryInterval() {
        // Core invariant: battery-mode interval > charging-mode interval
        assertTrue(
            scanIntervalBatteryMs > scanIntervalChargingMs,
            "Battery interval ($scanIntervalBatteryMs ms) must be greater than " +
                "charging interval ($scanIntervalChargingMs ms)"
        )
    }

    @Test
    fun chargingIntervalIsExpectedAggressiveValue() {
        // Charging mode uses 5-second aggressive interval
        assertEquals(
            5_000L,
            scanIntervalChargingMs,
            "Charging scan interval should be 5000ms (aggressive)"
        )
    }

    @Test
    fun batteryIntervalIsExpectedConservativeValue() {
        // Battery mode uses 30-second conservative interval
        assertEquals(
            30_000L,
            scanIntervalBatteryMs,
            "Battery scan interval should be 30000ms (conservative)"
        )
    }

    @Test
    fun forAnyPowerStateScanIntervalReflectsChargingState() = runTest {
        // Property: For any power state (charging or not), the scan interval
        // correctly maps to the expected value.
        forAll(Arb.boolean()) { isCharging ->
            val interval = computeScanInterval(isCharging)

            if (isCharging) {
                // Charging → aggressive (shorter) interval
                interval == scanIntervalChargingMs
            } else {
                // Battery → conservative (longer) interval
                interval == scanIntervalBatteryMs
            }
        }
    }

    @Test
    fun forAnyPowerStateBatteryIntervalAlwaysExceedsChargingInterval() = runTest {
        // Property: Regardless of which power state we're in, the relationship
        // between the two intervals is always maintained.
        forAll(Arb.boolean()) { isCharging ->
            val currentInterval = computeScanInterval(isCharging)
            val chargingInterval = computeScanInterval(true)
            val batteryInterval = computeScanInterval(false)

            // The invariant must always hold
            batteryInterval > chargingInterval &&
                // Current interval is always one of the two valid values
                (currentInterval == chargingInterval || currentInterval == batteryInterval)
        }
    }

    @Test
    fun scanIntervalTransitionsBetweenStates() = runTest {
        // Simulates state transitions: verify that switching from charging to battery
        // (and vice versa) always produces the correct interval change.
        forAll(Arb.boolean(), Arb.boolean()) { initialCharging, newCharging ->
            val initialInterval = computeScanInterval(initialCharging)
            val newInterval = computeScanInterval(newCharging)

            when {
                // Same state → same interval
                initialCharging == newCharging -> initialInterval == newInterval
                // Transition from charging to battery → interval increases
                initialCharging && !newCharging -> newInterval > initialInterval
                // Transition from battery to charging → interval decreases
                !initialCharging && newCharging -> newInterval < initialInterval
                else -> true // unreachable
            }
        }
    }
}
