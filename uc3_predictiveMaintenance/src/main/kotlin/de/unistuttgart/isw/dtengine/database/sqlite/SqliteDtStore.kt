package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.DataPointDefinition
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.ServiceDataPointRequirement
import de.unistuttgart.isw.dtengine.database.ServiceDescriptor
import de.unistuttgart.isw.dtengine.database.ServiceFunctionRequirement
import de.unistuttgart.isw.dtengine.database.ServiceModelRequirement
import de.unistuttgart.isw.dtengine.database.ServiceRequirementStore
import de.unistuttgart.isw.dtengine.database.ServiceRegistryStore
import de.unistuttgart.isw.dtengine.database.ServiceStartCheck
import de.unistuttgart.isw.dtengine.database.StoredDataValue
import de.unistuttgart.isw.dtengine.database.qualityFromString
import de.unistuttgart.isw.dtengine.database.toDefinition
import de.unistuttgart.isw.dtengine.database.toServiceDescriptor
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.ServiceType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * SQLite-backed runtime data store.
 *
 * This component is both the data-point dictionary and the value history store.
 * It also stores service requirements so services can be checked before startup.
 */
class SqliteDtStore(
    override val id: ComponentId = ComponentId("sqlite-db"),
    override val description: String = "SQLite DT data store",
    private val jdbcTemplate: JdbcTemplate
) : de.unistuttgart.isw.dtengine.core.DtComponent, DtDataStore, ServiceRequirementStore, ServiceRegistryStore {

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
            message = if (alive) "SQLite database reachable" else "SQLite database not reachable",
            details = mapOf(
                "knownDataPoints" to runCatching { count("data_points") }.getOrDefault(-1).toString(),
                "storedValues" to runCatching { count("data_values") }.getOrDefault(-1).toString(),
                "modelProperties" to runCatching { count("model_properties") }.getOrDefault(-1).toString(),
                "aasShells" to runCatching { count("aas_shells") }.getOrDefault(-1).toString(),
                "aasSubmodels" to runCatching { count("aas_submodels") }.getOrDefault(-1).toString(),
                "aasProperties" to runCatching { count("aas_properties") }.getOrDefault(-1).toString(),
                "serviceRequirements" to runCatching { count("service_requirements") }.getOrDefault(-1).toString(),
                "registeredServices" to runCatching { count("service_registry") }.getOrDefault(-1).toString()
            )
        )
    }

    override suspend fun upsertDataPoint(definition: DataPointDefinition): DataPointDefinition {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO data_points(id, source_component_id, value_type, machine_id, topic, description, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                source_component_id = excluded.source_component_id,
                value_type = excluded.value_type,
                machine_id = excluded.machine_id,
                topic = excluded.topic,
                description = excluded.description,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            definition.id.value,
            definition.sourceComponent?.value,
            definition.valueType,
            definition.machineId,
            definition.topic,
            definition.description,
            SqliteJson.write(definition.metadata),
            definition.createdAt.toString(),
            now
        )
        return definition.copy(updatedAt = Instant.parse(now))
    }

    override suspend fun getDataPoint(id: DataPointId): DataPointDefinition? =
        jdbcTemplate.query(
            "SELECT * FROM data_points WHERE id = ?",
            dataPointMapper,
            id.value
        ).firstOrNull()

    override suspend fun knownDataPoints(): Set<DataPointId> =
        jdbcTemplate.query("SELECT id FROM data_points") { rs, _ -> DataPointId(rs.getString("id")) }.toSet()

    override suspend fun writeValue(value: DataValue): DataValue {
        upsertDataPoint(value.toDefinition())
        jdbcTemplate.update(
            """
            INSERT INTO data_values(data_point_id, value_json, value_type, quality, source_component_id, timestamp, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            value.id.value,
            SqliteJson.write(value.value),
            value.metadata["valueType"] ?: value.value?.let { it::class.simpleName },
            value.quality.name,
            value.source?.value,
            value.timestamp.toString(),
            SqliteJson.write(value.metadata)
        )
        return value
    }

    override suspend fun readLatest(id: DataPointId): DataValue? =
        jdbcTemplate.query(
            """
            SELECT * FROM data_values
            WHERE data_point_id = ?
            ORDER BY timestamp DESC, sequence_id DESC
            LIMIT 1
            """.trimIndent(),
            dataValueMapper,
            id.value
        ).firstOrNull()

    override suspend fun readLatest(ids: Set<DataPointId>): List<DataValue> =
        ids.mapNotNull { readLatest(it) }

    override suspend fun history(id: DataPointId, limit: Int): List<StoredDataValue> =
        jdbcTemplate.query(
            """
            SELECT * FROM data_values
            WHERE data_point_id = ?
            ORDER BY timestamp DESC, sequence_id DESC
            LIMIT ?
            """.trimIndent(),
            storedValueMapper,
            id.value,
            limit.coerceAtLeast(1)
        )

    override suspend fun deleteDataPoint(id: DataPointId): Boolean =
        jdbcTemplate.update("DELETE FROM data_points WHERE id = ?", id.value) > 0

    override suspend fun registerRequirement(requirement: ServiceDataPointRequirement): ServiceDataPointRequirement {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO service_requirements(service_id, data_point_id, required, description, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(service_id, data_point_id) DO UPDATE SET
                required = excluded.required,
                description = excluded.description,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            requirement.serviceId.value,
            requirement.dataPointId.value,
            if (requirement.required) 1 else 0,
            requirement.description,
            SqliteJson.write(requirement.metadata),
            now,
            now
        )
        return requirement
    }

    override suspend fun registerRequirements(serviceId: ServiceId, dataPointIds: Set<DataPointId>) {
        dataPointIds.forEach { registerRequirement(ServiceDataPointRequirement(serviceId, it)) }
    }

    override suspend fun registerService(service: AbstractDtService) {
        registerServiceDescriptor(service.toServiceDescriptor())
        registerRequirements(service.serviceId, service.requiredDataPoints)
        registerModelRequirements(service.serviceId, service.requiredModelProperties)
        registerFunctionRequirements(service.serviceId, service.requiredFunctions)
    }

    override suspend fun requirements(serviceId: ServiceId): List<ServiceDataPointRequirement> =
        jdbcTemplate.query(
            "SELECT * FROM service_requirements WHERE service_id = ? ORDER BY data_point_id",
            requirementMapper,
            serviceId.value
        )

    override suspend fun removeRequirement(serviceId: ServiceId, dataPointId: DataPointId): Boolean =
        jdbcTemplate.update(
            "DELETE FROM service_requirements WHERE service_id = ? AND data_point_id = ?",
            serviceId.value,
            dataPointId.value
        ) > 0

    override suspend fun checkServiceReadiness(serviceId: ServiceId): ServiceStartCheck {
        val required = requirements(serviceId).filter { it.required }.map { it.dataPointId }.toSet()
        val available = knownDataPoints()
        return ServiceStartCheck(
            serviceId = serviceId,
            ready = required.all { it in available },
            requiredDataPoints = required,
            availableDataPoints = available,
            missingDataPoints = required.filterNot { it in available }.toSet()
        )
    }

    override suspend fun checkServiceReadiness(service: AbstractDtService): ServiceStartCheck {
        if (requirements(service.serviceId).isEmpty()) {
            registerService(service)
        }
        val dbCheck = checkServiceReadiness(service.serviceId)
        val allRequired = dbCheck.requiredDataPoints + service.requiredDataPoints
        val missing = allRequired.filterNot { it in dbCheck.availableDataPoints }.toSet()
        return dbCheck.copy(
            requiredDataPoints = allRequired,
            ready = missing.isEmpty(),
            missingDataPoints = missing
        )
    }

    override suspend fun registerServiceDescriptor(descriptor: ServiceDescriptor): ServiceDescriptor {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO service_registry(
                service_id, component_id, service_type, description,
                required_data_points_json, required_model_properties_json, required_functions_json,
                produced_data_points_json, metadata_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(service_id) DO UPDATE SET
                component_id = excluded.component_id,
                service_type = excluded.service_type,
                description = excluded.description,
                required_data_points_json = excluded.required_data_points_json,
                required_model_properties_json = excluded.required_model_properties_json,
                required_functions_json = excluded.required_functions_json,
                produced_data_points_json = excluded.produced_data_points_json,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            descriptor.serviceId.value,
            descriptor.componentId.value,
            descriptor.serviceType.name,
            descriptor.description,
            SqliteJson.write(descriptor.requiredDataPoints.map { it.value }.sorted()),
            SqliteJson.write(descriptor.requiredModelProperties.map { it.value }.sorted()),
            SqliteJson.write(descriptor.requiredFunctions.sorted()),
            SqliteJson.write(descriptor.producedDataPoints.map { it.value }.sorted()),
            SqliteJson.write(descriptor.metadata),
            descriptor.createdAt.toString(),
            now
        )
        descriptor.requiredDataPoints.forEach { registerRequirement(ServiceDataPointRequirement(descriptor.serviceId, it)) }
        descriptor.requiredModelProperties.forEach { registerModelRequirement(ServiceModelRequirement(descriptor.serviceId, it)) }
        descriptor.requiredFunctions.forEach { registerFunctionRequirement(ServiceFunctionRequirement(descriptor.serviceId, it)) }
        return descriptor.copy(updatedAt = Instant.parse(now))
    }

    override suspend fun serviceDescriptor(serviceId: ServiceId): ServiceDescriptor? =
        jdbcTemplate.query(
            "SELECT * FROM service_registry WHERE service_id = ?",
            serviceDescriptorMapper,
            serviceId.value
        ).firstOrNull()

    override suspend fun serviceDescriptors(): List<ServiceDescriptor> =
        jdbcTemplate.query("SELECT * FROM service_registry ORDER BY service_id", serviceDescriptorMapper)

    override suspend fun registerModelRequirement(requirement: ServiceModelRequirement): ServiceModelRequirement {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO service_model_requirements(service_id, model_property_id, required, description, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(service_id, model_property_id) DO UPDATE SET
                required = excluded.required,
                description = excluded.description,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            requirement.serviceId.value,
            requirement.modelPropertyId.value,
            if (requirement.required) 1 else 0,
            requirement.description,
            SqliteJson.write(requirement.metadata),
            now,
            now
        )
        return requirement
    }

    override suspend fun registerModelRequirements(serviceId: ServiceId, modelPropertyIds: Set<ModelPropertyId>) {
        modelPropertyIds.forEach { registerModelRequirement(ServiceModelRequirement(serviceId, it)) }
    }

    override suspend fun modelRequirements(serviceId: ServiceId): List<ServiceModelRequirement> =
        jdbcTemplate.query(
            "SELECT * FROM service_model_requirements WHERE service_id = ? ORDER BY model_property_id",
            modelRequirementMapper,
            serviceId.value
        )

    override suspend fun registerFunctionRequirement(requirement: ServiceFunctionRequirement): ServiceFunctionRequirement {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO service_function_requirements(service_id, function_name, required, description, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(service_id, function_name) DO UPDATE SET
                required = excluded.required,
                description = excluded.description,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            requirement.serviceId.value,
            requirement.functionName,
            if (requirement.required) 1 else 0,
            requirement.description,
            SqliteJson.write(requirement.metadata),
            now,
            now
        )
        return requirement
    }

    override suspend fun registerFunctionRequirements(serviceId: ServiceId, functionNames: Set<String>) {
        functionNames.filter { it.isNotBlank() }.forEach { registerFunctionRequirement(ServiceFunctionRequirement(serviceId, it)) }
    }

    override suspend fun functionRequirements(serviceId: ServiceId): List<ServiceFunctionRequirement> =
        jdbcTemplate.query(
            "SELECT * FROM service_function_requirements WHERE service_id = ? ORDER BY function_name",
            functionRequirementMapper,
            serviceId.value
        )

    override suspend fun removeServiceDescriptor(serviceId: ServiceId): Boolean {
        jdbcTemplate.update("DELETE FROM service_model_requirements WHERE service_id = ?", serviceId.value)
        jdbcTemplate.update("DELETE FROM service_function_requirements WHERE service_id = ?", serviceId.value)
        jdbcTemplate.update("DELETE FROM service_requirements WHERE service_id = ?", serviceId.value)
        return jdbcTemplate.update("DELETE FROM service_registry WHERE service_id = ?", serviceId.value) > 0
    }

    private fun count(table: String): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java) ?: 0

    private val dataPointMapper = RowMapper<DataPointDefinition> { rs, _ -> rs.toDataPointDefinition() }
    private val dataValueMapper = RowMapper<DataValue> { rs, _ -> rs.toDataValue() }
    private val storedValueMapper = RowMapper<StoredDataValue> { rs, _ ->
        StoredDataValue(rs.getLong("sequence_id"), rs.toDataValue())
    }
    private val requirementMapper = RowMapper<ServiceDataPointRequirement> { rs, _ ->
        ServiceDataPointRequirement(
            serviceId = ServiceId(rs.getString("service_id")),
            dataPointId = DataPointId(rs.getString("data_point_id")),
            required = rs.getInt("required") != 0,
            description = rs.getString("description") ?: "",
            metadata = SqliteJson.readStringMap(rs.getString("metadata_json"))
        )
    }
    private val serviceDescriptorMapper = RowMapper<ServiceDescriptor> { rs, _ -> rs.toServiceDescriptor() }
    private val modelRequirementMapper = RowMapper<ServiceModelRequirement> { rs, _ ->
        ServiceModelRequirement(
            serviceId = ServiceId(rs.getString("service_id")),
            modelPropertyId = ModelPropertyId(rs.getString("model_property_id")),
            required = rs.getInt("required") != 0,
            description = rs.getString("description") ?: "",
            metadata = SqliteJson.readStringMap(rs.getString("metadata_json"))
        )
    }
    private val functionRequirementMapper = RowMapper<ServiceFunctionRequirement> { rs, _ ->
        ServiceFunctionRequirement(
            serviceId = ServiceId(rs.getString("service_id")),
            functionName = rs.getString("function_name"),
            required = rs.getInt("required") != 0,
            description = rs.getString("description") ?: "",
            metadata = SqliteJson.readStringMap(rs.getString("metadata_json"))
        )
    }

    private fun ResultSet.toDataPointDefinition(): DataPointDefinition = DataPointDefinition(
        id = DataPointId(getString("id")),
        sourceComponent = getString("source_component_id")?.let { ComponentId(it) },
        valueType = getString("value_type"),
        machineId = getString("machine_id"),
        topic = getString("topic"),
        description = getString("description") ?: "",
        metadata = SqliteJson.readStringMap(getString("metadata_json")),
        createdAt = Instant.parse(getString("created_at")),
        updatedAt = Instant.parse(getString("updated_at"))
    )

    private fun ResultSet.toServiceDescriptor(): ServiceDescriptor = ServiceDescriptor(
        serviceId = ServiceId(getString("service_id")),
        componentId = ComponentId(getString("component_id")),
        serviceType = getString("service_type")?.let { runCatching { ServiceType.valueOf(it) }.getOrNull() } ?: ServiceType.CUSTOM,
        description = getString("description") ?: "",
        requiredDataPoints = SqliteJson.readStringSet(getString("required_data_points_json")).map { DataPointId(it) }.toSet(),
        requiredModelProperties = SqliteJson.readStringSet(getString("required_model_properties_json")).map { ModelPropertyId(it) }.toSet(),
        requiredFunctions = SqliteJson.readStringSet(getString("required_functions_json")),
        producedDataPoints = SqliteJson.readStringSet(getString("produced_data_points_json")).map { DataPointId(it) }.toSet(),
        metadata = SqliteJson.readStringMap(getString("metadata_json")),
        createdAt = Instant.parse(getString("created_at")),
        updatedAt = Instant.parse(getString("updated_at"))
    )

    private fun ResultSet.toDataValue(): DataValue = DataValue(
        id = DataPointId(getString("data_point_id")),
        value = SqliteJson.readAny(getString("value_json")),
        timestamp = Instant.parse(getString("timestamp")),
        quality = qualityFromString(getString("quality")),
        source = getString("source_component_id")?.let { ComponentId(it) },
        metadata = SqliteJson.readStringMap(getString("metadata_json"))
    )
}
