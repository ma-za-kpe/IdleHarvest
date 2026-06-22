package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.HardwareProfile
import com.maku.idleharvest.domain.models.InferenceBackend
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import com.maku.idleharvest.domain.models.QuantizationLevel
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property 22: Model Rollback Availability
 *
 * *For any* model update sequence, the Model_Registry SHALL retain the previous model version
 * as a rollback target. The previous version SHALL remain available until the new version is
 * explicitly marked stable.
 *
 * **Validates: Requirements 9.5**
 */
class ModelRollbackPropertyTest {

    private val cryptoProvider = SimpleCryptoProvider()

    /**
     * Computes the expected SHA-256 hex checksum for a given file size,
     * matching the logic in DefaultModelRegistry.computeSha256Hex and
     * NoOpModelDownloader's generated content: ByteArray(sizeBytes) { it.toByte() }.
     */
    private fun computeExpectedChecksum(sizeBytes: Long): String {
        val hashKey = "sha256_verification_key".encodeToByteArray().copyOf(32)
        val content = ByteArray(sizeBytes.toInt()) { it.toByte() }
        val hashBytes = cryptoProvider.computeHash(hashKey, content)
        return hashBytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Creates a DefaultModelRegistry with a fresh vault and NoOpModelDownloader.
     */
    private fun createRegistry(): DefaultModelRegistry {
        val vault = DefaultPrivacyVault(cryptoProvider)
        return DefaultModelRegistry(
            vault = vault,
            cryptoProvider = cryptoProvider,
            modelDownloader = NoOpModelDownloader(),
            clock = { 1_719_792_000_000L },
        )
    }

    /**
     * Creates model metadata with the correct checksum for the given size.
     */
    private fun createMetadata(
        id: String,
        version: String,
        sizeBytes: Long,
        purpose: ModelPurpose = ModelPurpose.USAGE_PREDICTION,
    ): ModelMetadata {
        return ModelMetadata(
            id = id,
            version = version,
            purpose = purpose,
            sizeBytes = sizeBytes,
            quantization = QuantizationLevel.INT8,
            targetHardware = HardwareProfile(
                architecture = "arm64-v8a",
                minCores = 4,
                minRamGb = 2f,
                supportedBackends = listOf(InferenceBackend.XNNPACK),
            ),
            sha256Checksum = computeExpectedChecksum(sizeBytes),
            isStable = false,
        )
    }

    /**
     * Property: After downloading a second version of a model, the previous version
     * is retained as a rollback target and accessible via getPreviousVersion().
     */
    @Test
    fun previousVersionRetainedAfterUpdate() = runTest {
        forAll(
            Arb.string(5..15),
            Arb.int(10..100),
            Arb.int(10..100),
        ) { modelId, size1, size2 ->
            val registry = createRegistry()

            // Register and download version 1
            val metadata1 = createMetadata(modelId, "1.0.0", size1.toLong())
            registry.registerModel(metadata1)
            val result1 = registry.downloadModel(modelId)
            val file1 = result1.getOrNull() ?: return@forAll false

            // Update metadata to version 2 (different size produces different checksum)
            val metadata2 = createMetadata(modelId, "2.0.0", size2.toLong())
            registry.registerModel(metadata2)
            val result2 = registry.downloadModel(modelId)
            result2.getOrNull() ?: return@forAll false

            // Previous version should be retained
            val previousVersion = registry.getPreviousVersion(modelId)
            previousVersion != null &&
                previousVersion.version == "1.0.0" &&
                previousVersion.modelId == modelId
        }
    }

    /**
     * Property: Rolling back restores the previous model version as the active downloaded model.
     */
    @Test
    fun rollbackRestoresPreviousVersion() = runTest {
        forAll(
            Arb.string(5..15),
            Arb.int(10..100),
            Arb.int(10..100),
        ) { modelId, size1, size2 ->
            val registry = createRegistry()

            // Register and download version 1
            val metadata1 = createMetadata(modelId, "1.0.0", size1.toLong())
            registry.registerModel(metadata1)
            registry.downloadModel(modelId).getOrNull() ?: return@forAll false

            // Update and download version 2
            val metadata2 = createMetadata(modelId, "2.0.0", size2.toLong())
            registry.registerModel(metadata2)
            registry.downloadModel(modelId).getOrNull() ?: return@forAll false

            // Rollback should restore version 1
            val rollbackResult = registry.rollback(modelId)
            val rolledBack = rollbackResult.getOrNull() ?: return@forAll false

            rolledBack.version == "1.0.0" &&
                registry.getDownloadedModel(modelId)?.version == "1.0.0"
        }
    }

    /**
     * Property: Confirming a model as stable removes the rollback copy.
     * After confirmStable(), getPreviousVersion() returns null.
     */
    @Test
    fun confirmStableRemovesRollbackCopy() = runTest {
        forAll(
            Arb.string(5..15),
            Arb.int(10..100),
            Arb.int(10..100),
        ) { modelId, size1, size2 ->
            val registry = createRegistry()

            // Register and download version 1
            val metadata1 = createMetadata(modelId, "1.0.0", size1.toLong())
            registry.registerModel(metadata1)
            registry.downloadModel(modelId).getOrNull() ?: return@forAll false

            // Update and download version 2
            val metadata2 = createMetadata(modelId, "2.0.0", size2.toLong())
            registry.registerModel(metadata2)
            registry.downloadModel(modelId).getOrNull() ?: return@forAll false

            // Before confirmStable, previous version exists
            val beforeConfirm = registry.getPreviousVersion(modelId) != null

            // Confirm stable removes rollback copy
            registry.confirmStable(modelId)

            val afterConfirm = registry.getPreviousVersion(modelId) == null

            beforeConfirm && afterConfirm
        }
    }

    /**
     * Property: A model with only one version has no rollback target.
     * getPreviousVersion() returns null when only one version has been downloaded.
     */
    @Test
    fun singleVersionHasNoRollbackTarget() = runTest {
        forAll(
            Arb.string(5..15),
            Arb.int(10..100),
        ) { modelId, size ->
            val registry = createRegistry()

            // Register and download only one version
            val metadata = createMetadata(modelId, "1.0.0", size.toLong())
            registry.registerModel(metadata)
            registry.downloadModel(modelId).getOrNull() ?: return@forAll false

            // No previous version should exist
            registry.getPreviousVersion(modelId) == null
        }
    }
}
