package de.unistuttgart.isw.dtengine.service

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import java.time.Instant

/**
 * Abstract monitoring service for exposing runtime state to GUI, REST, WebSocket, etc.
 */
abstract class AbstractMonitoringService : AbstractDtService() {
    final override val serviceType: ServiceType = ServiceType.MONITORING

    abstract suspend fun recordEvent(event: DtEvent)

    abstract suspend fun recordValue(value: DataValue)

    abstract suspend fun snapshot(): MonitoringSnapshot

    abstract suspend fun values(dataPointIds: Set<DataPointId> = emptySet()): List<DataValue>
}

data class MonitoringSnapshot(
    val timestamp: Instant = Instant.now(),
    val componentStates: Map<ComponentId, String> = emptyMap(),
    val latestValues: Map<DataPointId, DataValue> = emptyMap(),
    val recentEvents: List<DtEvent> = emptyList(),
    val messages: List<String> = emptyList()
)
