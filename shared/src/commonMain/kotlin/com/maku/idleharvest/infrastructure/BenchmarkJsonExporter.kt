package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.BenchmarkReport
import com.maku.idleharvest.domain.models.ModelSizeComparison
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Exports benchmark reports in a machine-readable JSON format compatible
 * with Arm Performix tooling.
 *
 * The exported JSON includes:
 * - Latency statistics (min, max, mean, p95) in milliseconds
 * - Memory usage and power draw measurements
 * - Device metadata for reproducibility (SoC, core config, RAM, OS)
 * - Model size comparison (FP32 vs quantized) when available
 * - Backend configuration used during the benchmark
 *
 * Validates: Requirements 11.1, 11.2, 11.4, 11.5, 11.6
 */
object BenchmarkJsonExporter {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    /**
     * Serializes a [BenchmarkReport] to Arm Performix-compatible JSON format.
     *
     * @param report The benchmark report to serialize.
     * @param modelSizeComparison Optional model size comparison data (FP32 vs quantized).
     * @return JSON string representation of the benchmark data.
     */
    fun exportToJson(
        report: BenchmarkReport,
        modelSizeComparison: ModelSizeComparison? = null,
    ): String {
        val exportData =
            BenchmarkExportData(
                schemaVersion = SCHEMA_VERSION,
                tooling = TOOLING_IDENTIFIER,
                benchmark =
                BenchmarkData(
                    modelId = report.modelId,
                    backend = report.backend.name,
                    iterationCount = report.iterationCount,
                    latency =
                    LatencyData(
                        minMs = report.latencyMinMs,
                        maxMs = report.latencyMaxMs,
                        meanMs = report.latencyMeanMs,
                        p95Ms = report.latencyP95Ms,
                    ),
                    memoryUsageMb = report.memoryUsageMb,
                    powerDrawMw = report.powerDrawMw,
                ),
                device =
                DeviceData(
                    socModel = report.deviceMetadata.socModel,
                    coreConfig = report.deviceMetadata.coreConfig,
                    ramGb = report.deviceMetadata.ramGb,
                    osVersion = report.deviceMetadata.osVersion,
                ),
                modelSize =
                modelSizeComparison?.let { comparison ->
                    ModelSizeData(
                        originalFp32Bytes = comparison.originalSizeFp32Bytes,
                        quantizedBytes = comparison.quantizedSizeBytes,
                        reductionPercent = comparison.reductionPercent,
                        quantizationLevel = comparison.quantizationLevel.name,
                    )
                },
            )

        return json.encodeToString(exportData)
    }

    /**
     * Deserializes an Arm Performix-compatible JSON string back into export data.
     *
     * @param jsonString The JSON string to parse.
     * @return Parsed benchmark export data.
     */
    fun importFromJson(jsonString: String): BenchmarkExportData = json.decodeFromString(jsonString)

    private const val SCHEMA_VERSION = "1.0"
    private const val TOOLING_IDENTIFIER = "arm-performix"
}

/**
 * Top-level export structure for Arm Performix-compatible JSON output.
 */
@Serializable
data class BenchmarkExportData(
    val schemaVersion: String,
    val tooling: String,
    val benchmark: BenchmarkData,
    val device: DeviceData,
    val modelSize: ModelSizeData? = null,
)

/**
 * Benchmark measurement data section.
 */
@Serializable
data class BenchmarkData(
    val modelId: String,
    val backend: String,
    val iterationCount: Int,
    val latency: LatencyData,
    val memoryUsageMb: Float,
    val powerDrawMw: Float?,
)

/**
 * Latency statistics subsection.
 */
@Serializable
data class LatencyData(
    val minMs: Long,
    val maxMs: Long,
    val meanMs: Double,
    val p95Ms: Long,
)

/**
 * Device metadata section for reproducibility.
 */
@Serializable
data class DeviceData(
    val socModel: String,
    val coreConfig: String,
    val ramGb: Float,
    val osVersion: String,
)

/**
 * Model size comparison section showing quantization gains.
 */
@Serializable
data class ModelSizeData(
    val originalFp32Bytes: Long,
    val quantizedBytes: Long,
    val reductionPercent: Float,
    val quantizationLevel: String,
)
