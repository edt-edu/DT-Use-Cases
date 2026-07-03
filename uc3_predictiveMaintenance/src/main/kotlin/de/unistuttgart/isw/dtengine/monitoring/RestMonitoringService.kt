package de.unistuttgart.isw.dtengine.monitoring

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.DataPointDefinition
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.DtEventStore
import de.unistuttgart.isw.dtengine.database.ServiceRequirementStore
import de.unistuttgart.isw.dtengine.database.ServiceRegistryStore
import de.unistuttgart.isw.dtengine.database.StoredDataValue
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.mapping.DatabaseEndpoint
import de.unistuttgart.isw.dtengine.mapping.DtMapping
import de.unistuttgart.isw.dtengine.mapping.GatewayEndpoint
import de.unistuttgart.isw.dtengine.mapping.MappingEndpoint
import de.unistuttgart.isw.dtengine.mapping.ModelEndpoint
import de.unistuttgart.isw.dtengine.mapping.ServiceEndpoint
import de.unistuttgart.isw.dtengine.model.aas.AasProperty
import de.unistuttgart.isw.dtengine.model.aas.AasRepositoryClient
import de.unistuttgart.isw.dtengine.service.AbstractMonitoringService
import de.unistuttgart.isw.dtengine.service.MonitoringSnapshot
import de.unistuttgart.isw.dtengine.service.ServiceReadiness
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import de.unistuttgart.isw.dtengine.service.ServiceResponse
import de.unistuttgart.isw.dtengine.service.ServiceStartContext
import de.unistuttgart.isw.dtengine.service.ServiceTickContext
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Monitoring service used by the REST controller and the frontend.
 *
 * The service reads the current runtime values from the DT data store, service
 * registry entries from the service registry, and AAS properties from the AAS
 * repository if these components are enabled. It also keeps a small in-memory
 * buffer for events and values that are dispatched directly to the service.
 */
