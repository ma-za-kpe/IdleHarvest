@file:Suppress("MagicNumber", "MaxLineLength", "ReturnCount")

package com.maku.idleharvest

import android.content.Context
import android.util.Log
import com.maku.idleharvest.domain.SecureKeystore
import com.maku.idleharvest.domain.models.AirtimeBalance
import com.maku.idleharvest.domain.models.ApprovalSource
import com.maku.idleharvest.domain.models.HardwareProfile
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import com.maku.idleharvest.domain.models.MonetizationAction
import com.maku.idleharvest.domain.models.MonetizationRecommendation
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
import com.maku.idleharvest.infrastructure.currentTimeMillis
import com.maku.idleharvest.service.AgentNotificationCenter
import com.maku.idleharvest.service.AndroidUssdAirtimeProbe
import com.maku.idleharvest.ui.dashboard.AirtimeProbeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * Manual dependency injection container for all agents and infrastructure services.
 *
 * Owns the shared CoroutineScope for all background agent work, cancelled on tearDown.
 */
class AgentContainer(
    context: Context,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appContext = context.applicationContext

    private val cryptoProvider = createPlatformCryptoProvider()
    private val vault = DefaultPrivacyVault(cryptoProvider)
    private val eventBus = DefaultAgentEventBus()
    private val complianceEngine = DefaultComplianceEngine(vault)
    val policyManager = DefaultPolicyManager(vault, eventBus)

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
    private val notificationCenter = AgentNotificationCenter(appContext, eventBus, appScope)
    private val ussdProbe = AndroidUssdAirtimeProbe(appContext)
    private val _phoneAirtimeBalance = MutableStateFlow<AirtimeBalance?>(null)
    val phoneAirtimeBalance: StateFlow<AirtimeBalance?> = _phoneAirtimeBalance.asStateFlow()
    private val _airtimeProbeState = MutableStateFlow<AirtimeProbeState>(AirtimeProbeState.Idle)
    val airtimeProbeState: StateFlow<AirtimeProbeState> = _airtimeProbeState.asStateFlow()
    private var lastPhoneCountryIso: String? = null

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

    init {
        notificationCenter.start()
        appScope.launch {
            runCatching {
                inferenceEngine.loadModel(DEFAULT_MODEL_ID).getOrThrow()
            }.onSuccess { modelInfo ->
                Log.i(TAG, "Preloaded model ${modelInfo.modelId} v${modelInfo.version} on ${modelInfo.backend}")
            }.onFailure { error ->
                Log.w(TAG, "Model preload failed: ${error.message}", error)
            }
        }
    }

    fun tearDown() {
        notificationCenter.stop()
        orchestrator.stop()
        appScope.cancel()
    }

    fun requestAirtimeBalance(ussdCode: String) {
        appScope.launch {
            _airtimeProbeState.value = AirtimeProbeState.Loading
            ussdProbe.requestBalance(ussdCode).fold(
                onSuccess = { result ->
                    lastPhoneCountryIso = result.countryIso
                    val parsedBalance = parsePhoneBalance(result.rawResponse, result.carrierName)
                    _phoneAirtimeBalance.value = parsedBalance
                    _airtimeProbeState.value = AirtimeProbeState.Success(result.rawResponse, parsedBalance)
                },
                onFailure = { error ->
                    _airtimeProbeState.value = AirtimeProbeState.Error(error.message ?: "USSD balance request failed")
                },
            )
        }
    }

    fun triggerManualAirtimeSale() {
        appScope.launch {
            val balance = _phoneAirtimeBalance.value
            if (balance == null) {
                _airtimeProbeState.value = AirtimeProbeState.Error("No phone airtime balance is available to sell.")
                return@launch
            }

            val action =
                MonetizationAction(
                    bundleId = "phone_balance_${balance.carrier}",
                    recommendation =
                    MonetizationRecommendation.Sell(
                        amount = balance.amountUnits,
                        platform = com.maku.idleharvest.domain.models.VtuPlatform.PRESTMIT,
                        confidence = 1f,
                    ),
                    approvedBy = ApprovalSource.USER_MANUAL,
                    timestamp = currentTimeMillis(),
                    currency = balance.currency,
                    carrier = balance.carrier,
                    country = lastPhoneCountryIso,
                )
            airtimeAgent.executeAction(action)
            _airtimeProbeState.value =
                AirtimeProbeState.Success(
                    rawResponse = "Manual sale triggered for ${formatUnits(balance.amountUnits)} ${balance.currency}.",
                    balance = balance,
                )
        }
    }

    private fun parsePhoneBalance(
        rawResponse: String,
        carrierName: String?,
    ): AirtimeBalance? {
        val match = balancePattern.find(rawResponse) ?: return null
        val currencyToken = match.groups[1]?.value ?: match.groups[4]?.value ?: return null
        val amountText = match.groups[2]?.value ?: match.groups[3]?.value ?: return null
        val normalizedCurrency = normalizeCurrency(currencyToken)
        val amountUnits = parseMoneyToUnits(amountText) ?: return null

        return AirtimeBalance(
            carrier = carrierName ?: "phone",
            amountUnits = amountUnits,
            currency = normalizedCurrency,
            expiryTimestamp = null,
        )
    }

    private fun normalizeCurrency(token: String): String = when (token.uppercase()) {
        "GHS", "GH\u20B5", "\u20B5", "\u00A2" -> "GHS"
        else -> token.uppercase()
    }

    private fun parseMoneyToUnits(value: String): Long? {
        val normalized = value.replace(",", ".")
        val decimal = normalized.toDoubleOrNull() ?: return null
        return round(decimal * PERCENT_SCALE).toLong()
    }

    private fun formatUnits(value: Long): String {
        val whole = value / MONEY_DECIMAL_BASE
        val fraction = (value % MONEY_DECIMAL_BASE).toString().padStart(2, '0')
        return "$whole.$fraction"
    }

    private companion object {
        private const val TAG = "AgentContainer"
        private const val DEFAULT_MODEL_ID = "idleharvest_model"
        private const val PERCENT_SCALE = 100.0
        private const val MONEY_DECIMAL_BASE = 100
        private val balancePattern =
            Regex(
                pattern =
                """(?i)(GHS|GH\u20B5|\u20B5|\u00A2|[A-Z]{3})\s*([0-9]+(?:[.,][0-9]+)?)|([0-9]+(?:[.,][0-9]+)?)\s*(GHS|GH\u20B5|\u20B5|\u00A2|[A-Z]{3})""",
            )
    }
}
