package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.InferenceEngine
import com.maku.idleharvest.domain.models.BenchmarkConfig
import com.maku.idleharvest.domain.models.BenchmarkReport
import com.maku.idleharvest.domain.models.DeviceMetadata
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.InferenceInput
import com.maku.idleharvest.domain.models.InferenceOutput
import com.maku.idleharvest.domain.models.ModelInfo
import com.maku.idleharvest.domain.models.ThermalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A no-op [InferenceEngine] for tests that don't require inference behavior.
 * Always returns heuristic fallback results without loading any model.
 */
class NoOpInferenceEngine : InferenceEngine {
    private val _loadedModel = MutableStateFlow<ModelInfo?>(null)
    override val loadedModel: StateFlow<ModelInfo?> = _loadedModel.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<BenchmarkReport?>(null)
    override val benchmarkResults: StateFlow<BenchmarkReport?> = _benchmarkResults.asStateFlow()

    override suspend fun loadModel(modelId: String): Result<ModelInfo> = Result.failure(UnsupportedOperationException("NoOp inference engine"))

    override suspend fun runInference(input: InferenceInput): Result<InferenceOutput> = Result.success(
        InferenceOutput(
            modelId = input.modelId,
            predictions = listOf(0.5f),
            shape = listOf(1),
            latencyMs = 0L,
            confidence = 0.5f,
        ),
    )

    override suspend fun hotSwapModel(newModelId: String): Result<ModelInfo> = Result.failure(UnsupportedOperationException("NoOp inference engine"))

    override suspend fun runBenchmark(config: BenchmarkConfig): BenchmarkReport = BenchmarkReport(
        modelId = "noop",
        backend = InferenceBackend.CPU_BASELINE,
        iterationCount = 0,
        latencyMinMs = 0L,
        latencyMaxMs = 0L,
        latencyMeanMs = 0.0,
        latencyP95Ms = 0L,
        memoryUsageMb = 0f,
        powerDrawMw = null,
        deviceMetadata =
        DeviceMetadata(
            socModel = "test",
            coreConfig = "test",
            ramGb = 4f,
            osVersion = "test",
        ),
    )

    override fun selectBackend(thermalState: ThermalState): InferenceBackend = InferenceBackend.CPU_BASELINE
}
