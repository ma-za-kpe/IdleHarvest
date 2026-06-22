package com.maku.idleharvest.generators

import com.maku.idleharvest.domain.models.AgentId
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.AirtimeBundle
import com.maku.idleharvest.domain.models.AirtimeBundleType
import com.maku.idleharvest.domain.models.AirtimeTransaction
import com.maku.idleharvest.domain.models.AutonomyLevel
import com.maku.idleharvest.domain.models.BenchmarkReport
import com.maku.idleharvest.domain.models.ComplianceRuleSet
import com.maku.idleharvest.domain.models.CryptographicProof
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.DePinContribution
import com.maku.idleharvest.domain.models.DeviceMetadata
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningSource
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnectionState
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.Policy
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ResourceThreshold
import com.maku.idleharvest.domain.models.ResourceType
import com.maku.idleharvest.domain.models.ThermalState
import com.maku.idleharvest.domain.models.TransactionOutcome
import com.maku.idleharvest.domain.models.TransactionType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid

/**
 * Custom Arb generators for IdleHarvest domain types.
 *
 * These generators produce realistic, constrained random instances for property-based testing.
 */

// Reference timestamp for generating realistic time values (approx mid-2024 epoch millis)
private const val REFERENCE_NOW_MS = 1_719_792_000_000L // 2024-07-01 00:00:00 UTC
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
private const val ONE_HOUR_MS = 3_600_000L

// --- Primitive domain generators ---

private val carrierArb: Arb<String> = Arb.of(
    "Safaricom", "Airtel", "MTN", "Glo", "9mobile", "Orange", "Vodacom"
)

private val currencyArb: Arb<String> = Arb.of(
    "KES", "NGN", "UGX", "TZS", "GHS", "ZAR", "USD"
)

private val bundleTypeArb: Arb<String> = Arb.of(
    "daily", "weekly", "monthly", "hourly"
)

private val platformArb: Arb<String> = Arb.of(
    "Prestmit", "VTU.ng", "Reloadly", "DT One"
)

private val tokenSymbolArb: Arb<String> = Arb.of(
    "GRASS", "TITAN", "HNT", "FIL", "AR"
)

private val socModelArb: Arb<String> = Arb.of(
    "Snapdragon 680", "Dimensity 700", "Helio G99", "Exynos 1280", "Cortex-A76"
)

private val coreConfigArb: Arb<String> = Arb.of(
    "4xA76+4xA55", "2xA78+6xA55", "4xA73+4xA53", "2xX1+2xA78+4xA55"
)

private val osVersionArb: Arb<String> = Arb.of(
    "Android 13", "Android 14", "Android 15", "iOS 17.4", "iOS 18.0"
)

private val agentIdArb: Arb<AgentId> = Arb.of(
    "airtime_agent", "depin_agent", "mesh_coordinator", "earning_engine"
).map { AgentId(it) }

// --- Domain type generators ---

/** Generates random AirtimeBalance instances. */
fun Arb.Companion.airtimeBalance(): Arb<AirtimeBalance> = arbitrary {
    AirtimeBalance(
        carrier = carrierArb.bind(),
        amountUnits = Arb.long(0L..100_000L).bind(),
        currency = currencyArb.bind(),
        expiryTimestamp = Arb.long(
            REFERENCE_NOW_MS..(REFERENCE_NOW_MS + THIRTY_DAYS_MS)
        ).orNull(0.3).bind(),
    )
}

/** Generates random DataBundle instances. */
fun Arb.Companion.dataBundle(): Arb<DataBundle> = arbitrary {
    val totalMb = Arb.long(100L..10_000L).bind()
    DataBundle(
        carrier = carrierArb.bind(),
        remainingMb = Arb.long(0L..totalMb).bind(),
        totalMb = totalMb,
        expiryTimestamp = Arb.long(
            REFERENCE_NOW_MS..(REFERENCE_NOW_MS + THIRTY_DAYS_MS)
        ).bind(),
        bundleType = bundleTypeArb.bind(),
    )
}

