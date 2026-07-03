package de.unistuttgart.isw.dtengine.synchronization

import de.unistuttgart.isw.dtengine.core.CommandRequest
import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.MappingId
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.toDefinition
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.mapping.DatabaseEndpoint
import de.unistuttgart.isw.dtengine.mapping.DtMapping
import de.unistuttgart.isw.dtengine.mapping.GatewayEndpoint
import de.unistuttgart.isw.dtengine.mapping.MappingEndpoint
import de.unistuttgart.isw.dtengine.mapping.MappingDirection
import de.unistuttgart.isw.dtengine.mapping.MappingProperties
import de.unistuttgart.isw.dtengine.mapping.ModelEndpoint
import de.unistuttgart.isw.dtengine.mapping.ServiceEndpoint
import de.unistuttgart.isw.dtengine.mapping.ValueTransformer
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Concrete mapping-based synchronizer.
 *
 * Two synchronization styles are supported:
 * 1. event-driven synchronization: a gateway/model/service event is translated
 *    into a source endpoint and only affected mappings are executed;
 * 2. cyclic synchronization: all enabled mappings are evaluated from their
 *    current source value.
 */
class MappingSynchronizer(
    override val id: ComponentId = ComponentId("mapping-synchronizer"),
    override val description: String = "Mapping-based DT synchronizer",
    private val mappingRegistry: AbstractMappingRegistry,
    private val transformer: ValueTransformer,
    private val dataStore: DtDataStore? = null,
    private val modelManager: AbstractModelManager? = null,
    private val mappingProperties: MappingProperties = MappingProperties(),
    gateways: List<AbstractGateway> = emptyList(),
    private val serviceManager: DtServiceManager? = null,
    private val eventBus: DtEventBus? = null
) : AbstractSynchronizer(), DtObserver {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)
    private val gatewaysById = ConcurrentHashMap<ComponentId, AbstractGateway>()

    init {
        gateways.forEach { registerGateway(it) }
    }

    override val status: ComponentStatus
        get() = statusRef.get()

    override val observerId: ComponentId
        get() = id

    fun registerGateway(gateway: AbstractGateway) {
        gatewaysById[gateway.id] = gateway
    }

    override suspend fun start() {
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus = HealthStatus(
        alive = status == ComponentStatus.RUNNING || status == ComponentStatus.CREATED,
        status = status,
        message = "${mappingRegistry.all().size} mapping(s) registered",
        details = mapOf(
            "gateways" to gatewaysById.keys.joinToString(",") { it.value },
            "dataStore" to (dataStore != null).toString(),
            "modelManager" to (modelManager != null).toString(),
            "serviceManager" to (serviceManager != null).toString()
        )
    )

    override suspend fun onEvent(event: DtEvent) {
        if (event.source == id) return
        val result = synchronizeEvent(event)
        toEvents(result).forEach { eventBus?.publish(it) }
    }

    override suspend fun synchronize(context: SynchronizationContext): SynchronizationResult {
        val steps = mutableListOf<SynchronizationStepResult>()
        val errors = mutableListOf<String>()
        mappingRegistry.all()
            .filter { it.enabled }
            .forEach { mapping ->
                val step = runCatching { synchronizeMapping(mapping, context) }.getOrElse { error ->
                    errors += "${mapping.id.value}: ${error.message ?: error::class.simpleName.orEmpty()}"
                    SynchronizationStepResult(mapping, successful = false, message = error.message.orEmpty())
                }
                steps += step
            }
        return SynchronizationResult(
            cycleId = context.cycleId,
            startedAt = context.startedAt,
            finishedAt = Instant.now(),
            steps = steps,
            errors = errors
        )
    }

    suspend fun synchronizeEvent(event: DtEvent): SynchronizationResult {
        val startedAt = Instant.now()
        val eventValue = event.toDataValueOrNull()
        val mappings = mappingsForEvent(event).ifEmpty { autoMappingsForGatewayEvent(event) }
        val steps = mappings.map { mapping ->
            runCatching {
                val sourceValue = eventValue ?: readSourceValue(mapping)
                if (sourceValue == null) {
                    SynchronizationStepResult(mapping, successful = false, message = "No source value available for event ${event.id}")
                } else {
                    val transformed = transformer.transform(sourceValue, mapping)
                    writeTargetValue(mapping, transformed)
                }
            }.getOrElse { error ->
                SynchronizationStepResult(mapping, successful = false, message = error.message.orEmpty())
            }
        }
        return SynchronizationResult(
            cycleId = event.timestamp.toEpochMilli(),
            startedAt = startedAt,
            finishedAt = Instant.now(),
            steps = steps,
            errors = steps.filterNot { it.successful }.map { "${it.mapping.id.value}: ${it.message}" }
        )
    }


    private suspend fun autoMappingsForGatewayEvent(event: DtEvent): List<DtMapping> {
        if (!mappingProperties.autoMapDiscoveredGatewayDataPoints) return emptyList()
        if (event.type != EventType.GATEWAY_DATA_RECEIVED) return emptyList()
        val dataPointIdValue = event.payload["dataPointId"]?.toString()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val dataPointId = DataPointId(dataPointIdValue)
        val source = GatewayEndpoint(event.source, dataPointId)
        val topic = event.payload["topic"]?.toString().orEmpty()
        val commonMetadata = mapOf(
            "dataPointId" to dataPointId.value,
            "topic" to topic,
            "seededBy" to "MappingSynchronizer.autoMappingsForGatewayEvent",
            "discovered" to event.payload["discovered"].toString()
        )
        val databaseMapping = DtMapping(
            id = MappingId("auto:mqtt:${dataPointId.value}:to-db"),
            source = source,
            target = DatabaseEndpoint(
                owner = ComponentId(mappingProperties.databaseComponentId),
                table = "data_values",
                column = "value",
                key = dataPointId.value
            ),
            direction = MappingDirection.GATEWAY_TO_DATABASE,
            metadata = commonMetadata + mapOf("targetKind" to "latest-and-history")
        )
        val modelMapping = DtMapping(
            id = MappingId("auto:mqtt:${dataPointId.value}:to-aas"),
            source = source,
            target = ModelEndpoint(
                owner = ComponentId(mappingProperties.modelComponentId),
                propertyId = ModelPropertyId(dataPointId.value)
            ),
            direction = MappingDirection.GATEWAY_TO_MODEL,
            metadata = commonMetadata + mapOf("targetKind" to "aas-property")
        )
        val created = mutableListOf<DtMapping>()
        for (mapping in listOf(databaseMapping, modelMapping)) {
            created += mappingRegistry.findById(mapping.id) ?: mappingRegistry.add(mapping)
        }
        return created
    }

    override suspend fun synchronizeMapping(
        mapping: DtMapping,
        context: SynchronizationContext
    ): SynchronizationStepResult {
        if (!mapping.enabled) return SynchronizationStepResult(mapping, successful = true, message = "Mapping disabled")
        val liveness = checkLiveness(mapping)
        if (!liveness.bothAlive) {
            return SynchronizationStepResult(mapping, successful = false, message = liveness.message.ifBlank { "Source or target not alive" })
        }
        val sourceValue = readSourceValue(mapping)
            ?: return SynchronizationStepResult(mapping, successful = false, message = "No source value available")
        val transformed = transformer.transform(sourceValue, mapping)
        return writeTargetValue(mapping, transformed)
    }

    override suspend fun checkLiveness(mapping: DtMapping): LivenessResult {
        val sourceAlive = endpointAlive(mapping.source, asSource = true)
        val targetAlive = endpointAlive(mapping.target, asSource = false)
        val message = buildList {
            if (!sourceAlive) add("source ${mapping.source.describe()} not alive")
            if (!targetAlive) add("target ${mapping.target.describe()} not alive")
        }.joinToString("; ")
        return LivenessResult(sourceAlive, targetAlive, message)
    }

    override suspend fun readSourceValue(mapping: DtMapping): DataValue? = when (val source = mapping.source) {
        is GatewayEndpoint -> gatewaysById[source.owner]?.read(source.dataPointId)
            ?: dataStore?.readLatest(source.dataPointId)
        is ModelEndpoint -> modelManager?.getProperty(source.propertyId)?.value
        is DatabaseEndpoint -> source.key?.let { dataStore?.readLatest(DataPointId(it)) }
        is ServiceEndpoint -> null
    }

    override suspend fun writeTargetValue(mapping: DtMapping, value: DataValue): SynchronizationStepResult =
        when (val target = mapping.target) {
            is DatabaseEndpoint -> writeDatabaseTarget(mapping, target, value)
            is ModelEndpoint -> writeModelTarget(mapping, target, value)
            is GatewayEndpoint -> writeGatewayTarget(mapping, target, value)
            is ServiceEndpoint -> writeServiceTarget(mapping, target, value)
        }

    override suspend fun toEvents(result: SynchronizationResult): List<DtEvent> {
        val stepEvents = result.steps.map { step ->
            DtEvent(
                type = if (step.successful) EventType.MAPPING_APPLIED else EventType.MAPPING_SKIPPED,
                source = id,
                severity = if (step.successful) EventSeverity.DEBUG else EventSeverity.WARN,
                payload = mapOf(
                    "cycleId" to result.cycleId,
                    "mappingId" to step.mapping.id.value,
                    "direction" to step.mapping.direction.name,
                    "source" to step.mapping.source.describe(),
                    "target" to step.mapping.target.describe(),
                    "successful" to step.successful,
                    "dataPointId" to step.value?.id?.value,
                    "value" to step.value?.value,
                    "message" to step.message
                )
            )
        }
        return stepEvents + DtEvent(
            type = EventType.SYNCHRONIZATION_COMPLETED,
            source = id,
            severity = if (result.successful) EventSeverity.DEBUG else EventSeverity.WARN,
            payload = mapOf(
                "cycleId" to result.cycleId,
                "successful" to result.successful,
                "stepCount" to result.steps.size,
                "errors" to result.errors
            )
        )
    }

    private suspend fun writeDatabaseTarget(
        mapping: DtMapping,
        target: DatabaseEndpoint,
        value: DataValue
    ): SynchronizationStepResult {
        val store = dataStore
            ?: return SynchronizationStepResult(mapping, successful = false, value = value, message = "No data store configured")
        val targetId = target.key?.takeIf { it.isNotBlank() }?.let { DataPointId(it) } ?: value.id
        val normalized = if (targetId == value.id) value else value.copy(id = targetId)
        store.upsertDataPoint(normalized.toDefinition(description = normalized.metadata["description"].orEmpty()))
        store.writeValue(normalized)
        return SynchronizationStepResult(mapping, successful = true, value = normalized, message = "Wrote ${targetId.value} to database")
    }

    private suspend fun writeModelTarget(
        mapping: DtMapping,
        target: ModelEndpoint,
        value: DataValue
    ): SynchronizationStepResult {
        val manager = modelManager
            ?: return SynchronizationStepResult(mapping, successful = false, value = value, message = "No model manager configured")
        val property = manager.updateProperty(target.propertyId, value)
        return SynchronizationStepResult(
            mapping = mapping,
            successful = true,
            value = property.value ?: value,
            message = "Updated model property ${target.propertyId.value}"
        )
    }

    private suspend fun writeGatewayTarget(
        mapping: DtMapping,
        target: GatewayEndpoint,
        value: DataValue
    ): SynchronizationStepResult {
        val gateway = gatewaysById[target.owner]
            ?: return SynchronizationStepResult(mapping, successful = false, value = value, message = "Gateway ${target.owner.value} not registered")
        val result = gateway.write(
            CommandRequest(
                commandId = UUID.randomUUID().toString(),
                target = target.owner,
                command = target.dataPointId.value,
                payload = mapOf(
                    "value" to value.value,
                    "dataPointId" to target.dataPointId.value,
                    "mappingId" to mapping.id.value
                )
            )
        )
        return SynchronizationStepResult(mapping, successful = result.accepted && result.executed, value = value, message = result.message)
    }

    private suspend fun writeServiceTarget(
        mapping: DtMapping,
        target: ServiceEndpoint,
        value: DataValue
    ): SynchronizationStepResult {
        val manager = serviceManager
            ?: return SynchronizationStepResult(mapping, successful = false, value = value, message = "No service manager configured")
        val service = manager.registeredServices().firstOrNull { service ->
            service.id == target.owner || service.serviceId.value == target.owner.value
        } ?: return SynchronizationStepResult(mapping, successful = false, value = value, message = "Service ${target.owner.value} not registered")

        val response = manager.handleRequest(
            ServiceRequest(
                targetService = service.serviceId,
                operation = "mapped-value",
                payload = mapOf(
                    "inputOrOutput" to target.inputOrOutput,
                    "dataPointId" to value.id.value,
                    "value" to value.value,
                    "quality" to value.quality.name,
                    "timestamp" to value.timestamp.toString(),
                    "mappingId" to mapping.id.value
                ),
                requestedBy = id
            )
        )
        return SynchronizationStepResult(mapping, successful = response.successful, value = value, message = response.message)
    }

    private suspend fun endpointAlive(endpoint: MappingEndpoint, asSource: Boolean): Boolean = when (endpoint) {
        is GatewayEndpoint -> gatewaysById[endpoint.owner]?.health()?.alive == true || (!asSource && gatewaysById.containsKey(endpoint.owner))
        is ModelEndpoint -> modelManager?.health()?.alive != false && modelManager != null
        is DatabaseEndpoint -> dataStore?.healthIfComponentAlive() != false && dataStore != null
        is ServiceEndpoint -> serviceManager?.registeredServices()?.any { it.id == endpoint.owner || it.serviceId.value == endpoint.owner.value } == true
    }

    private suspend fun mappingsForEvent(event: DtEvent): List<DtMapping> {
        val candidates = sourceEndpointCandidates(event)
        if (candidates.isEmpty()) return emptyList()
        val result = linkedSetOf<DtMapping>()
        candidates.forEach { candidate ->
            result += mappingRegistry.findForSource(candidate).filter { it.enabled }
        }
        return result.toList()
    }

    private fun sourceEndpointCandidates(event: DtEvent): List<MappingEndpoint> {
        val dataPointId = event.payload["dataPointId"]?.toString()?.takeIf { it.isNotBlank() }
        val propertyId = event.payload["propertyId"]?.toString()?.takeIf { it.isNotBlank() }
        return buildList {
            when (event.type) {
                EventType.GATEWAY_DATA_RECEIVED, EventType.GATEWAY_COMMAND_SENT -> {
                    dataPointId?.let { add(GatewayEndpoint(event.source, DataPointId(it))) }
                }
                EventType.MODEL_PROPERTY_CREATED, EventType.MODEL_PROPERTY_UPDATED, EventType.MODEL_PROPERTY_READ -> {
                    propertyId?.let { add(ModelEndpoint(event.source, ModelPropertyId(it))) }
                    dataPointId?.let { add(ModelEndpoint(event.source, ModelPropertyId(it))) }
                }
                EventType.SERVICE_ANSWER, EventType.SERVICE_REQUEST -> {
                    dataPointId?.let { add(ServiceEndpoint(event.source, it)) }
                    propertyId?.let { add(ServiceEndpoint(event.source, it)) }
                }
                else -> Unit
            }

            dataPointId?.let { add(ServiceEndpoint(event.source, it)) }
            propertyId?.let { add(ServiceEndpoint(event.source, it)) }
        }.distinct()
    }

    private fun DtEvent.toDataValueOrNull(): DataValue? {
        val dataPointId = payload["dataPointId"]?.toString()
            ?: payload["propertyId"]?.toString()
            ?: return null
        return DataValue(
            id = DataPointId(dataPointId),
            value = payload["value"],
            timestamp = timestamp,
            quality = payload["quality"]?.toString()?.let { runCatching { DataQuality.valueOf(it) }.getOrNull() } ?: DataQuality.GOOD,
            source = source,
            metadata = payload.mapValues { it.value?.toString().orEmpty() }
        )
    }

    private suspend fun Any.healthIfComponentAlive(): Boolean? =
        (this as? de.unistuttgart.isw.dtengine.core.DtComponent)?.health()?.alive

    private fun MappingEndpoint.describe(): String = when (this) {
        is GatewayEndpoint -> "gateway:${owner.value}:${dataPointId.value}"
        is ModelEndpoint -> "model:${owner.value}:${propertyId.value}"
        is ServiceEndpoint -> "service:${owner.value}:$inputOrOutput"
        is DatabaseEndpoint -> "database:${owner.value}:$table:$column:${key.orEmpty()}"
    }
}
