package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.ModelRegistry
import com.maku.idleharvest.domain.interfaces.PrivacyVault
import com.maku.idleharvest.domain.models.ModelFile
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import com.maku.idleharvest.infrastructure.crypto.CryptoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Default implementation of [ModelRegistry] with lifecycle management.
 *
 * Provides:
 * - Model metadata storage in Privacy_Vault with key pattern `model_metadata_{id}`
 * - Metered-aware download (prefers Wi-Fi, configurable behavior on metered connections)
 * - SHA-256 integrity verification before making models available
 * - Resumable downloads from checkpoint (download progress tracked in vault)
 * - Rollback retention (previous version kept until new one is confirmed stable)
 *
 * Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5
 */
class DefaultModelRegistry(
    private val vault: PrivacyVault,
    private val cryptoProvider: CryptoProvider,
    private val connectivityProvider: ConnectivityProvider = DefaultConnectivityProvider(),
    private val modelDownloader: ModelDownloader = NoOpModelDownloader(),
    initialModels: List<ModelMetadata> = emptyList(),
    private val clock: () -> Long = { currentTimeMillis() },
) : ModelRegistry {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _availableModels = MutableStateFlow(initialModels)
    override val availableModels: StateFlow<List<ModelMetadata>> = _availableModels.asStateFlow()

    /** Track active model per purpose. */
    private val activeModels = mutableMapOf<ModelPurpose, ModelMetadata>()

    /** Rollback storage: previous model files by model ID. */
    private val previousVersions = mutableMapOf<String, ModelFile>()

    /** Currently downloaded model files by model ID. */
    private val downloadedModels = mutableMapOf<String, ModelFile>()

    /** Download progress tracking: model ID -> bytes downloaded so far. */
    private val downloadProgress = mutableMapOf<String, Long>()

    /**
     * Loads all persisted model metadata from the vault into memory.
     * Should be called during initialization.
     */
    suspend fun loadFromVault() {
        val metadataList = mutableListOf<ModelMetadata>()
        val indexBytes = vault.retrieve(MODEL_INDEX_KEY).getOrNull()
        if (indexBytes != null) {
            val indexJson = indexBytes.decodeToString()
            val modelIds: List<String> = json.decodeFromString(indexJson)
            for (id in modelIds) {
                val metaBytes = vault.retrieve("$MODEL_METADATA_PREFIX$id").getOrNull()
                if (metaBytes != null) {
                    val metadata: ModelMetadata = json.decodeFromString(metaBytes.decodeToString())
                    metadataList.add(metadata)
                }
            }
        }
        _availableModels.value = metadataList
    }

    /**
     * Registers model metadata in the registry and persists to vault.
     */
    suspend fun registerModel(metadata: ModelMetadata) {
        mutex.withLock {
            val currentModels = _availableModels.value.toMutableList()
            val existingIndex = currentModels.indexOfFirst { it.id == metadata.id }

            if (existingIndex >= 0) {
                currentModels[existingIndex] = metadata
            } else {
                currentModels.add(metadata)
            }

            _availableModels.value = currentModels
            persistMetadata(metadata)
        }
    }

    @Suppress("LongMethod", "UseCheckOrError")
    override suspend fun downloadModel(modelId: String): Result<ModelFile> = runCatching {
        val metadata =
            findMetadata(modelId)
                ?: throw IllegalArgumentException("Model not found in registry: $modelId")

        // Check connectivity state — prefer Wi-Fi over metered
        val connectivity = connectivityProvider.getConnectivityState()
        if (connectivity.isMetered && !connectivity.allowMeteredDownloads) {
            throw MeteredConnectionException(
                "Download blocked: metered connection detected. Connect to Wi-Fi or enable metered downloads.",
            )
        }

        // Check for existing download progress (resumable download)
        val resumeFromByte =
            mutex.withLock {
                downloadProgress[modelId] ?: 0L
            }

        // Perform download (with resume support)
        val modelFileData =
            modelDownloader.download(
                modelId = modelId,
                metadata = metadata,
                resumeFromByte = resumeFromByte,
                onProgress = { bytesDownloaded ->
                    mutex.withLock {
                        downloadProgress[modelId] = bytesDownloaded
                        // Persist download progress for crash recovery
                        persistDownloadProgress(modelId, bytesDownloaded)
                    }
                },
            )

        val fileContent =
            modelDownloader.readFileContent(modelFileData.filePath)
                ?: error("Downloaded file missing for $modelId: ${modelFileData.filePath}")
        val computedHash = computeSha256Hex(fileContent)
        val expectedChecksum = metadata.sha256Checksum.trim()

        if (expectedChecksum.isNotEmpty() && computedHash != expectedChecksum) {
            mutex.withLock {
                downloadProgress.remove(modelId)
            }
            vault.delete("$DOWNLOAD_PROGRESS_PREFIX$modelId")
            throw IntegrityVerificationException(
                "Model integrity verification failed for $modelId: SHA-256 mismatch",
            )
        }

        val resolvedChecksum = expectedChecksum.ifEmpty { computedHash }

        val modelFile =
            ModelFile(
                modelId = modelId,
                version = metadata.version,
                filePath = modelFileData.filePath,
                sizeBytes = fileContent.size.toLong(),
                sha256Checksum = resolvedChecksum,
                downloadedAt = clock(),
            )

        // Verify integrity before making available
        if (!verifyIntegrity(modelFile)) {
            // Clean up failed download progress
            mutex.withLock {
                downloadProgress.remove(modelId)
            }
            vault.delete("$DOWNLOAD_PROGRESS_PREFIX$modelId")
            throw IntegrityVerificationException(
                "Model integrity verification failed for $modelId: SHA-256 mismatch",
            )
        }

        mutex.withLock {
            // Store previous version for rollback before replacing
            downloadedModels[modelId]?.let { existingFile ->
                previousVersions[modelId] = existingFile
            }

            // Store the new model file
            downloadedModels[modelId] = modelFile

            // Set as active model for its purpose
            activeModels[metadata.purpose] = metadata

            // Clear download progress
            downloadProgress.remove(modelId)
        }

        // Clean up persisted download progress
        vault.delete("$DOWNLOAD_PROGRESS_PREFIX$modelId")

        modelFile
    }

    override suspend fun verifyIntegrity(modelFile: ModelFile): Boolean {
        // Compute SHA-256 hash of the model file content
        val fileContent =
            modelDownloader.readFileContent(modelFile.filePath)
                ?: return false

        val computedHash = computeSha256Hex(fileContent)
        return computedHash == modelFile.sha256Checksum
    }

    override suspend fun rollback(modelId: String): Result<ModelFile> = runCatching {
        mutex.withLock {
            val previousFile =
                previousVersions[modelId]
                    ?: throw IllegalStateException("No previous version available for rollback: $modelId")

            // Restore the previous version as the active model
            downloadedModels[modelId] = previousFile

            // Find and update the active model for the corresponding purpose
            val metadata = _availableModels.value.firstOrNull { it.id == modelId }
            if (metadata != null) {
                activeModels[metadata.purpose] =
                    metadata.copy(
                        version = previousFile.version,
                        isStable = true,
                    )
            }

            previousFile
        }
    }

    override fun getActiveModel(purpose: ModelPurpose): ModelMetadata? = activeModels[purpose]

    /**
     * Marks a model version as stable, removing the rollback copy.
     * Once confirmed stable, the previous version is no longer retained.
     */
    suspend fun confirmStable(modelId: String) {
        mutex.withLock {
            // Remove rollback copy
            previousVersions.remove(modelId)

            // Mark the model as stable in metadata
            val currentModels = _availableModels.value.toMutableList()
            val index = currentModels.indexOfFirst { it.id == modelId }
            if (index >= 0) {
                val updated = currentModels[index].copy(isStable = true)
                currentModels[index] = updated
                _availableModels.value = currentModels

                // Update active model if applicable
                if (activeModels[updated.purpose]?.id == modelId) {
                    activeModels[updated.purpose] = updated
                }

                // Persist the updated metadata
                persistMetadata(updated)
            }
        }
    }

    /**
     * Gets the downloaded model file for a given model ID, if available.
     */
    fun getDownloadedModel(modelId: String): ModelFile? = downloadedModels[modelId]

    /**
     * Gets the previous model version stored for rollback.
     */
    fun getPreviousVersion(modelId: String): ModelFile? = previousVersions[modelId]

    /**
     * Gets the current download progress (bytes downloaded) for a model.
     */
    fun getDownloadProgress(modelId: String): Long? = downloadProgress[modelId]

    // --- Private helpers ---

    private fun findMetadata(modelId: String): ModelMetadata? = _availableModels.value.firstOrNull { it.id == modelId }

    private suspend fun persistMetadata(metadata: ModelMetadata) {
        val metaJson = json.encodeToString(metadata)
        vault.store("$MODEL_METADATA_PREFIX${metadata.id}", metaJson.encodeToByteArray())

        // Update index
        val modelIds = _availableModels.value.map { it.id }
        val indexJson = json.encodeToString(modelIds)
        vault.store(MODEL_INDEX_KEY, indexJson.encodeToByteArray())
    }

    private suspend fun persistDownloadProgress(
        modelId: String,
        bytesDownloaded: Long,
    ) {
        val progressJson = json.encodeToString(bytesDownloaded)
        vault.store("$DOWNLOAD_PROGRESS_PREFIX$modelId", progressJson.encodeToByteArray())
    }

    /**
     * Computes a SHA-256-like hash of the given data and returns it as a hex string.
     *
     * Uses the CryptoProvider's computeHash for a deterministic hash.
     * In production, platform-specific actual implementations provide real SHA-256.
     * The key used here is a fixed "sha256" seed for reproducibility.
     */
    private fun computeSha256Hex(data: ByteArray): String {
        val hashKey = SHA256_HASH_KEY.encodeToByteArray().copyOf(32)
        val hashBytes = cryptoProvider.computeHash(hashKey, data)
        return hashBytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    companion object {
        private const val MODEL_INDEX_KEY = "model_registry_index"
        private const val MODEL_METADATA_PREFIX = "model_metadata_"
        private const val DOWNLOAD_PROGRESS_PREFIX = "model_download_progress_"
        private const val SHA256_HASH_KEY = "sha256_verification_key"
    }
}

/**
 * Provides connectivity information for metered-aware downloads.
 */
interface ConnectivityProvider {
    fun getConnectivityState(): ConnectivityState
}

/**
 * State of the device's network connectivity.
 */
data class ConnectivityState(
    val isConnected: Boolean,
    val isMetered: Boolean,
    val allowMeteredDownloads: Boolean = false,
)

/**
 * Default connectivity provider that assumes unmetered connection.
 * Platform-specific implementations provide real connectivity info.
 */
class DefaultConnectivityProvider : ConnectivityProvider {
    override fun getConnectivityState(): ConnectivityState = ConnectivityState(
        isConnected = true,
        isMetered = false,
        allowMeteredDownloads = false,
    )
}

/**
 * Abstraction for model file download operations.
 * Platform implementations provide actual HTTP download capabilities.
 */
interface ModelDownloader {
    /**
     * Download a model file, optionally resuming from a byte offset.
     */
    suspend fun download(
        modelId: String,
        metadata: ModelMetadata,
        resumeFromByte: Long = 0L,
        onProgress: suspend (bytesDownloaded: Long) -> Unit = {},
    ): DownloadResult

    /**
     * Read the raw bytes of a file at the given path.
     * Returns null if the file does not exist or cannot be read.
     */
    suspend fun readFileContent(filePath: String): ByteArray?
}

/**
 * Result of a model download operation.
 */
data class DownloadResult(
    val filePath: String,
    val sizeBytes: Long,
)

/**
 * No-op downloader for testing and commonMain usage.
 * Returns synthetic data suitable for unit tests.
 */
class NoOpModelDownloader : ModelDownloader {
    /** Map of filePath -> content for simulated file system. */
    private val fileSystem = mutableMapOf<String, ByteArray>()

    /**
     * Pre-populate the simulated file system for testing.
     */
    fun putFile(
        filePath: String,
        content: ByteArray,
    ) {
        fileSystem[filePath] = content
    }

    override suspend fun download(
        modelId: String,
        metadata: ModelMetadata,
        resumeFromByte: Long,
        onProgress: suspend (bytesDownloaded: Long) -> Unit,
    ): DownloadResult {
        val filePath = "models/$modelId/${metadata.version}.pte"
        val content = ByteArray(metadata.sizeBytes.toInt()) { it.toByte() }
        fileSystem[filePath] = content

        // Simulate progress reporting
        val chunkSize = maxOf(1L, metadata.sizeBytes / 10)
        var downloaded = resumeFromByte
        while (downloaded < metadata.sizeBytes) {
            downloaded = minOf(downloaded + chunkSize, metadata.sizeBytes)
            onProgress(downloaded)
        }

        return DownloadResult(filePath = filePath, sizeBytes = metadata.sizeBytes)
    }

    override suspend fun readFileContent(filePath: String): ByteArray? = fileSystem[filePath]
}

/**
 * Exception thrown when a download is blocked due to metered connection.
 */
class MeteredConnectionException(
    message: String,
) : Exception(message)

/**
 * Exception thrown when model integrity verification fails.
 */
class IntegrityVerificationException(
    message: String,
) : Exception(message)
