package de.unistuttgart.isw.dtengine.model.aas

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.database.qualityFromString
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.model.ModelMutation
import de.unistuttgart.isw.dtengine.model.ModelOperation
import de.unistuttgart.isw.dtengine.model.ModelProperty
import de.unistuttgart.isw.dtengine.model.ValidationResult
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * AAS-oriented model manager for the DT engine.
 *
 * The manager maps DT model properties to AAS SubmodelElement-like properties.
 * It currently uses the repository interface in this package. The default Spring
 * configuration wires an in-memory repository, while a future BaSyx adapter can
 * implement the same repository boundary.
 */
class AasModelManager(
    override val id: ComponentId = ComponentId("aas-model-manager"),
    override val description: String = "AAS-backed model manager",
    private val repository: AasRepositoryClient = InMemoryAasRepositoryClient(),
    private val shellId: String = "dt-engine-aas",
    private val shellIdShort: String = "DTEngineAas",
    private val dataSubmodelId: String = "dt-engine-aas:data",
    private val dataSubmodelIdShort: String = "Data",
    private val stateSubmodelId: String = "dt-engine-aas:state",
    private val stateSubmodelIdShort: String = "State",
    private val defaultParentPath: String? = null,
    private val autoCreatePropertiesFromGateway: Boolean = true
) : AbstractModelManager() {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)

    override val status: ComponentStatus
        get() = statusRef.get()

    override suspend fun start() {
        statusRef.set(ComponentStatus.STARTING)
        repository.start()
        repository.ensureShell(
            AasShellDescriptor(
                id = shellId,
                idShort = shellIdShort,
                description = "Digital Twin AAS managed by the DT engine",
                submodelIds = setOf(dataSubmodelId, stateSubmodelId),
                metadata = mapOf("componentId" to id.value)
            )
        )
        repository.ensureSubmodel(
            shellId = shellId,
            submodel = AasSubmodelDescriptor(
                id = dataSubmodelId,
                idShort = dataSubmodelIdShort,
                description = "Runtime data and model properties"
            )
        )
        repository.ensureSubmodel(
            shellId = shellId,
            submodel = AasSubmodelDescriptor(
                id = stateSubmodelId,
                idShort = stateSubmodelIdShort,
                description = "Derived DT state and service outputs"
            )
        )
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPING)
        repository.stop()
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus {
        val repositoryHealth = repository.health()
        return HealthStatus(
            alive = repositoryHealth.alive,
            status = if (repositoryHealth.alive) status else ComponentStatus.FAILED,
            message = repositoryHealth.message,
            details = repositoryHealth.details + mapOf(
                "shellId" to shellId,
                "dataSubmodelId" to dataSubmodelId,
                "stateSubmodelId" to stateSubmodelId
            )
        )
    }

    override suspend fun validateSyntax(candidate: ModelMutation): ValidationResult {
        val errors = mutableListOf<String>()
        val property = candidate.property
        if (property.id.value.isBlank()) errors += "property id must not be blank"
        if (candidate.operation != ModelOperation.DELETE && property.name.isBlank()) errors += "property name must not be blank"

        val address = runCatching { addressFor(property) }.getOrElse {
            errors += it.message ?: "invalid AAS address"
            null
        }
        address?.idShortPath?.forEach { idShort ->
            if (!idShort.first().isLetter()) errors += "AAS idShort '$idShort' must start with a letter"
            if (!idShort.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))) {
                errors += "AAS idShort '$idShort' contains unsupported characters"
            }
        }
        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override suspend fun validateConformance(candidate: ModelMutation): ValidationResult {
        val property = candidate.property
        val declaredType = property.type ?: return ValidationResult(valid = true)
        val value = property.value?.value ?: return ValidationResult(valid = true)
        val valid = when (AasValueType.fromModelType(declaredType)) {
            AasValueType.BOOLEAN -> value is Boolean
            AasValueType.INTEGER -> value is Int
            AasValueType.LONG -> value is Long || value is Int
            AasValueType.DOUBLE -> value is Number
            AasValueType.STRING -> value is String
            AasValueType.JSON -> value is Map<*, *> || value is List<*>
            AasValueType.UNKNOWN -> true
        }
        return if (valid) {
            ValidationResult(valid = true)
        } else {
            ValidationResult(
                valid = false,
                errors = listOf("value of '${property.id.value}' does not conform to declared AAS type '$declaredType'")
            )
        }
    }

    override suspend fun getProperty(propertyId: ModelPropertyId): ModelProperty? {
        val byModelPropertyId = repository.findPropertyByMetadata("modelPropertyId", propertyId.value)
        val aasProperty = byModelPropertyId ?: repository.getProperty(addressFor(propertyId))
        return aasProperty?.toModelProperty(propertyId)
    }

    override suspend fun createProperty(property: ModelProperty): ModelProperty {
        val mutation = ModelMutation(ModelOperation.CREATE, property)
        val syntax = validateSyntax(mutation)
        require(syntax.valid) { syntax.errors.joinToString() }
        val conformance = validateConformance(mutation)
        require(conformance.valid) { conformance.errors.joinToString() }

        val address = addressFor(property)
        val metadata = property.metadata + mapOf(
            "modelPropertyId" to property.id.value,
            "aasShellId" to address.shellId,
            "aasSubmodelId" to address.submodelId,
            "aasIdShortPath" to address.path,
            "aasExternalId" to address.externalId
        ) + (property.value?.id?.value?.let { mapOf("dataPointId" to it) } ?: emptyMap()) +
            (property.value?.quality?.name?.let { mapOf("quality" to it) } ?: emptyMap()) +
            (property.value?.source?.value?.let { mapOf("sourceComponentId" to it) } ?: emptyMap())

        repository.upsertProperty(
            AasProperty(
                address = address,
                idShort = address.idShort,
                value = property.value?.value,
                valueType = AasValueType.fromModelType(property.type).takeIf { it != AasValueType.UNKNOWN }
                    ?: AasValueType.fromValue(property.value?.value),
                semanticId = property.semanticId,
                description = property.name,
                observedAt = property.value?.timestamp ?: Instant.now(),
                metadata = metadata
            )
        )
        return property.copy(metadata = metadata)
    }

    override suspend fun updateProperty(propertyId: ModelPropertyId, value: DataValue): ModelProperty {
        val existing = getProperty(propertyId)
        val property = existing?.copy(value = value) ?: ModelProperty(
            id = propertyId,
            name = propertyId.value.substringAfterLast('.'),
            value = value,
            type = value.metadata["valueType"] ?: value.value?.let { it::class.simpleName },
            semanticId = value.metadata["semanticId"],
            parentPath = value.metadata["aasParentPath"] ?: defaultParentPath,
            metadata = mapOf("dataPointId" to value.id.value)
        )
        return createProperty(property)
    }

    override suspend fun deleteProperty(propertyId: ModelPropertyId): Boolean {
        val property = repository.findPropertyByMetadata("modelPropertyId", propertyId.value)
        return if (property != null) {
            repository.deleteProperty(property.address)
        } else {
            repository.deleteProperty(addressFor(propertyId))
        }
    }

    override suspend fun resolvePropertyId(dataPointOrExternalId: String): ModelPropertyId? {
        if (dataPointOrExternalId.isBlank()) return null
        repository.findPropertyByMetadata("modelPropertyId", dataPointOrExternalId)?.let {
            return ModelPropertyId(it.metadata.getValue("modelPropertyId"))
        }
        repository.findPropertyByMetadata("dataPointId", dataPointOrExternalId)?.let {
            return ModelPropertyId(it.metadata["modelPropertyId"] ?: dataPointOrExternalId)
        }
        repository.findPropertyByMetadata("aasExternalId", dataPointOrExternalId)?.let {
            return ModelPropertyId(it.metadata["modelPropertyId"] ?: dataPointOrExternalId)
        }
        val candidate = ModelPropertyId(dataPointOrExternalId)
        return if (repository.getProperty(addressFor(candidate)) != null) candidate else null
    }

    override suspend fun handleGatewayEvent(event: DtEvent): List<DtEvent> {
        if (event.type != EventType.GATEWAY_DATA_RECEIVED) return emptyList()
        val dataPointId = event.payload["dataPointId"]?.toString()
            ?: return listOf(errorEvent("Gateway event does not contain dataPointId", event))
        val propertyId = resolvePropertyId(dataPointId) ?: if (autoCreatePropertiesFromGateway) {
            AasPathCodec.dataPointIdToModelPropertyId(dataPointId)
        } else {
            return listOf(errorEvent("No AAS property found for data point '$dataPointId'", event))
        }
        val value = DataValue(
            id = DataPointId(dataPointId),
            value = event.payload["value"],
            timestamp = event.timestamp,
            quality = event.payload["quality"]?.toString()?.let { qualityFromString(it) } ?: DataQuality.GOOD,
            source = event.source,
            metadata = event.payload.mapValues { it.value?.toString().orEmpty() }
        )
        val existed = getProperty(propertyId) != null
        val property = updateProperty(propertyId, value)
        return listOf(
            DtEvent(
                type = if (existed) EventType.MODEL_PROPERTY_UPDATED else EventType.MODEL_PROPERTY_CREATED,
                source = id,
                correlationId = event.correlationId,
                payload = mapOf(
                    "propertyId" to property.id.value,
                    "dataPointId" to dataPointId,
                    "value" to value.value,
                    "quality" to value.quality.name,
                    "aasShellId" to (property.metadata["aasShellId"] ?: shellId),
                    "aasSubmodelId" to (property.metadata["aasSubmodelId"] ?: dataSubmodelId),
                    "aasIdShortPath" to property.metadata["aasIdShortPath"]
                )
            )
        )
    }

    override suspend fun buildServiceAnswerEvent(requestEvent: DtEvent): DtEvent {
        val requested = requestEvent.payload["propertyId"]?.toString()
            ?: requestEvent.payload["dataPointId"]?.toString()
            ?: requestEvent.payload["aasExternalId"]?.toString()
            ?: requestEvent.payload["id"]?.toString()
        val propertyId = requested?.let { resolvePropertyId(it) ?: ModelPropertyId(it) }
        val property = propertyId?.let { getProperty(it) }
        return DtEvent(
            type = EventType.SERVICE_ANSWER,
            source = id,
            correlationId = requestEvent.correlationId,
            payload = mapOf(
                "requestEventId" to requestEvent.id.toString(),
                "propertyId" to (property?.id?.value ?: requested),
                "found" to (property != null),
                "value" to property?.value?.value,
                "quality" to property?.value?.quality?.name,
                "timestamp" to property?.value?.timestamp?.toString(),
                "aasShellId" to property?.metadata?.get("aasShellId"),
                "aasSubmodelId" to property?.metadata?.get("aasSubmodelId"),
                "aasIdShortPath" to property?.metadata?.get("aasIdShortPath")
            )
        )
    }

    private fun addressFor(property: ModelProperty): AasPropertyAddress = AasPathCodec.addressForProperty(
        property = property,
        shellId = shellId,
        defaultSubmodelId = property.metadata["aasSubmodelId"] ?: chooseDefaultSubmodel(property.id.value),
        defaultParentPath = defaultParentPath
    )

    private fun addressFor(propertyId: ModelPropertyId): AasPropertyAddress = AasPathCodec.addressForModelPropertyId(
        propertyId = propertyId,
        shellId = shellId,
        defaultSubmodelId = chooseDefaultSubmodel(propertyId.value),
        defaultParentPath = defaultParentPath
    )

    private fun chooseDefaultSubmodel(id: String): String = if (
        id.contains("state", ignoreCase = true) ||
        id.contains("integrity", ignoreCase = true) ||
        id.contains("productivity", ignoreCase = true) ||
        id.contains("transport-time", ignoreCase = true)
    ) {
        stateSubmodelId
    } else {
        dataSubmodelId
    }

    private fun AasProperty.toModelProperty(requestedPropertyId: ModelPropertyId? = null): ModelProperty {
        val propertyId = requestedPropertyId ?: metadata["modelPropertyId"]?.let { ModelPropertyId(it) }
            ?: ModelPropertyId(address.path.replace('/', '.'))
        val dataPointId = metadata["dataPointId"] ?: propertyId.value
        return ModelProperty(
            id = propertyId,
            name = description.ifBlank { idShort },
            value = DataValue(
                id = DataPointId(dataPointId),
                value = value,
                timestamp = observedAt,
                quality = metadata["quality"]?.let { qualityFromString(it) } ?: DataQuality.GOOD,
                source = metadata["sourceComponentId"]?.let { ComponentId(it) },
                metadata = metadata
            ),
            type = valueType.name,
            semanticId = semanticId,
            parentPath = address.idShortPath.dropLast(1).joinToString("/").ifBlank { null },
            metadata = metadata
        )
    }

    private fun errorEvent(message: String, requestEvent: DtEvent): DtEvent = DtEvent(
        type = EventType.ERROR,
        source = id,
        severity = EventSeverity.ERROR,
        correlationId = requestEvent.correlationId,
        payload = mapOf("message" to message, "requestEventId" to requestEvent.id.toString())
    )
}
