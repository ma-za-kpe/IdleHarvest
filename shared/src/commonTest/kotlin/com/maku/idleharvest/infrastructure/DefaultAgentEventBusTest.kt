package com.maku.idleharvest.infrastructure

import com.maku.idleharvest.domain.models.AgentEvent
import com.maku.idleharvest.domain.models.ResourceProfile
import com.maku.idleharvest.domain.models.ThermalState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAgentEventBusTest {

    private val eventBus = DefaultAgentEventBus()

    private val sampleResourceProfile = ResourceProfile(
        airtimeBalance = null,
        dataBundles = emptyList(),
        availableBandwidthMbps = 10f,
        freeStorageMb = 5000L,
        idleComputePercent = 70,
        batteryLevel = 80,
        isCharging = false,
        thermalState = ThermalState.COOL,
        timestamp = 1_000_000L,
    )

    @Test
    fun subscriberReceivesPublishedEvent() = runTest(UnconfinedTestDispatcher()) {
        val deferred = async {
            eventBus.subscribe(AgentEvent.ConnectivityChanged::class).first()
        }

        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = true))

        val received = deferred.await()
        assertEquals(true, received.isOnline)
    }

    @Test
    fun subscriberOnlyReceivesMatchingEventType() = runTest(UnconfinedTestDispatcher()) {
        val deferred = async {
            eventBus.subscribe(AgentEvent.ConnectivityChanged::class).take(1).toList()
        }

        // Publish a non-matching event first
        eventBus.publish(AgentEvent.ThermalStateChanged(state = ThermalState.HOT))
        // Then the matching event
        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = false))

        val receivedEvents = deferred.await()
        assertEquals(1, receivedEvents.size)
        assertEquals(false, receivedEvents[0].isOnline)
    }

    @Test
    fun multipleSubscribersReceiveSameEvent() = runTest(UnconfinedTestDispatcher()) {
        val deferred1 = async {
            eventBus.subscribe(AgentEvent.ResourceUpdated::class).first()
        }
        val deferred2 = async {
            eventBus.subscribe(AgentEvent.ResourceUpdated::class).first()
        }

        eventBus.publish(AgentEvent.ResourceUpdated(profile = sampleResourceProfile))

        val received1 = deferred1.await()
        val received2 = deferred2.await()
        assertEquals(received1.profile, received2.profile)
    }

    @Test
    fun multipleEventTypesRoutedCorrectly() = runTest(UnconfinedTestDispatcher()) {
        val thermalDeferred = async {
            eventBus.subscribe(AgentEvent.ThermalStateChanged::class).first()
        }
        val connectivityDeferred = async {
            eventBus.subscribe(AgentEvent.ConnectivityChanged::class).first()
        }

        eventBus.publish(AgentEvent.ThermalStateChanged(state = ThermalState.HOT))
        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = true))

        val thermalEvent = thermalDeferred.await()
        val connectivityEvent = connectivityDeferred.await()

        assertEquals(ThermalState.HOT, thermalEvent.state)
        assertTrue(connectivityEvent.isOnline)
    }

    @Test
    fun publishDoesNotBlockWhenNoSubscribers() {
        // Should not throw or block
        eventBus.publish(AgentEvent.ConnectivityChanged(isOnline = true))
        eventBus.publish(AgentEvent.ThermalStateChanged(state = ThermalState.CRITICAL))
    }
}
