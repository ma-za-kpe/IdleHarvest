package com.maku.idleharvest.domain.models

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Unique identifier for an agent instance. */
@Serializable
@JvmInline
value class AgentId(val value: String)

/** Identifier for a BLE mesh peer. */
@Serializable
@JvmInline
value class PeerId(val value: String)

/** Wallet address for USDC/token transactions. */
@Serializable
@JvmInline
value class WalletAddress(val value: String)
