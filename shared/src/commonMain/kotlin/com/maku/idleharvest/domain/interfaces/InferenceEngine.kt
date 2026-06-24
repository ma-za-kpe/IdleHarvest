package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.BenchmarkConfig
import com.maku.idleharvest.domain.models.BenchmarkReport
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.InferenceInput
import com.maku.idleharvest.domain.models.InferenceOutput
import com.maku.idleharvest.domain.models.ModelInfo
import com.maku.idleharvest.domain.models.ThermalState
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device AI inference via ExecuTorch with Arm-optimized backends.
 * Supports model loading, inference execution, hot-swapping, and benchmarking.
 *
 * Validates: Requirements 7.1, 7.4, 7.5, 7.6
 */
interface InferenceEngine {
    /** Currently loaded model metadata, or null if no model is loaded. */
    val loadedModel: StateFlow<ModelInfo?>

    /** Most recent benchmark results, or null if no benchmark has been run. */
    val benchmarkResults: StateFlow<BenchmarkReport?>

    /** Load a model by its registry identifier. */
    suspend fun loadModel(modelId: String): Result<ModelInfo>

    /** Run inference on the loaded model with the given input tensors. */
    suspend fun runInference(input: InferenceInput): Result<InferenceOutput>

    /** Hot-swap to a different model without restarting background services. */
    suspend fun hotSwapModel(newModelId: String): Result<ModelInfo>

    /** Run a standardized benchmark suite and produce a report. */
    suspend fun runBenchmark(config: BenchmarkConfig): BenchmarkReport

    /** Select the optimal inference backend based on current thermal state. */
    fun selectBackend(thermalState: ThermalState): InferenceBackend
}
