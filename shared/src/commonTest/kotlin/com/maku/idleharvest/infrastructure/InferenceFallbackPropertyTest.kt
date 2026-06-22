package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.InferenceInput
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 17: Inference Fallback Guarantee
 *
 * *For any* model load failure or inference execution error, the Inference_Engine SHALL produce
 * a valid output via rule-based heuristics. The system SHALL never fail to produce a decision
 * due to model issues.
 *
 * **Validates: Requirements 7.5**
 */
class InferenceFallbackPropertyTest {

    @Test
    fun inferenceNeverFailsEvenWithoutLoadedModel() = runTest {
        forAll(
            Arb.list(Arb.float(-1f..1f), 1..100),
            Arb.string(3..30),
        ) { tensorData, modelId ->
            val registry = DefaultModelRegistry(
                DefaultPrivacyVault(SimpleCryptoProvider()),
                SimpleCryptoProvider(),
            )
            val engine = DefaultInferenceEngine(registry)

            val input = InferenceInput(
                modelId = modelId,
                tensorData = tensorData,
                shape = listOf(1, tensorData.size),
            )

            val result = engine.runInference(input)

            // Must always succeed — never a failure
            result.isSuccess &&
                result.getOrNull()!!.let { output ->
                    // Predictions must be non-empty
                    output.predictions.isNotEmpty() &&
                        // All predictions must be in valid range [0, 1]
                        output.predictions.all { it in 0f..1f } &&
                        // Confidence must be low (< 1.0) indicating heuristic fallback
                        output.confidence != null && output.confidence!! < 1.0f &&
                        // Shape must be non-empty
                        output.shape.isNotEmpty()
                }
        }
    }

    @Test
    fun inferenceWithEmptyTensorStillProducesValidOutput() = runTest {
        forAll(
            Arb.string(3..30),
        ) { modelId ->
            val registry = DefaultModelRegistry(
                DefaultPrivacyVault(SimpleCryptoProvider()),
                SimpleCryptoProvider(),
            )
            val engine = DefaultInferenceEngine(registry)

            val input = InferenceInput(
                modelId = modelId,
                tensorData = emptyList(),
                shape = listOf(1, 0),
            )

            val result = engine.runInference(input)

            // Even with empty tensor data, must succeed
            result.isSuccess &&
                result.getOrNull()!!.let { output ->
                    output.predictions.isNotEmpty() &&
                        output.predictions.all { it in 0f..1f } &&
                        output.confidence != null && output.confidence!! < 1.0f
                }
        }
    }

    @Test
    fun inferenceWithMismatchedModelIdFallsBackToHeuristics() = runTest {
        forAll(
            Arb.list(Arb.float(-1f..1f), 1..50),
            Arb.int(1..10),
        ) { tensorData, batchSize ->
            val registry = DefaultModelRegistry(
                DefaultPrivacyVault(SimpleCryptoProvider()),
                SimpleCryptoProvider(),
            )
            val engine = DefaultInferenceEngine(registry)

            // Even with varied shapes, fallback must always produce valid output
            val input = InferenceInput(
                modelId = "model_that_does_not_exist",
                tensorData = tensorData,
                shape = listOf(batchSize, tensorData.size / batchSize.coerceAtLeast(1)),
            )

            val result = engine.runInference(input)

            result.isSuccess &&
                result.getOrNull()!!.let { output ->
                    output.predictions.isNotEmpty() &&
                        output.predictions.all { it in 0f..1f } &&
                        // Low confidence confirms heuristic path was used
                        output.confidence != null && output.confidence!! <= 0.5f
                }
        }
    }
}
