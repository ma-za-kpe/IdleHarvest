package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.ModelFile
import com.maku.idleharvest.domain.models.ModelMetadata
import com.maku.idleharvest.domain.models.ModelPurpose
import kotlinx.coroutines.flow.StateFlow

/**
 * Model lifecycle and update management for on-device ML models.
 * Handles downloads, integrity verification, versioning, and rollback.
 *
 * Validates: Requirements 9.1, 9.3, 9.5
 */
interface ModelRegistry {
    /** All available model metadata in the registry. */
    val availableModels: StateFlow<List<ModelMetadata>>

    /** Download a model .pte file by its registry identifier. */
    suspend fun downloadModel(modelId: String): Result<ModelFile>

    /** Verify model file integrity via SHA-256 checksum. */
    suspend fun verifyIntegrity(modelFile: ModelFile): Boolean

    /** Rollback to the previous stable model version. */
    suspend fun rollback(modelId: String): Result<ModelFile>

    /** Get the active model metadata for a given purpose, or null if none is active. */
    fun getActiveModel(purpose: ModelPurpose): ModelMetadata?
}
