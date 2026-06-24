package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Available inference backend implementations optimized for Arm hardware.
 */
@Serializable
enum class InferenceBackend { KLEIDIAI, XNNPACK, SME2, CPU_BASELINE }

/**
 * Metadata about a loaded model in the inference engine.
 */
@Serializable
data class ModelInfo(
    val modelId: String,
    val version: String,
    val purpose: ModelPurpose,
    val backend: InferenceBackend,
    val sizeBytes: Long,
    val loadedAt: Long,
)

/**
 * Input data for an inference pass.
 * Tensor data is represented as a flat float array with shape metadata.
 */
@Serializable
data class InferenceInput(
    val modelId: String,
    val tensorData: List<Float>,
    val shape: List<Int>,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Output from an inference pass.
 */
@Serializable
data class InferenceOutput(
    val modelId: String,
    val predictions: List<Float>,
    val shape: List<Int>,
    val latencyMs: Long,
    val confidence: Float?,
)

/**
 * Configuration for running a standardized benchmark suite.
 */
@Serializable
data class BenchmarkConfig(
    val modelId: String,
    val iterationCount: Int = 100,
    val warmupIterations: Int = 10,
    val backend: InferenceBackend? = null,
)

/**
 * Benchmark report with latency statistics, memory usage, and device metadata.
 */
@Serializable
data class BenchmarkReport(
    val modelId: String,
    val backend: InferenceBackend,
    val iterationCount: Int,
    val latencyMinMs: Long,
    val latencyMaxMs: Long,
    val latencyMeanMs: Double,
    val latencyP95Ms: Long,
    val memoryUsageMb: Float,
    val powerDrawMw: Float?,
    val deviceMetadata: DeviceMetadata,
)

/**
 * Device hardware metadata for benchmark reproducibility.
 *
 * Validates: Requirements 11.6
 */
@Serializable
data class DeviceMetadata(
    val socModel: String,
    val coreConfig: String,
    val ramGb: Float,
    val osVersion: String,
)

/**
 * Comparison of model size between original FP32 and quantized version.
 * Used to report model size reduction achieved through quantization.
 *
 * Validates: Requirements 11.4
 */
@Serializable
data class ModelSizeComparison(
    val originalSizeFp32Bytes: Long,
    val quantizedSizeBytes: Long,
    val reductionPercent: Float,
    val quantizationLevel: QuantizationLevel,
) {
    companion object {
        /**
         * Computes a model size comparison given the quantized model size and its quantization level.
         * Estimates the FP32 size based on quantization ratios:
         * - FP32: no reduction (1:1)
         * - FP16: ~2x reduction
         * - INT8: ~4x reduction
         * - INT4: ~8x reduction
         */
        fun compute(
            quantizedSizeBytes: Long,
            quantizationLevel: QuantizationLevel,
        ): ModelSizeComparison {
            val estimatedFp32Size =
                when (quantizationLevel) {
                    QuantizationLevel.FP32 -> quantizedSizeBytes
                    QuantizationLevel.FP16 -> quantizedSizeBytes * 2
                    QuantizationLevel.INT8 -> quantizedSizeBytes * 4
                    QuantizationLevel.INT4 -> quantizedSizeBytes * 8
                }

            val reductionPercent =
                if (estimatedFp32Size > 0) {
                    ((estimatedFp32Size - quantizedSizeBytes).toFloat() / estimatedFp32Size.toFloat()) * 100f
                } else {
                    0f
                }

            return ModelSizeComparison(
                originalSizeFp32Bytes = estimatedFp32Size,
                quantizedSizeBytes = quantizedSizeBytes,
                reductionPercent = reductionPercent,
                quantizationLevel = quantizationLevel,
            )
        }
    }
}
