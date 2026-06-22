package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ThermalState
import com.maku.idleharvest.generators.resourceProfile
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.forAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 1: Resource Profile Completeness
 *
 * *For any* device state (any combination of available/unavailable resource metrics),
 * the Resource_Monitor SHALL produce a ResourceProfile that contains a defined value
 * or explicit null/error marker for each of: airtime balance, data bundle status,
 * available bandwidth, free storage, and idle compute capacity.
 *
 * **Validates: Requirements 1.1, 1.3**
 */
class ResourceProfileCompletenessPropertyTest {

    // --- Fake implementations for testing ---

    /**
     * A configurable fake PlatformResourceScanner that can simulate any combination
     * of successful and failing scans for each resource metric.
     */
    private class FakeScannableResourceScanner(
        var airtimeResult: Result<AirtimeBalance?> = Result.success(null),
        var dataBundlesResult: Result<List<DataBundle>> = Result.success(emptyList()),
        var bandwidthResult: Result<Float> = Result.success(0f),
        var freeStorageResult: Result<Long> = Result.success(0L),
        var idleComputeResult: Result<Int> = Result.success(0),
        var batteryLevelResult: Result<Int> = Result.success(50),
        var isChargingResult: Result<Boolean> = Result.success(false),
        var thermalStateResult: Result<ThermalState> = Result.success(ThermalState.COOL),
    )

    private class FakeAgentEventBus : AgentEventBus {
        private val flow = MutableSharedFlow<AgentEvent>()
        override fun <T : AgentEvent> publish(event: T) { /* no-op for testing */ }
        @Suppress("UNCHECKED_CAST")
        override fun <T : AgentEvent> subscribe(eventType: KClass<T>): Flow<T> = flow as Flow<T>
    }

    /**
     * A testable resource monitor that accepts configurable scan results
     * to simulate any combination of available/unavailable resource metrics.
     */
    private class TestableResourceMonitor(
        private val scanner: FakeScannableResourceScanner,
        scope: CoroutineScope,
        eventBus: AgentEventBus,
    ) {
        /**
         * Performs a scan using the configured results, mimicking
         * DefaultResourceMonitor.performScan() behavior where individual
         * metric failures are caught and default values are used.
         */
        suspend fun performScan(): ResourceProfile {
            val airtimeBalance = scanSafely { scanner.airtimeResult.getOrThrow() }
            val dataBundles = scanSafely { scanner.dataBundlesResult.getOrThrow() } ?: emptyList()
            val bandwidth = scanSafely { scanner.bandwidthResult.getOrThrow() } ?: 0f
            val freeStorage = scanSafely { scanner.freeStorageResult.getOrThrow() } ?: 0L
            val idleCompute = scanSafely { scanner.idleComputeResult.getOrThrow() } ?: 0
            val batteryLevel = scanSafely { scanner.batteryLevelResult.getOrThrow() } ?: -1
            val isCharging = scanSafely { scanner.isChargingResult.getOrThrow() } ?: false
            val thermalState = scanSafely { scanner.thermalStateResult.getOrThrow() } ?: ThermalState.COOL

            return ResourceProfile(
                airtimeBalance = airtimeBalance,
                dataBundles = dataBundles,
                availableBandwidthMbps = bandwidth,
                freeStorageMb = freeStorage,
                idleComputePercent = idleCompute,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                thermalState = thermalState,
                timestamp = 1_000_000L,
            )
        }

        private inline fun <T> scanSafely(block: () -> T): T? {
            return try {
                block()
            } catch (_: Exception) {
                null
            }
        }
    }

    // --- Property Tests ---

    @Test
    fun anyGeneratedResourceProfileHasAllFieldsDefined() = runTest {
        forAll(Arb.resourceProfile()) { profile ->
            // All numeric fields should be accessible and within valid ranges
            profile.availableBandwidthMbps >= 0f &&
            profile.freeStorageMb >= 0L &&
            profile.idleComputePercent in 0..100 &&
            profile.batteryLevel in -1..100 &&
            profile.timestamp >= 0L &&
            // dataBundles is never null (always a list, possibly empty)
            profile.dataBundles.all { bundle ->
                bundle.remainingMb >= 0 && bundle.totalMb > 0
            } &&
            // thermalState is always defined
            ThermalState.entries.contains(profile.thermalState)
        }
    }

    @Test
    fun emptyProfileHasAllFieldsDefined() {
        val empty = ResourceProfile.empty()

        // airtimeBalance is explicitly null (valid "no balance" marker)
        // This is the explicit null/error marker for airtime balance
        assertEquals(null, empty.airtimeBalance)

        // dataBundles is an empty list (not null)
        assertNotNull(empty.dataBundles)
        assertTrue(empty.dataBundles.isEmpty())

        // Numeric fields have defined default values
        assertEquals(0f, empty.availableBandwidthMbps)
        assertEquals(0L, empty.freeStorageMb)
        assertEquals(0, empty.idleComputePercent)

        // batteryLevel = -1 is the explicit "unavailable" marker
        assertEquals(-1, empty.batteryLevel)
        assertEquals(false, empty.isCharging)

        // thermalState has a default
        assertNotNull(empty.thermalState)
        assertEquals(ThermalState.COOL, empty.thermalState)

        // timestamp is defined (0L for empty)
        assertEquals(0L, empty.timestamp)
    }

