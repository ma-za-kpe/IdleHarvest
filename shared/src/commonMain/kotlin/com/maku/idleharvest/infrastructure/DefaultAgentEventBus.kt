package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.interfaces.AgentEventBus
import com.maku.idleharvest.domain.models.AgentEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlin.reflect.KClass

/**
 * Default implementation of [AgentEventBus] using Kotlin coroutines SharedFlow.
 *
 * Thread-safe by design — MutableSharedFlow is safe for concurrent emit and collect.
 * Multiple subscribers of the same event type are supported naturally via Flow collection.
 *
 * Uses replay = 0 (no event replay for late subscribers) and extraBufferCapacity = 64
 * to avoid blocking publishers. DROP_OLDEST overflow strategy ensures publishers never suspend.
 */
class DefaultAgentEventBus : AgentEventBus {
    private val _events =
        MutableSharedFlow<AgentEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override fun <T : AgentEvent> publish(event: T) {
        _events.tryEmit(event)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : AgentEvent> subscribe(eventType: KClass<T>): Flow<T> = _events.filter { eventType.isInstance(it) } as Flow<T>
}
