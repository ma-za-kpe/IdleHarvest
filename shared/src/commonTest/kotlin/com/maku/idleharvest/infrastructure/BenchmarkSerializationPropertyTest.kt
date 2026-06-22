package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.BenchmarkReport
import com.maku.idleharvest.generators.benchmarkReport
import io.kotest.property.Arb
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Property 24: Benchmark Report Serialization Round-Trip
 *
 * *For any* valid BenchmarkReport, serializing to JSON and deserializing back SHALL produce
 * an equivalent BenchmarkReport with all fields (including device metadata) preserved.
 *
 * **Validates: Requirements 11.5**
 */
class BenchmarkSerializationPropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun benchmarkReportRoundTrip() = runTest {
        forAll(Arb.benchmarkReport()) { report ->
            val jsonString = json.encodeToString(report)
            val decoded = json.decodeFromString<BenchmarkReport>(jsonString)
            decoded == report
        }
    }

    @Test
    fun benchmarkExporterRoundTrip() = runTest {
        forAll(Arb.benchmarkReport()) { report ->
            val exported = BenchmarkJsonExporter.exportToJson(report)
            val imported = BenchmarkJsonExporter.importFromJson(exported)
            // Verify key fields are preserved in the export format
            imported.benchmark.modelId == report.modelId &&
                imported.benchmark.backend == report.backend.name &&
                imported.benchmark.iterationCount == report.iterationCount &&
                imported.benchmark.latency.minMs == report.latencyMinMs &&
                imported.benchmark.latency.maxMs == report.latencyMaxMs &&
                imported.benchmark.latency.meanMs == report.latencyMeanMs &&
                imported.benchmark.latency.p95Ms == report.latencyP95Ms &&
                imported.benchmark.memoryUsageMb == report.memoryUsageMb &&
                imported.benchmark.powerDrawMw == report.powerDrawMw &&
                imported.device.socModel == report.deviceMetadata.socModel &&
                imported.device.coreConfig == report.deviceMetadata.coreConfig &&
                imported.device.ramGb == report.deviceMetadata.ramGb &&
                imported.device.osVersion == report.deviceMetadata.osVersion
        }
    }
}
