package de.unistuttgart.isw.dtengine.core

import java.time.Instant
import java.util.UUID

data class DtEvent(
    val id: UUID = UUID.randomUUID(),
    val type: EventType,
    val source: ComponentId,
    val payload: Map<String, Any?> = emptyMap(),
    val timestamp: Instant = Instant.now(),
    val correlationId: CorrelationId? = null,
    val severity: EventSeverity = EventSeverity.INFO
)

enum class EventType {
    GATEWAY_DATA_RECEIVED,
    GATEWAY_COMMAND_REQUESTED,
    GATEWAY_COMMAND_SENT,
    DATA_VALUE_WRITTEN,
    MODEL_PROPERTY_READ,
    MODEL_PROPERTY_CREATED,
    MODEL_PROPERTY_UPDATED,
    MODEL_VALIDATION_FAILED,
    SERVICE_REQUEST,
    SERVICE_ANSWER,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    MAPPING_CREATED,
    MAPPING_UPDATED,
    MAPPING_APPLIED,
    MAPPING_SKIPPED,
    SYNCHRONIZATION_REQUESTED,
    SYNCHRONIZATION_COMPLETED,
    MONITORING_SNAPSHOT,
    ERROR
}

enum class EventSeverity {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

interface DtObserver {
    val observerId: ComponentId
    suspend fun onEvent(event: DtEvent)
}

interface DtEventBus {
    suspend fun publish(event: DtEvent)
    suspend fun subscribe(observer: DtObserver, filter: EventFilter = EventFilter.all())
    suspend fun unsubscribe(observerId: ComponentId)
}

data class EventFilter(
    val acceptedTypes: Set<EventType>? = null,
    val acceptedSources: Set<ComponentId>? = null
) {
    fun matches(event: DtEvent): Boolean {
        val typeMatches = acceptedTypes == null || event.type in acceptedTypes
        val sourceMatches = acceptedSources == null || event.source in acceptedSources
        return typeMatches && sourceMatches
    }

    companion object {
        fun all(): EventFilter = EventFilter()
    }
}
