package de.unistuttgart.isw.dtengine.monitoring

import java.time.Instant

/**
 * REST-facing monitoring DTOs.
 *
 * They are intentionally separate from the engine domain classes so the frontend
 * gets a stable and compact API even if the internal DT model evolves.
 */
data class MonitoringDashboardSnapshot(
    val timestamp: Instant = Instant.now(),
    val conveyor: ConveyorMonitoringState,
    val kpis: List<KpiValue>,
    val machineData: List<MachineDataPoint>,
    val services: List<MonitoringServiceDescriptor>,
    val aasProperties: List<MonitoringAasProperty>,
    val mappings: List<MonitoringMappingDescriptor> = emptyList(),
    val recentEvents: List<MonitoringEventDescriptor> = emptyList(),
    val messages: List<String> = emptyList()
)

data class ConveyorMonitoringState(
    val state: String = "UNKNOWN",
    val severity: String = "unknown",
    val integrityPercent: Double? = null,
    val transportTimeMillis: Long? = null,
    val productivityKgPerSecond: Double? = null,
    val payloadWeightKg: Double? = null,
    val updatedAt: Instant? = null
)

data class KpiValue(
    val id: String,
    val label: String,
    val value: Any?,
    val unit: String? = null,
    val updatedAt: Instant? = null
)

data class MachineDataPoint(
    val id: String,
    val description: String = "",
    val machineId: String? = null,
    val topic: String? = null,
    val valueType: String? = null,
    val latestValue: Any? = null,
    val quality: String? = null,
    val updatedAt: Instant? = null,
    val sourceComponent: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class MachineDataHistoryEntry(
    val sequenceId: Long,
    val value: Any?,
    val quality: String,
    val timestamp: Instant,
    val sourceComponent: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class MonitoringServiceDescriptor(
    val serviceId: String,
    val componentId: String,
    val serviceType: String,
    val description: String = "",
    val requiredDataPoints: List<String> = emptyList(),
    val requiredModelProperties: List<String> = emptyList(),
    val requiredFunctions: List<String> = emptyList(),
    val producedDataPoints: List<String> = emptyList(),
    val ready: Boolean? = null,
    val missingDataPoints: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val updatedAt: Instant? = null
)

data class MonitoringAasProperty(
    val shellId: String,
    val submodelId: String,
    val idShortPath: String,
    val idShort: String,
    val value: Any?,
    val valueType: String,
    val semanticId: String? = null,
    val category: String? = null,
    val description: String = "",
    val observedAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)
data class MonitoringMappingDescriptor(
    val mappingId: String,
    val source: String,
    val target: String,
    val direction: String,
    val transformation: String,
    val enabled: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

data class MonitoringEventDescriptor(
    val id: String,
    val type: String,
    val source: String,
    val severity: String,
    val timestamp: Instant,
    val correlationId: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)