/** Generates random ResourceProfile instances. */
fun Arb.Companion.resourceProfile(): Arb<ResourceProfile> = arbitrary {
    ResourceProfile(
        airtimeBalance = Arb.airtimeBalance().orNull(0.2).bind(),
        dataBundles = Arb.list(Arb.dataBundle(), 0..5).bind(),
        availableBandwidthMbps = Arb.float(0f..100f).bind(),
        freeStorageMb = Arb.long(0L..128_000L).bind(),
        idleComputePercent = Arb.int(0..100).bind(),
        batteryLevel = Arb.int(0..100).bind(),
        isCharging = Arb.boolean().bind(),
        thermalState = Arb.enum<ThermalState>().bind(),
        timestamp = Arb.long(0L..REFERENCE_NOW_MS).bind(),
    )
}

/** Generates random AirtimeBundle instances (for monetization evaluation). */
fun Arb.Companion.airtimeBundle(): Arb<AirtimeBundle> = arbitrary {
    val purchasedAt = Arb.long(REFERENCE_NOW_MS - THIRTY_DAYS_MS..REFERENCE_NOW_MS).bind()
    AirtimeBundle(
        id = Arb.uuid().map { it.toString() }.bind(),
        carrier = carrierArb.bind(),
        type = Arb.enum<AirtimeBundleType>().bind(),
        amountUnits = Arb.long(100L..50_000L).bind(),
        currency = currencyArb.bind(),
        remainingMb = Arb.long(0L..5_000L).orNull(0.4).bind(),
        expiryTimestamp = Arb.long(
            REFERENCE_NOW_MS..(REFERENCE_NOW_MS + THIRTY_DAYS_MS)
        ).bind(),
        purchasedAt = purchasedAt,
    )
}

/** Generates random Policy instances. */
fun Arb.Companion.policy(): Arb<Policy> = arbitrary {
    Policy(
        id = Arb.uuid().map { it.toString() }.bind(),
        agentId = agentIdArb.bind(),
        autonomyLevel = Arb.enum<AutonomyLevel>().bind(),
        maxTransactionPerDay = Arb.double(1.0..10_000.0).orNull(0.3).bind(),
        maxTransactionSingle = Arb.double(0.5..5_000.0).orNull(0.3).bind(),
        resourceShareLimits = Arb.resourceThreshold().orNull(0.4).bind(),
        requireBiometricAbove = Arb.double(10.0..1_000.0).orNull(0.5).bind(),
        isActive = Arb.boolean().bind(),
    )
}

/** Generates random ResourceThreshold instances. */
fun Arb.Companion.resourceThreshold(): Arb<ResourceThreshold> = arbitrary {
    ResourceThreshold(
        bandwidthMinMbps = Arb.float(0.1f..50f).bind(),
        storageMinMb = Arb.long(100L..10_000L).bind(),
        computeMaxCpuPercent = Arb.int(5..80).bind(),
    )
}

/** Generates random AirtimeTransaction instances. */
fun Arb.Companion.airtimeTransaction(): Arb<AirtimeTransaction> = arbitrary {
    AirtimeTransaction(
        id = Arb.uuid().map { it.toString() }.bind(),
        type = Arb.enum<TransactionType>().bind(),
        amount = Arb.long(1L..50_000L).bind(),
        currency = currencyArb.bind(),
        counterparty = Arb.string(5..20).orNull(0.3).bind(),
        platform = platformArb.bind(),
        outcome = Arb.enum<TransactionOutcome>().bind(),
        timestamp = Arb.long(0L..REFERENCE_NOW_MS).bind(),
        complianceCheckId = Arb.uuid().map { it.toString() }.bind(),
    )
}

/** Generates random ComplianceRuleSet instances. */
fun Arb.Companion.complianceRuleSet(): Arb<ComplianceRuleSet> = arbitrary {
    ComplianceRuleSet(
        country = Arb.of("KE", "NG", "UG", "TZ", "GH", "ZA").bind(),
        carrier = carrierArb.orNull(0.2).bind(),
        dailyTransactionLimit = Arb.double(10.0..10_000.0).bind(),
        monthlyTransactionLimit = Arb.double(100.0..100_000.0).bind(),
        kycThreshold = Arb.double(50.0..5_000.0).bind(),
        rateLimitPerHour = Arb.int(1..100).bind(),
        version = Arb.int(1..50).bind(),
        lastUpdated = Arb.long(0L..REFERENCE_NOW_MS).bind(),
    )
}

