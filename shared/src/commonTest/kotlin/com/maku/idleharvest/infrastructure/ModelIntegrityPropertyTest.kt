package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.ModelFile
import com.maku.idleharvest.infrastructure.crypto.SimpleCryptoProvider
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 21: Model Integrity Verification
 *
 * *For any* model file and its declared SHA-256 checksum, the Model_Registry integrity check
 * SHALL accept files whose computed hash matches the declared checksum and reject files whose
 * hash does not match.
 *
 * **Validates: Requirements 9.3**
 */
class ModelIntegrityPropertyTest {
    private val cryptoProvider = SimpleCryptoProvider()

    private companion object {
        const val SHA256_HASH_KEY = "sha256_verification_key"
    }

    /**
     * Helper: Computes the SHA-256 hex string the same way DefaultModelRegistry does.
     */
    private fun computeSha256Hex(data: ByteArray): String {
        val hashKey = SHA256_HASH_KEY.encodeToByteArray().copyOf(32)
        val hashBytes = cryptoProvider.computeHash(hashKey, data)
        return hashBytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Property: Valid checksum passes integrity verification.
     *
     * For any model file content, computing the correct checksum and creating a ModelFile
     * with that checksum must pass verifyIntegrity.
     */
    @Test
    fun validChecksumPassesVerification() = runTest {
        forAll(
            Arb.byteArray(Arb.int(10..1000), Arb.byte()),
            Arb.string(3..20),
            Arb.string(1..10),
        ) { content, modelId, version ->
            val vault = DefaultPrivacyVault(cryptoProvider)
            val downloader = NoOpModelDownloader()
            val registry =
                DefaultModelRegistry(
                    vault = vault,
                    cryptoProvider = cryptoProvider,
                    modelDownloader = downloader,
                )

            // Compute the expected checksum using the same mechanism as the registry
            val expectedChecksum = computeSha256Hex(content)

            // Set up the file in the simulated file system
            val filePath = "models/$modelId/$version.pte"
            downloader.putFile(filePath, content)

            // Create a model file with a matching checksum
            val modelFile =
                ModelFile(
                    modelId = modelId,
                    version = version,
                    filePath = filePath,
                    sizeBytes = content.size.toLong(),
                    sha256Checksum = expectedChecksum,
                    downloadedAt = currentTimeMillis(),
                )

            // Verify integrity should pass
            registry.verifyIntegrity(modelFile)
        }
    }

    /**
     * Property: Invalid/tampered checksum fails integrity verification.
     *
     * For any model file content and a checksum that does NOT match the content,
     * verifyIntegrity must return false.
     */
    @Test
    fun invalidChecksumFailsVerification() = runTest {
        forAll(
            Arb.byteArray(Arb.int(10..1000), Arb.byte()),
            Arb.string(3..20),
            Arb.string(1..10),
            Arb.string(64..64), // Wrong checksum (64-char hex-like string)
        ) { content, modelId, version, wrongChecksum ->
            val vault = DefaultPrivacyVault(cryptoProvider)
            val downloader = NoOpModelDownloader()
            val registry =
                DefaultModelRegistry(
                    vault = vault,
                    cryptoProvider = cryptoProvider,
                    modelDownloader = downloader,
                )

            // Compute the real checksum so we can ensure ours differs
            val realChecksum = computeSha256Hex(content)

            // Only test when the wrong checksum is actually different from the real one
            if (wrongChecksum == realChecksum) {
                // Skip this case — extremely unlikely but handle gracefully
                true
            } else {
                // Set up the file in the simulated file system
                val filePath = "models/$modelId/$version.pte"
                downloader.putFile(filePath, content)

                // Create a model file with a mismatched checksum
                val modelFile =
                    ModelFile(
                        modelId = modelId,
                        version = version,
                        filePath = filePath,
                        sizeBytes = content.size.toLong(),
                        sha256Checksum = wrongChecksum,
                        downloadedAt = currentTimeMillis(),
                    )

                // Verify integrity should fail
                !registry.verifyIntegrity(modelFile)
            }
        }
    }

    /**
     * Property: Tampered content fails verification even with originally correct checksum.
     *
     * For any model file content, computing the correct checksum, then modifying the file
     * content (simulating file tampering) must cause verifyIntegrity to return false.
     */
    @Test
    fun tamperedContentFailsVerification() = runTest {
        forAll(
            Arb.byteArray(Arb.int(10..1000), Arb.byte()),
            Arb.string(3..20),
            Arb.string(1..10),
            Arb.int(0..999), // Index to tamper
        ) { content, modelId, version, tamperSeed ->
            val vault = DefaultPrivacyVault(cryptoProvider)
            val downloader = NoOpModelDownloader()
            val registry =
                DefaultModelRegistry(
                    vault = vault,
                    cryptoProvider = cryptoProvider,
                    modelDownloader = downloader,
                )

            // Compute the correct checksum from the original content
            val originalChecksum = computeSha256Hex(content)

            // Tamper with the content by flipping a byte
            val tamperedContent = content.copyOf()
            val tamperIndex = tamperSeed % tamperedContent.size
            tamperedContent[tamperIndex] = (tamperedContent[tamperIndex].toInt() xor 0xFF).toByte()

            // Set up the tampered file in the simulated file system
            val filePath = "models/$modelId/$version.pte"
            downloader.putFile(filePath, tamperedContent)

            // Create a model file with the checksum of the ORIGINAL (untampered) content
            val modelFile =
                ModelFile(
                    modelId = modelId,
                    version = version,
                    filePath = filePath,
                    sizeBytes = tamperedContent.size.toLong(),
                    sha256Checksum = originalChecksum,
                    downloadedAt = currentTimeMillis(),
                )

            // Verify integrity should fail because the file was tampered
            !registry.verifyIntegrity(modelFile)
        }
    }

    /**
     * Property: Missing file fails integrity verification.
     *
     * For any model file reference pointing to a non-existent file path,
     * verifyIntegrity must return false.
     */
    @Test
    fun missingFileFailsVerification() = runTest {
        forAll(
            Arb.string(3..20),
            Arb.string(1..10),
            Arb.string(64..64),
        ) { modelId, version, checksum ->
            val vault = DefaultPrivacyVault(cryptoProvider)
            val downloader = NoOpModelDownloader()
            val registry =
                DefaultModelRegistry(
                    vault = vault,
                    cryptoProvider = cryptoProvider,
                    modelDownloader = downloader,
                )

            // Do NOT put any file in the downloader → file doesn't exist
            val modelFile =
                ModelFile(
                    modelId = modelId,
                    version = version,
                    filePath = "models/$modelId/$version.pte",
                    sizeBytes = 1024L,
                    sha256Checksum = checksum,
                    downloadedAt = currentTimeMillis(),
                )

            // Verify integrity should fail because the file doesn't exist
            !registry.verifyIntegrity(modelFile)
        }
    }
}
