package de.unistuttgart.isw.dtengine.event

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventFilter
import de.unistuttgart.isw.dtengine.database.DtEventStore
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple process-local event bus.
 *
 * The bus is intentionally small and synchronous from the caller's perspective:
 * every publish first persists the event if an event store is configured, then
 * forwards the event to all matching subscribers. This makes it suitable as the
 * central integration point between gateways, mappings, services, synchronizers,
 * AAS model management and monitoring.
 */
class InMemoryDtEventBus(
    private val eventStore: DtEventStore? = null,
    private val maxBufferedEvents: Int = 500
) : DtEventBus {

    private val subscribers = ConcurrentHashMap<ComponentId, Subscription>()
    private val bufferedEvents = ArrayDeque<DtEvent>()

    override suspend fun publish(event: DtEvent) {
        eventStore?.append(event)
        synchronized(bufferedEvents) {
            bufferedEvents.addLast(event)
            while (bufferedEvents.size > maxBufferedEvents.coerceAtLeast(1)) {
                bufferedEvents.removeFirst()
            }
        }

        subscribers.values
            .filter { it.filter.matches(event) }
            .forEach { subscription -> subscription.observer.onEvent(event) }
    }

    override suspend fun subscribe(observer: DtObserver, filter: EventFilter) {
        subscribers[observer.observerId] = Subscription(observer, filter)
    }

    override suspend fun unsubscribe(observerId: ComponentId) {
        subscribers.remove(observerId)
    }

    suspend fun recent(limit: Int = 100): List<DtEvent> {
        val limited = limit.coerceAtLeast(1)
        return eventStore?.recent(limited)
            ?: synchronized(bufferedEvents) { bufferedEvents.toList().asReversed().take(limited) }
    }

    private data class Subscription(
        val observer: DtObserver,
        val filter: EventFilter
    )
}
