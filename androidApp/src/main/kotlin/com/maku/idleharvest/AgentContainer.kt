package com.maku.idleharvest

import android.content.Context
import com.maku.idleharvest.domain.SecureKeystore
import com.maku.idleharvest.domain.models.HardwareProfile
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import com.maku.idleharvest.domain.models.QuantizationLevel
import com.maku.idleharvest.infrastructure.AgentOrchestrator
import com.maku.idleharvest.infrastructure.AndroidModelDownloader
import com.maku.idleharvest.infrastructure.BleAdapter
import com.maku.idleharvest.infrastructure.DefaultAgentEventBus
import com.maku.idleharvest.infrastructure.DefaultAirtimeAgent
import com.maku.idleharvest.infrastructure.DefaultComplianceEngine
import com.maku.idleharvest.infrastructure.DefaultDePinAgent
import com.maku.idleharvest.infrastructure.DefaultEarningEngine
import com.maku.idleharvest.infrastructure.DefaultInferenceEngine
import com.maku.idleharvest.infrastructure.DefaultMeshCoordinator
import com.maku.idleharvest.infrastructure.DefaultModelRegistry
import com.maku.idleharvest.infrastructure.DefaultPolicyManager
import com.maku.idleharvest.infrastructure.DefaultPrivacyVault
import com.maku.idleharvest.infrastructure.DefaultResourceMonitor
import com.maku.idleharvest.infrastructure.PlatformResourceScanner
import com.maku.idleharvest.infrastructure.crypto.createPlatformCryptoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Manual dependency injection container for all agents and infrastructure services.
 *
 * Wires the full agent graph: Privacy_Vault → Policy_Manager + Compliance_Engine
 * → individual agents → AgentOrchestrator. Owns the shared CoroutineScope for all
 * background agent work, cancelled on [tearDown].
 *
 * Lifetime: tied to the Application process (created once in MainActivity, survives
 * configuration changes via the Activity's lazy property).
 */
class AgentContainer(
    context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Real AES-256-GCM + HMAC-SHA256 backed by the platform crypto library
    // (javax.crypto on Android). Replaces the insecure XOR test provider.
    private val cryptoProvider = createPlatformCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val eventBus = DefaultAgentEventBus()
    private val complianceEngine = DefaultComplianceEngine(vault)
    val policyManager = DefaultPolicyManager(vault, eventBus) // Exposed for onboarding policy application (was private)

    private val scanner = PlatformResourceScanner(context)
    val resourceMonitor = DefaultResourceMonitor(scanner, appScope, eventBus)

    private val bleAdapter = BleAdapter(context)
    val meshCoordinator = DefaultMeshCoordinator(bleAdapter, cryptoProvider, eventBus, appScope)

    private val modelDownloader = AndroidModelDownloader(context)
    private val initialModels =
        listOf(
            ModelMetadata(
                id = "idleharvest_model",
                version = "1.0.0",
                purpose = ModelPurpose.RESOURCE_OPTIMIZATION,
                sizeBytes = 8_000_000L,
                quantization = QuantizationLevel.INT8,
                targetHardware =
                HardwareProfile(
                    architecture = "arm64-v8a",
                    minCores = 4,
                    minRamGb = 4f,
                    supportedBackends =
                    listOf(
                        InferenceBackend.KLEIDIAI,
                        InferenceBackend.XNNPACK,
                        InferenceBackend.CPU_BASELINE,
                    ),
                ),
                sha256Checksum = "",
                isStable = true,
            ),
        )
    private val modelRegistry =
        DefaultModelRegistry(
            vault,
            cryptoProvider,
            modelDownloader = modelDownloader,
            initialModels = initialModels,
        )
    private val inferenceEngine = DefaultInferenceEngine(modelRegistry)

    val airtimeAgent = DefaultAirtimeAgent(policyManager, complianceEngine, vault, eventBus, inferenceEngine)
    val depinAgent = DefaultDePinAgent(vault, eventBus, policyManager)
    private val secureKeystore = SecureKeystore()
    val earningEngine = DefaultEarningEngine(policyManager, vault, eventBus, secureKeystore)

    val orchestrator =
        AgentOrchestrator(
            resourceMonitor = resourceMonitor,
            airtimeAgent = airtimeAgent,
            depinAgent = depinAgent,
            meshCoordinator = meshCoordinator,
            earningEngine = earningEngine,
            eventBus = eventBus,
            scope = appScope,
        )

    fun tearDown() {
        orchestrator.stop()
        appScope.cancel()
    }
}
