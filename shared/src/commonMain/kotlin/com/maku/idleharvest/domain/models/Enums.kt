package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable

/** Thermal state of the device affecting agent behavior and scan frequency. */
@Serializable
enum class ThermalState { COOL, WARM, HOT, CRITICAL }

/** Type of airtime/data transaction. */
@Serializable
enum class TransactionType { SELL, TRANSFER, PURCHASE }

/** Outcome of a transaction attempt. */
@Serializable
enum class TransactionOutcome { SUCCESS, FAILED, PENDING, CANCELLED }

/** Type of resource shared to DePIN networks. */
@Serializable
enum class ResourceType { BANDWIDTH, STORAGE, COMPUTE }

/** Source of an earning event. */
@Serializable
enum class EarningSource { AIRTIME_SALE, DEPIN_REWARD, MESH_SERVICE, NANOPAYMENT }

/** Level of autonomous action allowed for an agent. */
@Serializable
enum class AutonomyLevel { MANUAL, SEMI_AUTOMATIC, FULLY_AUTOMATIC }

/** Status of a payout request lifecycle. */
@Serializable
enum class PayoutStatus { PENDING, POLICY_CHECK, SIGNING, SUBMITTED, CONFIRMED, FAILED }

/** State of the BLE mesh coordinator. */
@Serializable
enum class MeshState { IDLE, SCANNING, CONNECTED, ERROR }

/** Purpose category for deployed ML models. */
@Serializable
enum class ModelPurpose { USAGE_PREDICTION, RESOURCE_OPTIMIZATION, PRICE_ESTIMATION }

/** Quantization level for deployed models. */
@Serializable
enum class QuantizationLevel { FP32, FP16, INT8, INT4 }

/** Overall state of an agent's lifecycle. */
@Serializable
enum class AgentState { IDLE, EVALUATING, EXECUTING, PAUSED, ERROR }