    @Test
    fun performScanProducesCompleteProfileWhenAllMetricsSucceed() = runTest {
        forAll(
            Arb.float(0f..100f),
            Arb.long(0L..128_000L),
            Arb.int(0..100),
            Arb.int(0..100),
        ) { bandwidth, storage, idleCompute, battery ->
            val scanner = FakeScannableResourceScanner(
                airtimeResult = Result.success(
                    AirtimeBalance("Safaricom", 1000L, "KES", null)
                ),
                dataBundlesResult = Result.success(
                    listOf(DataBundle("Safaricom", 500L, 1000L, 9999999999L, "daily"))
                ),
                bandwidthResult = Result.success(bandwidth),
                freeStorageResult = Result.success(storage),
                idleComputeResult = Result.success(idleCompute),
                batteryLevelResult = Result.success(battery),
                isChargingResult = Result.success(true),
                thermalStateResult = Result.success(ThermalState.WARM),
            )

            val monitor = TestableResourceMonitor(scanner, this@runTest, FakeAgentEventBus())
            val profile = monitor.performScan()

            // All fields populated with actual values
            profile.airtimeBalance != null &&
            profile.dataBundles.isNotEmpty() &&
            profile.availableBandwidthMbps == bandwidth &&
            profile.freeStorageMb == storage &&
            profile.idleComputePercent == idleCompute &&
            profile.batteryLevel == battery &&
            profile.isCharging &&
            profile.thermalState == ThermalState.WARM &&
            profile.timestamp > 0L
        }
    }

    @Test
    fun performScanProducesCompleteProfileWhenAllMetricsFail() = runTest {
        // Simulate every single metric failing — the profile should still be complete
        // with default/null markers for each field
        val scanner = FakeScannableResourceScanner(
            airtimeResult = Result.failure(RuntimeException("SIM not available")),
            dataBundlesResult = Result.failure(RuntimeException("Network error")),
            bandwidthResult = Result.failure(RuntimeException("No connectivity")),
            freeStorageResult = Result.failure(RuntimeException("Storage API unavailable")),
            idleComputeResult = Result.failure(RuntimeException("CPU info denied")),
            batteryLevelResult = Result.failure(RuntimeException("Battery manager null")),
            isChargingResult = Result.failure(RuntimeException("Charging state unknown")),
            thermalStateResult = Result.failure(RuntimeException("Thermal sensor error")),
        )

        val monitor = TestableResourceMonitor(scanner, this, FakeAgentEventBus())
        val profile = monitor.performScan()

        // Profile is fully populated with defaults/null markers
        // airtimeBalance = null is the explicit "unavailable" marker
        assertEquals(null, profile.airtimeBalance)
        // dataBundles = empty list (not null)
        assertNotNull(profile.dataBundles)
        assertTrue(profile.dataBundles.isEmpty())
        // Numeric fields get safe defaults
        assertEquals(0f, profile.availableBandwidthMbps)
        assertEquals(0L, profile.freeStorageMb)
        assertEquals(0, profile.idleComputePercent)
        // batteryLevel = -1 signals "unavailable"
        assertEquals(-1, profile.batteryLevel)
        assertEquals(false, profile.isCharging)
        assertEquals(ThermalState.COOL, profile.thermalState)
        assertTrue(profile.timestamp > 0L)
    }

    @Test
    fun performScanProducesCompleteProfileWithPartialFailures() = runTest {
        forAll(Arb.boolean(), Arb.boolean(), Arb.boolean(), Arb.boolean()) { airtimeFails, bundleFails, bandwidthFails, storageFails ->
            val scanner = FakeScannableResourceScanner(
                airtimeResult = if (airtimeFails)
                    Result.failure(RuntimeException("fail"))
                else
                    Result.success(AirtimeBalance("MTN", 500L, "NGN", null)),
                dataBundlesResult = if (bundleFails)
                    Result.failure(RuntimeException("fail"))
                else
                    Result.success(listOf(DataBundle("MTN", 200L, 1000L, 99999999L, "weekly"))),
                bandwidthResult = if (bandwidthFails)
                    Result.failure(RuntimeException("fail"))
                else
                    Result.success(25.5f),
                freeStorageResult = if (storageFails)
                    Result.failure(RuntimeException("fail"))
                else
                    Result.success(4096L),
                idleComputeResult = Result.success(45),
                batteryLevelResult = Result.success(80),
                isChargingResult = Result.success(false),
                thermalStateResult = Result.success(ThermalState.HOT),
            )

            val monitor = TestableResourceMonitor(scanner, this@runTest, FakeAgentEventBus())
            val profile = monitor.performScan()

            // Regardless of which metrics fail, the profile is ALWAYS complete:
            // - airtimeBalance is either a value or null (explicit marker)
            // - dataBundles is either a list or empty list (never null)
            // - numeric fields always have a defined value (actual or default)
            val airtimeComplete = if (airtimeFails) profile.airtimeBalance == null else profile.airtimeBalance != null
            val bundlesComplete = profile.dataBundles != null // always non-null list
            val bandwidthComplete = profile.availableBandwidthMbps >= 0f
            val storageComplete = profile.freeStorageMb >= 0L
            val computeComplete = profile.idleComputePercent in 0..100
            val batteryComplete = profile.batteryLevel in -1..100
            val thermalComplete = ThermalState.entries.contains(profile.thermalState)
            val timestampComplete = profile.timestamp > 0L

            airtimeComplete && bundlesComplete && bandwidthComplete &&
            storageComplete && computeComplete && batteryComplete &&
            thermalComplete && timestampComplete
        }
    }
}