/** Generates random BenchmarkReport instances. */
fun Arb.Companion.benchmarkReport(): Arb<BenchmarkReport> = arbitrary {
    val latencyMin = Arb.long(5L..50L).bind()
    val latencyMax = Arb.long(latencyMin + 10..latencyMin + 200).bind()
    val latencyMean = (latencyMin + latencyMax) / 2.0
    BenchmarkReport(
        modelId = Arb.uuid().map { it.toString() }.bind(),
        backend = Arb.enum<InferenceBackend>().bind(),
        iterationCount = 100,
        latencyMinMs = latencyMin,
        latencyMaxMs = latencyMax,
        latencyMeanMs = latencyMean,
        latencyP95Ms = Arb.long(latencyMin..latencyMax).bind(),
        memoryUsageMb = Arb.float(1f..256f).bind(),
        powerDrawMw = Arb.float(50f..2000f).orNull(0.3).bind(),
        deviceMetadata = Arb.deviceMetadata().bind(),
    )
}

/** Generates random DeviceMetadata instances. */
fun Arb.Companion.deviceMetadata(): Arb<DeviceMetadata> = arbitrary {
    DeviceMetadata(
        socModel = socModelArb.bind(),
        coreConfig = coreConfigArb.bind(),
        ramGb = Arb.float(2f..12f).bind(),
        osVersion = osVersionArb.bind(),
    )
}

/** Generates random Peer instances. */
fun Arb.Companion.peer(): Arb<Peer> = arbitrary {
    Peer(
        id = PeerId(Arb.uuid().map { it.toString() }.bind()),
        displayName = Arb.of(
            "Phone-A", "Phone-B", "Device-1", "IdleHarvest-Node", "Peer-X"
        ).bind(),
        resourceProfile = Arb.resourceProfile().bind(),
        signalStrength = Arb.int(-100..-30).bind(),
        connectionState = Arb.enum<PeerConnectionState>().bind(),
        lastSeen = Arb.long(0L..REFERENCE_NOW_MS).bind(),
    )
}

/** Generates random EarningEvent instances. */
fun Arb.Companion.earningEvent(): Arb<EarningEvent> = arbitrary {
    EarningEvent(
        id = Arb.uuid().map { it.toString() }.bind(),
        source = Arb.enum<EarningSource>().bind(),
        amountUsdc = Arb.double(0.001..100.0).bind(),
        amountLocal = Arb.double(1.0..50_000.0).orNull(0.3).bind(),
        localCurrency = currencyArb.orNull(0.3).bind(),
        agentId = agentIdArb.bind(),
        timestamp = Arb.long(0L..REFERENCE_NOW_MS).bind(),
    )
}

/** Generates random CryptographicProof instances. */
fun Arb.Companion.cryptographicProof(): Arb<CryptographicProof> = arbitrary {
    CryptographicProof(
        sessionId = Arb.uuid().map { it.toString() }.bind(),
        proofData = Arb.string(32..64).bind(),
        signature = Arb.string(64..128).bind(),
        timestamp = Arb.long(0L..REFERENCE_NOW_MS).bind(),
    )
}

/** Generates random DePinContribution instances. */
fun Arb.Companion.dePinContribution(): Arb<DePinContribution> = arbitrary {
    val startedAt = Arb.long(0L..REFERENCE_NOW_MS - ONE_HOUR_MS).bind()
    DePinContribution(
        networkId = Arb.uuid().map { it.toString() }.bind(),
        networkName = Arb.of("Grass", "Titan Network", "Filecoin", "Arweave", "Helium").bind(),
        resourceType = Arb.enum<ResourceType>().bind(),
        sessionId = Arb.uuid().map { it.toString() }.bind(),
        startedAt = startedAt,
        endedAt = Arb.long(startedAt..REFERENCE_NOW_MS).orNull(0.3).bind(),
        earnedTokens = Arb.double(0.0001..10.0).bind(),
        tokenSymbol = tokenSymbolArb.bind(),
        proof = Arb.cryptographicProof().orNull(0.4).bind(),
    )
}