class RestMonitoringService(
    override val serviceId: ServiceId,
    override val id: ComponentId,
    override val description: String,
    private val maxRecentEvents: Int,
    private val defaultHistoryLimit: Int,
    private val dataStoreProvider: ObjectProvider<DtDataStore>,
    private val requirementStoreProvider: ObjectProvider<ServiceRequirementStore>,
    private val registryStoreProvider: ObjectProvider<ServiceRegistryStore>,
    private val aasRepositoryProvider: ObjectProvider<AasRepositoryClient>,
    private val mappingRegistryProvider: ObjectProvider<AbstractMappingRegistry>,
    private val eventStoreProvider: ObjectProvider<DtEventStore>
) : AbstractMonitoringService() {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)
    private val recentEvents = ArrayDeque<DtEvent>()
    private val recordedValues = ConcurrentHashMap<DataPointId, DataValue>()

    override val status: ComponentStatus
        get() = statusRef.get()

    override val requiredDataPoints: Set<DataPointId> = emptySet()
    override val producedDataPoints: Set<DataPointId> = emptySet()

    private val dataStore: DtDataStore?
        get() = dataStoreProvider.ifAvailableOrNull()

    private val requirementStore: ServiceRequirementStore?
        get() = requirementStoreProvider.ifAvailableOrNull()

    private val registryStore: ServiceRegistryStore?
        get() = registryStoreProvider.ifAvailableOrNull()

    private val aasRepository: AasRepositoryClient?
        get() = aasRepositoryProvider.ifAvailableOrNull()

    private val mappingRegistry: AbstractMappingRegistry?
        get() = mappingRegistryProvider.ifAvailableOrNull()

    private val eventStore: DtEventStore?
        get() = eventStoreProvider.ifAvailableOrNull()

    override suspend fun start() {
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus = HealthStatus(
        alive = status == ComponentStatus.RUNNING || status == ComponentStatus.CREATED,
        status = status,
        message = "REST monitoring service",
        details = mapOf(
            "dataStore" to (dataStore != null).toString(),
            "serviceRegistry" to (registryStore != null).toString(),
            "aasRepository" to (aasRepository != null).toString(),
            "mappingRegistry" to (mappingRegistry != null).toString(),
            "eventStore" to (eventStore != null).toString(),
            "recordedValues" to recordedValues.size.toString(),
            "recentEvents" to recentEvents.size.toString()
        )
    )

    override suspend fun canStart(context: ServiceStartContext): ServiceReadiness = ServiceReadiness(ready = true)

    override suspend fun handleRequest(request: ServiceRequest): ServiceResponse = when (request.operation) {
        "snapshot", "dashboard-snapshot" -> ServiceResponse(
            request = request,
            successful = true,
            payload = mapOf("snapshot" to dashboardSnapshot()),
            message = "Current monitoring snapshot"
        )
        "values" -> {
            val requestedIds = request.payload["dataPointIds"]
                ?.toString()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.map { DataPointId(it) }
                ?.toSet()
                .orEmpty()
            ServiceResponse(
                request = request,
                successful = true,
                payload = mapOf("values" to values(requestedIds)),
                message = "Current monitoring values"
            )
        }
        else -> ServiceResponse(request, successful = false, message = "Unsupported monitoring operation '${request.operation}'")
    }

    override suspend fun handleEvent(event: DtEvent): List<ServiceRequest> {
        recordEvent(event)
        event.toDataValueOrNull()?.let { recordValue(it) }
        return emptyList()
    }

    override suspend fun tick(context: ServiceTickContext): List<DtEvent> = listOf(
        DtEvent(
            type = EventType.MONITORING_SNAPSHOT,
            source = id,
            severity = EventSeverity.DEBUG,
            payload = mapOf(
                "serviceId" to serviceId.value,
                "knownDataPoints" to dataStore?.knownDataPoints()?.size,
                "recordedValues" to recordedValues.size,
                "timestamp" to context.now.toString()
            )
        )
    )

    override suspend fun recordEvent(event: DtEvent) {
        synchronized(recentEvents) {
            recentEvents.addLast(event)
            while (recentEvents.size > maxRecentEvents) {
                recentEvents.removeFirst()
            }
        }
    }

    override suspend fun recordValue(value: DataValue) {
        recordedValues[value.id] = value
    }

    override suspend fun snapshot(): MonitoringSnapshot = MonitoringSnapshot(
        timestamp = Instant.now(),
        componentStates = mapOf(id to status.name),
        latestValues = currentValues().associateBy { it.id },
        recentEvents = synchronized(recentEvents) { recentEvents.toList() },
        messages = serviceMessages()
    )

    override suspend fun values(dataPointIds: Set<DataPointId>): List<DataValue> {
        val ids = dataPointIds.ifEmpty { dataStore?.knownDataPoints().orEmpty() }
        val dbValues = if (ids.isEmpty()) emptyList() else dataStore?.readLatest(ids).orEmpty()
        val dbIds = dbValues.map { it.id }.toSet()
        val memoryValues = recordedValues.values.filter { ids.isEmpty() || it.id in ids }.filterNot { it.id in dbIds }
        return (dbValues + memoryValues).sortedBy { it.id.value }
    }

    suspend fun dashboardSnapshot(): MonitoringDashboardSnapshot {
        val machineData = machineDataPoints()
        val latestById = machineData.associateBy { it.id }
        val aasProperties = aasProperties()
        val services = serviceDescriptors()
        return MonitoringDashboardSnapshot(
            timestamp = Instant.now(),
            conveyor = conveyorState(latestById, aasProperties),
            kpis = conveyorKpis(latestById),
            machineData = machineData,
            services = services,
            aasProperties = aasProperties,
            mappings = mappings(),
            recentEvents = recentEvents(50),
            messages = serviceMessages()
        )
    }

    suspend fun machineDataPoints(): List<MachineDataPoint> {
        val store = dataStore
        val definitions = store?.knownDataPoints()
            .orEmpty()
            .mapNotNull { store?.getDataPoint(it) }
        val latestValues = values(definitions.map { it.id }.toSet()).associateBy { it.id }
        val definitionRows = definitions.map { it.toMachineDataPoint(latestValues[it.id]) }

        val definitionIds = definitions.map { it.id }.toSet()
        val memoryOnlyRows = recordedValues.values
            .filterNot { it.id in definitionIds }
            .map { value ->
                MachineDataPoint(
                    id = value.id.value,
                    description = value.metadata["description"].orEmpty(),
                    machineId = value.metadata["machineId"],
                    topic = value.metadata["topic"],
                    valueType = value.metadata["valueType"],
                    latestValue = value.value,
                    quality = value.quality.name,
                    updatedAt = value.timestamp,
                    sourceComponent = value.source?.value,
                    metadata = value.metadata
                )
            }

        return (definitionRows + memoryOnlyRows).sortedWith(
            compareBy<MachineDataPoint> { it.machineId ?: "" }
                .thenBy { it.topic ?: "" }
                .thenBy { it.id }
        )
    }

    suspend fun history(dataPointId: DataPointId, limit: Int = defaultHistoryLimit): List<MachineDataHistoryEntry> {
        val limited = limit.coerceIn(1, defaultHistoryLimit.coerceAtLeast(1) * 10)
        val dbHistory = dataStore?.history(dataPointId, limited).orEmpty()
        if (dbHistory.isNotEmpty()) return dbHistory.map { it.toHistoryEntry() }
        val memoryValue = recordedValues[dataPointId] ?: return emptyList()
        return listOf(
            MachineDataHistoryEntry(
                sequenceId = 0,
                value = memoryValue.value,
                quality = memoryValue.quality.name,
                timestamp = memoryValue.timestamp,
                sourceComponent = memoryValue.source?.value,
                metadata = memoryValue.metadata
            )
        )
    }

    suspend fun serviceDescriptors(): List<MonitoringServiceDescriptor> {
        val store = registryStore ?: return emptyList()
        return store.serviceDescriptors().map { descriptor ->
            val readiness = requirementStore?.checkServiceReadiness(descriptor.serviceId)
            MonitoringServiceDescriptor(
                serviceId = descriptor.serviceId.value,
                componentId = descriptor.componentId.value,
                serviceType = descriptor.serviceType.name,
                description = descriptor.description,
                requiredDataPoints = descriptor.requiredDataPoints.map { it.value }.sorted(),
                requiredModelProperties = descriptor.requiredModelProperties.map { it.value }.sorted(),
                requiredFunctions = descriptor.requiredFunctions.sorted(),
                producedDataPoints = descriptor.producedDataPoints.map { it.value }.sorted(),
                ready = readiness?.ready,
                missingDataPoints = readiness?.missingDataPoints?.map { it.value }?.sorted().orEmpty(),
                metadata = descriptor.metadata,
                updatedAt = descriptor.updatedAt
            )
        }
    }

    suspend fun aasProperties(): List<MonitoringAasProperty> = aasRepository
        ?.listProperties()
        .orEmpty()
        .map { it.toMonitoringAasProperty() }
        .sortedWith(compareBy<MonitoringAasProperty> { it.submodelId }.thenBy { it.idShortPath })

    private suspend fun currentValues(): List<DataValue> = values(emptySet())

    private fun serviceMessages(): List<String> = buildList {
        if (dataStore == null) add("No DT data store bean available. Enable dt.sqlite.enabled to show persisted machine values.")
        if (registryStore == null) add("No service registry bean available. Enable dt.sqlite.enabled to show service descriptors.")
        if (aasRepository == null) add("No AAS repository bean available. Enable dt.aas.enabled to show AAS properties.")
        if (mappingRegistry == null) add("No mapping registry bean available. Mappings are shown only after synchronization is enabled.")
    }

    suspend fun mappings(): List<MonitoringMappingDescriptor> = mappingRegistry
        ?.all()
        .orEmpty()
        .map { it.toMonitoringMappingDescriptor() }
        .sortedBy { it.mappingId }

    suspend fun recentEvents(limit: Int = maxRecentEvents): List<MonitoringEventDescriptor> {
        val limited = limit.coerceIn(1, maxRecentEvents.coerceAtLeast(1) * 10)
        val stored = eventStore?.recent(limited).orEmpty()
        val events = if (stored.isNotEmpty()) {
            stored
        } else {
            synchronized(recentEvents) { recentEvents.toList().asReversed().take(limited) }
        }
        return events.map { it.toMonitoringEventDescriptor() }
    }

    private fun conveyorState(
        latestById: Map<String, MachineDataPoint>,
        aasProperties: List<MonitoringAasProperty>
    ): ConveyorMonitoringState {
        val statePoint = latestById[CONVEYOR_STATE_ID]
        val integrityPoint = latestById[CONVEYOR_INTEGRITY_ID]
        val transportPoint = latestById[CONVEYOR_TRANSPORT_TIME_ID]
        val productivityPoint = latestById[CONVEYOR_PRODUCTIVITY_ID]
        val payloadPoint = latestById[CONVEYOR_PAYLOAD_WEIGHT_ID]

        val aasState = aasProperties.firstOrNull { it.metadata["dataPointId"] == CONVEYOR_STATE_ID || it.idShortPath.endsWith("state") }
        val state = statePoint?.latestValue?.toString()
            ?: aasState?.value?.toString()
            ?: "UNKNOWN"

        return ConveyorMonitoringState(
            state = state,
            severity = state.toConveyorSeverity(),
            integrityPercent = integrityPoint?.latestValue.toDoubleOrNull(),
            transportTimeMillis = transportPoint?.latestValue.toLongOrNull(),
            productivityKgPerSecond = productivityPoint?.latestValue.toDoubleOrNull(),
            payloadWeightKg = payloadPoint?.latestValue.toDoubleOrNull(),
            updatedAt = listOfNotNull(
                statePoint?.updatedAt,
                integrityPoint?.updatedAt,
                transportPoint?.updatedAt,
                productivityPoint?.updatedAt,
                payloadPoint?.updatedAt,
                aasState?.observedAt
            ).maxOrNull()
        )
    }

    private fun conveyorKpis(latestById: Map<String, MachineDataPoint>): List<KpiValue> = listOf(
        KpiValue(
            id = CONVEYOR_TRANSPORT_TIME_ID,
            label = "Transport time",
            value = latestById[CONVEYOR_TRANSPORT_TIME_ID]?.latestValue,
            unit = "ms",
            updatedAt = latestById[CONVEYOR_TRANSPORT_TIME_ID]?.updatedAt
        ),
        KpiValue(
            id = CONVEYOR_PAYLOAD_WEIGHT_ID,
            label = "Payload weight",
            value = latestById[CONVEYOR_PAYLOAD_WEIGHT_ID]?.latestValue,
            unit = "kg",
            updatedAt = latestById[CONVEYOR_PAYLOAD_WEIGHT_ID]?.updatedAt
        ),
        KpiValue(
            id = CONVEYOR_PRODUCTIVITY_ID,
            label = "Productivity",
            value = latestById[CONVEYOR_PRODUCTIVITY_ID]?.latestValue,
            unit = "kg/s",
            updatedAt = latestById[CONVEYOR_PRODUCTIVITY_ID]?.updatedAt
        ),
        KpiValue(
            id = CONVEYOR_INTEGRITY_ID,
            label = "Conveyor integrity",
            value = latestById[CONVEYOR_INTEGRITY_ID]?.latestValue,
            unit = "%",
            updatedAt = latestById[CONVEYOR_INTEGRITY_ID]?.updatedAt
        )
    )

    private fun DtMapping.toMonitoringMappingDescriptor(): MonitoringMappingDescriptor = MonitoringMappingDescriptor(
        mappingId = id.value,
        source = source.describe(),
        target = target.describe(),
        direction = direction.name,
        transformation = transformation.type.name,
        enabled = enabled,
        metadata = metadata
    )

    private fun MappingEndpoint.describe(): String = when (this) {
        is GatewayEndpoint -> "gateway:${owner.value}:${dataPointId.value}"
        is ModelEndpoint -> "model:${owner.value}:${propertyId.value}"
        is ServiceEndpoint -> "service:${owner.value}:$inputOrOutput"
        is DatabaseEndpoint -> "database:${owner.value}:$table:$column:${key.orEmpty()}"
    }

    private fun DtEvent.toMonitoringEventDescriptor(): MonitoringEventDescriptor = MonitoringEventDescriptor(
        id = id.toString(),
        type = type.name,
        source = source.value,
        severity = severity.name,
        timestamp = timestamp,
        correlationId = correlationId?.value,
        payload = payload
    )

    private fun DataPointDefinition.toMachineDataPoint(latest: DataValue?): MachineDataPoint = MachineDataPoint(
        id = id.value,
        description = description,
        machineId = machineId,
        topic = topic,
        valueType = valueType,
        latestValue = latest?.value,
        quality = latest?.quality?.name,
        updatedAt = latest?.timestamp ?: updatedAt,
        sourceComponent = latest?.source?.value ?: sourceComponent?.value,
        metadata = metadata + latest?.metadata.orEmpty()
    )

    private fun StoredDataValue.toHistoryEntry(): MachineDataHistoryEntry = MachineDataHistoryEntry(
        sequenceId = sequenceId,
        value = value.value,
        quality = value.quality.name,
        timestamp = value.timestamp,
        sourceComponent = value.source?.value,
        metadata = value.metadata
    )

    private fun AasProperty.toMonitoringAasProperty(): MonitoringAasProperty = MonitoringAasProperty(
        shellId = address.shellId,
        submodelId = address.submodelId,
        idShortPath = address.path,
        idShort = idShort,
        value = value,
        valueType = valueType.name,
        semanticId = semanticId,
        category = category,
        description = description,
        observedAt = observedAt,
        metadata = metadata
    )

    private fun DtEvent.toDataValueOrNull(): DataValue? {
        val dataPointId = payload["dataPointId"]?.toString() ?: payload["propertyId"]?.toString() ?: return null
        return DataValue(
            id = DataPointId(dataPointId),
            value = payload["value"],
            timestamp = timestamp,
            quality = payload["quality"]?.toString()?.let { runCatching { de.unistuttgart.isw.dtengine.core.DataQuality.valueOf(it) }.getOrNull() }
                ?: de.unistuttgart.isw.dtengine.core.DataQuality.UNKNOWN,
            source = source,
            metadata = payload.mapValues { it.value?.toString().orEmpty() }
        )
    }

    private fun Any?.toDoubleOrNull(): Double? = when (this) {
        null -> null
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> toString().toDoubleOrNull()
    }

    private fun Any?.toLongOrNull(): Long? = when (this) {
        null -> null
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> toString().toLongOrNull()
    }

    private fun String.toConveyorSeverity(): String = when (uppercase()) {
        "NORMAL", "OK", "GOOD" -> "normal"
        "WARNING", "WARN" -> "warning"
        "EMERGENCY", "CRITICAL", "ERROR" -> "emergency"
        else -> "unknown"
    }

    private fun <T : Any> ObjectProvider<T>.ifAvailableOrNull(): T? = runCatching { getIfAvailable() }.getOrNull()

    companion object {
        const val CONVEYOR_STATE_ID = "conveyor.state"
        const val CONVEYOR_TRANSPORT_TIME_ID = "conveyor.transport-time-ms"
        const val CONVEYOR_PAYLOAD_WEIGHT_ID = "conveyor.payload-weight-kg"
        const val CONVEYOR_PRODUCTIVITY_ID = "conveyor.productivity-kg-per-second"
        const val CONVEYOR_INTEGRITY_ID = "conveyor.integrity-percent"
    }
}
