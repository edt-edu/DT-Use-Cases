package de.unistuttgart.isw.dtengine.event

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventFilter
import de.unistuttgart.isw.dtengine.core.EventType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryDtEventBusTests {
    @Test
    fun `publishes event to matching subscriber`() = runBlocking {
        val bus = InMemoryDtEventBus(maxBufferedEvents = 10)
        val received = mutableListOf<DtEvent>()
        val observer = object : DtObserver {
            override val observerId: ComponentId = ComponentId("observer")
            override suspend fun onEvent(event: DtEvent) {
                received += event
            }
        }

        bus.subscribe(observer, EventFilter(acceptedTypes = setOf(EventType.GATEWAY_DATA_RECEIVED)))
        bus.publish(DtEvent(type = EventType.GATEWAY_DATA_RECEIVED, source = ComponentId("gateway")))
        bus.publish(DtEvent(type = EventType.ERROR, source = ComponentId("gateway")))

        assertEquals(1, received.size)
        assertEquals(EventType.GATEWAY_DATA_RECEIVED, received.single().type)
    }
}
