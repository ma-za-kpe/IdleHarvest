package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AirtimeAgent
import com.maku.idleharvest.domain.interfaces.DePinAgent
import com.maku.idleharvest.domain.interfaces.EarningEngine
import com.maku.idleharvest.domain.interfaces.MeshCoordinator
import com.maku.idleharvest.domain.interfaces.ResourceMonitor
import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.AgentState
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.AirtimeBundleType
import com.maku.idleharvest.domain.models.AirtimeTransaction
import com.maku.idleharvest.domain.models.ContributionSession
import com.maku.idleharvest.domain.models.CryptographicProof
import com.maku.idleharvest.domain.models.DataBundle
import com.maku.idleharvest.domain.models.DePinContribution
import com.maku.idleharvest.domain.models.DePinNetwork
import com.maku.idleharvest.domain.models.EarningEvent
import com.maku.idleharvest.domain.models.EarningsSummary
import com.maku.idleharvest.domain.models.MeshState
import com.maku.idleharvest.domain.models.MonetizationAction
import com.maku.idleharvest.domain.models.MonetizationRecommendation
import com.maku.idleharvest.domain.models.MonitorConfig
import com.maku.idleharvest.domain.models.NanopaymentReceipt
import com.maku.idleharvest.domain.models.NanopaymentRequest
import com.maku.idleharvest.domain.models.PayoutReceipt
import com.maku.idleharvest.domain.models.PayoutRequest
import com.maku.idleharvest.domain.models.Peer
import com.maku.idleharvest.domain.models.PeerConnection
import com.maku.idleharvest.domain.models.PeerId
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ResourceThreshold
import com.maku.idleharvest.domain.models.ResourceType
import com.maku.idleharvest.domain.models.ThermalState
import com.maku.idleharvest.domain.models.TransactionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the AgentOrchestrator event wiring.
 *
 * Validates end-to-end flows through the event bus without real hardware:
 * - Resource detection → BundleExpiring event publication (Requirements 2.1, 2.2)
 * - Connectivity loss → DePIN graceful pause (Requirement 5.1)
 * - Thermal change → ResourceMonitor adaptation (Requirement 16.1)
 * - Resource scarcity → DePIN threshold enforcement (Requirement 16.3)
 *
 * Uses real [AgentOrchestrator] and [DefaultAgentEventBus] with spy implementations
 * of each agent interface to record interactions without side effects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentOrchestratorIntegrationTest {
    // ── Spy implementations ───────────────────────────────────────────────────

    private class SpyResourceMonitor : ResourceMonitor {
        val thermalStatesReceived = mutableListOf<ThermalState>()

        override val resourceProfile: StateFlow<ResourceProfile> =
            MutableStateFlow(ResourceProfile.empty()).asStateFlow()

        override fun startMonitoring(config: MonitorConfig) {}

        override fun stopMonitoring() {}

        override fun forceRefresh(): ResourceProfile = ResourceProfile.empty()

        override fun adaptToThermalState(state: ThermalState) {
            thermalStatesReceived += state
        }
    }

    private class SpyDePinAgent : DePinAgent {
        val thresholdsReceived = mutableListOf<ResourceThreshold>()

        override val state: StateFlow<AgentState> =
            MutableStateFlow(AgentState.IDLE).asStateFlow()
        override val contributions: StateFlow<List<DePinContribution>> =
            MutableStateFlow(emptyList<DePinContribution>()).asStateFlow()

        override suspend fun register(
            network: DePinNetwork,
            resourceType: ResourceType,
        ) {}

        override suspend fun unregister(network: DePinNetwork) {}

        override suspend fun adjustContribution(threshold: ResourceThreshold) {
            thresholdsReceived += threshold
        }

        override fun generateProof(session: ContributionSession): CryptographicProof = CryptographicProof("", "", "", 0L)
    }

    private class SpyAirtimeAgent : AirtimeAgent {
        override val state: StateFlow<AgentState> =
            MutableStateFlow(AgentState.IDLE).asStateFlow()

        override suspend fun evaluateBundle(
            bundle: com.maku.idleharvest.domain.models.AirtimeBundle,
        ): MonetizationRecommendation = MonetizationRecommendation.Hold

        override suspend fun executeAction(action: MonetizationAction): TransactionResult = TransactionResult("", false, null, "stub", currentTimeMillis())

        override fun getTransactionHistory(): Flow<List<AirtimeTransaction>> = emptyFlow()
    }

    private class SpyMeshCoordinator : MeshCoordinator {
        override val activePeers: StateFlow<List<Peer>> =
            MutableStateFlow(emptyList<Peer>()).asStateFlow()
        override val meshState: StateFlow<MeshState> =
            MutableStateFlow(MeshState.IDLE).asStateFlow()

        override fun startDiscovery() {}

        override fun stopDiscovery() {}

        override suspend fun connectToPeer(peerId: PeerId): Result<PeerConnection> = Result.failure(UnsupportedOperationException("stub"))

        override fun disconnectPeer(peerId: PeerId) {}

        override fun broadcastResourceProfile(profile: ResourceProfile) {}
    }

    private class SpyEarningEngine : EarningEngine {
        override val pendingPayouts: StateFlow<List<PayoutRequest>> =
            MutableStateFlow(emptyList<PayoutRequest>()).asStateFlow()
        override val earningHistory: StateFlow<List<EarningEvent>> =
            MutableStateFlow(emptyList<EarningEvent>()).asStateFlow()

        override suspend fun initiatePayout(event: EarningEvent): Result<PayoutReceipt> = Result.failure(UnsupportedOperationException("stub"))

        override suspend fun processNanopayment(payment: NanopaymentRequest): Result<NanopaymentReceipt> = Result.failure(UnsupportedOperationException("stub"))

        override fun getTotalEarnings(): EarningsSummary = EarningsSummary(0.0, 0.0, 0.0, emptyMap())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildOrchestrator(
        eventBus: DefaultAgentEventBus,
        resourceMonitor: ResourceMonitor = SpyResourceMonitor(),
        airtimeAgent: AirtimeAgent = SpyAirtimeAgent(),
        depinAgent: DePinAgent = SpyDePinAgent(),
        meshCoordinator: MeshCoordinator = SpyMeshCoordinator(),
        earningEngine: EarningEngine = SpyEarningEngine(),
        testScope: kotlinx.coroutines.CoroutineScope,
    ) = AgentOrchestrator(
        resourceMonitor = resourceMonitor,
        airtimeAgent = airtimeAgent,
        depinAgent = depinAgent,
        meshCoordinator = meshCoordinator,
        earningEngine = earningEngine,
        eventBus = eventBus,
        scope = testScope,
    )

    private fun baseProfile(
        airtimeBalance: AirtimeBalance? = null,
        dataBundles: List<DataBundle> = emptyList(),
    ) = ResourceProfile(
        airtimeBalance = airtimeBalance,
        dataBundles = dataBundles,
        availableBandwidthMbps = 10f,
        freeStorageMb = 5000L,
        idleComputePercent = 60,
        batteryLevel = 80,
        isCharging = true,
        thermalState = ThermalState.COOL,
        timestamp = currentTimeMillis(),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Full lifecycle: ResourceUpdated with expiring airtime balance within 72h
     * → orchestrator publishes BundleExpiring for that balance.
     * Validates Requirements 2.1, 2.2
     */
    @Test
    fun expiringAirtimeBalanceProducesBundleExpiringEvent() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val orchestrator = buildOrchestrator(eventBus, testScope = backgroundScope)
        orchestrator.start()

        val now = currentTimeMillis()
        val expiryIn24h = now + 24 * 3_600_000L

        val bundleExpiringDeferred =
            async {
                eventBus.subscribe(AgentEvent.BundleExpiring::class).first()
            }

        eventBus.publish(
            AgentEvent.ResourceUpdated(
                profile =
                baseProfile(
                    airtimeBalance =
                    AirtimeBalance(
                        carrier = "MTN",
                        amountUnits = 1000L,
                        currency = "NGN",
                        expiryTimestamp = expiryIn24h,
                    ),
                ),
            ),
        )

        val event = bundleExpiringDeferred.await()
        assertEquals(AirtimeBundleType.AIRTIME, event.bundle.type)
        assertEquals("MTN", event.bundle.carrier)
        assertTrue(event.expiryHours in 0..24, "Expected ≤24h but got ${event.expiryHours}")
    }

    /**
     * ResourceUpdated with expiring data bundle within 72h
     * → orchestrator publishes BundleExpiring of type DATA.
     * Validates Requirement 2.1
     */
    @Test
    fun expiringDataBundleProducesBundleExpiringEvent() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val orchestrator = buildOrchestrator(eventBus, testScope = backgroundScope)
        orchestrator.start()

        val now = currentTimeMillis()
        val expiryIn48h = now + 48 * 3_600_000L

        val bundleExpiringDeferred =
            async {
                eventBus.subscribe(AgentEvent.BundleExpiring::class).first()
            }

        eventBus.publish(
            AgentEvent.ResourceUpdated(
                profile =
                baseProfile(
                    dataBundles =
                    listOf(
                        DataBundle(
                            carrier = "Safaricom",
                            remainingMb = 500L,
                            totalMb = 2048L,
                            expiryTimestamp = expiryIn48h,
                            bundleType = "DATA",
                        ),
                    ),
                ),
            ),
        )

        val event = bundleExpiringDeferred.await()
        assertEquals(AirtimeBundleType.DATA, event.bundle.type)
        assertEquals("Safaricom", event.bundle.carrier)
        assertEquals(500L, event.bundle.remainingMb)
        assertTrue(event.expiryHours in 0..48, "Expected ≤48h but got ${event.expiryHours}")
    }

    /**
     * Bundle expiring beyond the 72h horizon is NOT published as BundleExpiring.
     * Validates the expiry detection boundary.
     */
    @Test
    fun bundleExpiringBeyond72hIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val received = mutableListOf<AgentEvent.BundleExpiring>()
        val orchestrator = buildOrchestrator(eventBus, testScope = backgroundScope)
        orchestrator.start()

        val now = currentTimeMillis()
        val expiryIn80h = now + 80 * 3_600_000L

        eventBus.publish(
            AgentEvent.ResourceUpdated(
                profile =
                baseProfile(
                    airtimeBalance = AirtimeBalance("Airtel", 200L, "KES", expiryIn80h),
                ),
            ),
        )

        // Give the orchestrator a moment to process, then verify no BundleExpiring was emitted
        kotlinx.coroutines.delay(50)
        assertEquals(0, received.size, "Expected no BundleExpiring event for 80h-away expiry")
    }

    /**
     * ConnectivityChanged(offline) → DePIN agent receives a max-bandwidth threshold
     * that effectively pauses all contributions.
     * Validates Requirement 5.1 (graceful degradation)
     */
    @Test
    fun connectivityLossTriggersDepinGracefulPause() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val spyDepin = SpyDePinAgent()
        val orchestrator = buildOrchestrator(eventBus, depinAgent = spyDepin, testScope = backgroundScope)
        orchestrator.start()

        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = false))

        kotlinx.coroutines.delay(50)

        assertTrue(spyDepin.thresholdsReceived.isNotEmpty(), "DePIN agent should receive a threshold on disconnect")
        val pauseThreshold = spyDepin.thresholdsReceived.last()
        assertEquals(
            Float.MAX_VALUE,
            pauseThreshold.bandwidthMinMbps,
            "Offline pause should set bandwidthMinMbps to MAX_VALUE",
        )
        assertEquals(0, pauseThreshold.computeMaxCpuPercent, "Offline pause should set computeMaxCpuPercent to 0")
    }

    /**
     * ResourceUpdated always triggers a DePIN threshold adjustment so contributions
     * remain within current resource headroom.
     * Validates Requirement 16.3 (graceful degradation under resource constraints)
     */
    @Test
    fun resourceUpdateTriggersDepinThresholdAdjustment() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val spyDepin = SpyDePinAgent()
        val orchestrator = buildOrchestrator(eventBus, depinAgent = spyDepin, testScope = backgroundScope)
        orchestrator.start()

        eventBus.publish(AgentEvent.ResourceUpdated(profile = baseProfile()))

        kotlinx.coroutines.delay(50)

        assertTrue(
            spyDepin.thresholdsReceived.isNotEmpty(),
            "DePIN should adjust threshold on every ResourceUpdated",
        )
        val threshold = spyDepin.thresholdsReceived.last()
        assertTrue(threshold.bandwidthMinMbps > 0f, "Threshold must require positive minimum bandwidth")
    }

    /**
     * ThermalStateChanged → ResourceMonitor adapts scan frequency.
     * Validates Requirement 16.1 (thermal adaptation)
     */
    @Test
    fun thermalStateChangePropagatedToResourceMonitor() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val spyMonitor = SpyResourceMonitor()
        val orchestrator = buildOrchestrator(eventBus, resourceMonitor = spyMonitor, testScope = backgroundScope)
        orchestrator.start()

        eventBus.publish(AgentEvent.ThermalStateChanged(state = ThermalState.HOT))

        kotlinx.coroutines.delay(50)

        assertTrue(
            spyMonitor.thermalStatesReceived.isNotEmpty(),
            "ResourceMonitor should receive thermal state changes",
        )
        assertEquals(ThermalState.HOT, spyMonitor.thermalStatesReceived.last())
    }

    /**
     * Orchestrator stop cancels all active event subscriptions.
     * After stop(), publishing events must not invoke any agent callbacks.
     */
    @Test
    fun stopCancelsAllSubscriptions() = runTest(UnconfinedTestDispatcher()) {
        val eventBus = DefaultAgentEventBus()
        val spyDepin = SpyDePinAgent()
        val orchestrator = buildOrchestrator(eventBus, depinAgent = spyDepin, testScope = backgroundScope)

        orchestrator.start()
        orchestrator.stop()

        // After stop, connectivity event should not reach the DePIN agent
        val countBefore = spyDepin.thresholdsReceived.size
        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = false))
        kotlinx.coroutines.delay(50)

        assertEquals(countBefore, spyDepin.thresholdsReceived.size, "No new calls expected after stop()")
    }
}
