package com.maku.idleharvest.domain.interfaces

import com.maku.idleharvest.domain.models.AgentEvent
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Central event coordination bus between agents.
 * Agents communicate through publish/subscribe rather than direct calls,
 * enabling loose coupling and easier testing.
 *
 * Validates: Requirements 13.1
 */
interface AgentEventBus {
    /** Publish an event to all subscribers of the event's type. */
    fun <T : AgentEvent> publish(event: T)

    /** Subscribe to events of a specific type as a reactive Flow. */
    fun <T : AgentEvent> subscribe(eventType: KClass<T>): Flow<T>
}
