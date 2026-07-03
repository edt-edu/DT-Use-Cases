package de.unistuttgart.isw.dtengine.database.sqlite

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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * SQLite-backed model manager.
 *
 * It stores model properties in the local database and can consume gateway events
 * by updating/creating the mapped property. This is intentionally independent of
 * AAS/BaSyx; the AAS-backed manager can be added later behind the same abstraction.
 */
class SqliteModelManager(
    override val id: ComponentId = ComponentId("sqlite-model-manager"),
    override val description: String = "SQLite-backed model manager",
    private val jdbcTemplate: JdbcTemplate,
    private val autoCreatePropertiesFromGateway: Boolean = true
) : AbstractModelManager() {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)

    override val status: ComponentStatus
        get() = statusRef.get()

    override suspend fun start() {
        statusRef.set(ComponentStatus.STARTING)
        SqliteSchema.initialize(jdbcTemplate)
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus {
        val alive = runCatching { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 }
            .getOrDefault(false)
        return HealthStatus(
            alive = alive,
            status = if (alive) status else ComponentStatus.FAILED,
            message = if (alive) "SQLite model manager reachable" else "SQLite model manager not reachable",
            details = mapOf("modelProperties" to runCatching { propertyCount().toString() }.getOrDefault("unknown"))
        )
    }

    override suspend fun validateSyntax(candidate: ModelMutation): ValidationResult {
        val errors = buildList {
            if (candidate.property.id.value.isBlank()) add("property id must not be blank")
            if (candidate.operation != ModelOperation.DELETE && candidate.property.name.isBlank()) add("property name must not be blank")
        }
        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    override suspend fun validateConformance(candidate: ModelMutation): ValidationResult {
        val property = candidate.property
        val declaredType = property.type ?: return ValidationResult(valid = true)
        val value = property.value?.value ?: return ValidationResult(valid = true)
        val valid = when (declaredType.lowercase()) {
            "boolean", "bool" -> value is Boolean
            "integer", "int", "long" -> value is Int || value is Long
            "double", "float", "number" -> value is Number
            "string", "text" -> value is String
            else -> true
        }
        return if (valid) {
            ValidationResult(valid = true)
        } else {
            ValidationResult(
                valid = false,
                errors = listOf("value of '${property.id.value}' does not conform to declared type '$declaredType'")
            )
        }
    }

    override suspend fun getProperty(propertyId: ModelPropertyId): ModelProperty? =
        jdbcTemplate.query(
            "SELECT * FROM model_properties WHERE property_id = ?",
            propertyMapper,
            propertyId.value
        ).firstOrNull()

    override suspend fun createProperty(property: ModelProperty): ModelProperty {
        val mutation = ModelMutation(ModelOperation.CREATE, property)
        val syntax = validateSyntax(mutation)
        require(syntax.valid) { syntax.errors.joinToString() }
        val conformance = validateConformance(mutation)
        require(conformance.valid) { conformance.errors.joinToString() }

        val now = Instant.now().toString()
        val dataPointId = property.metadata["dataPointId"]
        val value = property.value
        jdbcTemplate.update(
            """
            INSERT INTO model_properties(
                property_id, name, data_point_id, value_json, value_type, value_quality,
                value_timestamp, value_source_component_id, value_metadata_json,
                declared_type, semantic_id, parent_path, metadata_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(property_id) DO UPDATE SET
                name = excluded.name,
                data_point_id = excluded.data_point_id,
                value_json = excluded.value_json,
                value_type = excluded.value_type,
                value_quality = excluded.value_quality,
                value_timestamp = excluded.value_timestamp,
                value_source_component_id = excluded.value_source_component_id,
                value_metadata_json = excluded.value_metadata_json,
                declared_type = excluded.declared_type,
                semantic_id = excluded.semantic_id,
                parent_path = excluded.parent_path,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            property.id.value,
            property.name,
            dataPointId,
            SqliteJson.write(value?.value),
            value?.metadata?.get("valueType") ?: value?.value?.let { it::class.simpleName },
            value?.quality?.name,
            value?.timestamp?.toString(),
            value?.source?.value,
            SqliteJson.write(value?.metadata ?: emptyMap<String, String>()),
            property.type,
            property.semanticId,
            property.parentPath,
            SqliteJson.write(property.metadata),
            now,
            now
        )
        return property
    }

    override suspend fun updateProperty(propertyId: ModelPropertyId, value: DataValue): ModelProperty {
        val existing = getProperty(propertyId)
        val property = existing?.copy(value = value) ?: ModelProperty(
            id = propertyId,
            name = propertyId.value,
            value = value,
            type = value.metadata["valueType"] ?: value.value?.let { it::class.simpleName },
            metadata = mapOf("dataPointId" to value.id.value)
        )
        return createProperty(property)
    }

    override suspend fun deleteProperty(propertyId: ModelPropertyId): Boolean =
        jdbcTemplate.update("DELETE FROM model_properties WHERE property_id = ?", propertyId.value) > 0

    override suspend fun resolvePropertyId(dataPointOrExternalId: String): ModelPropertyId? =
        jdbcTemplate.query(
            """
            SELECT property_id FROM model_properties
            WHERE property_id = ? OR data_point_id = ? OR name = ?
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> ModelPropertyId(rs.getString("property_id")) },
            dataPointOrExternalId,
            dataPointOrExternalId,
            dataPointOrExternalId
        ).firstOrNull()

    override suspend fun handleGatewayEvent(event: DtEvent): List<DtEvent> {
        if (event.type != EventType.GATEWAY_DATA_RECEIVED) return emptyList()
        val dataPointId = event.payload["dataPointId"]?.toString()
            ?: return listOf(errorEvent("Gateway event does not contain dataPointId", event))
        val propertyId = resolvePropertyId(dataPointId) ?: if (autoCreatePropertiesFromGateway) {
            ModelPropertyId(dataPointId)
        } else {
            return listOf(errorEvent("No model property found for data point '$dataPointId'", event))
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
                    "quality" to value.quality.name
                )
            )
        )
    }

    override suspend fun buildServiceAnswerEvent(requestEvent: DtEvent): DtEvent {
        val requested = requestEvent.payload["propertyId"]?.toString()
            ?: requestEvent.payload["dataPointId"]?.toString()
            ?: requestEvent.payload["id"]?.toString()
        val property = requested?.let { resolvePropertyId(it) ?: ModelPropertyId(it) }?.let { getProperty(it) }
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
                "timestamp" to property?.value?.timestamp?.toString()
            )
        )
    }

    private fun errorEvent(message: String, requestEvent: DtEvent): DtEvent = DtEvent(
        type = EventType.ERROR,
        source = id,
        severity = EventSeverity.ERROR,
        correlationId = requestEvent.correlationId,
        payload = mapOf("message" to message, "requestEventId" to requestEvent.id.toString())
    )

    private fun propertyCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM model_properties", Int::class.java) ?: 0

    private val propertyMapper = RowMapper<ModelProperty> { rs, _ -> rs.toModelProperty() }

    private fun ResultSet.toModelProperty(): ModelProperty {
        val valueTimestamp = getString("value_timestamp")
        val dataPointId = getString("data_point_id")
        val value = if (valueTimestamp == null && getString("value_json") == null) {
            null
        } else {
            DataValue(
                id = DataPointId(dataPointId ?: getString("property_id")),
                value = SqliteJson.readAny(getString("value_json")),
                timestamp = valueTimestamp?.let { Instant.parse(it) } ?: Instant.now(),
                quality = qualityFromString(getString("value_quality")),
                source = getString("value_source_component_id")?.let { ComponentId(it) },
                metadata = SqliteJson.readStringMap(getString("value_metadata_json"))
            )
        }
        return ModelProperty(
            id = ModelPropertyId(getString("property_id")),
            name = getString("name"),
            value = value,
            type = getString("declared_type"),
            semanticId = getString("semantic_id"),
            parentPath = getString("parent_path"),
            metadata = SqliteJson.readStringMap(getString("metadata_json"))
        )
    }
}
