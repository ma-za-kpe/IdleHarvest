package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/**
 * Metadata for a deployed ML model in the registry.
 */
@Serializable
data class ModelMetadata(
    val id: String,
    val version: String,
    val purpose: ModelPurpose,
    val sizeBytes: Long,
    val quantization: QuantizationLevel,
    val targetHardware: HardwareProfile,
    val sha256Checksum: String,
    val isStable: Boolean,
)

/**
 * Hardware profile describing the target device characteristics for a model.
 */
@Serializable
data class HardwareProfile(
    val architecture: String,
    val minCores: Int,
    val minRamGb: Float,
    val supportedBackends: List<InferenceBackend>,
)

/**
 * Represents a downloaded model file on the device filesystem.
 */
@Serializable
data class ModelFile(
    val modelId: String,
    val version: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256Checksum: String,
    val downloadedAt: Long,
)
