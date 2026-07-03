package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.MappingId
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.mapping.DatabaseEndpoint
import de.unistuttgart.isw.dtengine.mapping.DtMapping
import de.unistuttgart.isw.dtengine.mapping.GatewayEndpoint
import de.unistuttgart.isw.dtengine.mapping.MappingDirection
import de.unistuttgart.isw.dtengine.mapping.MappingEndpoint
import de.unistuttgart.isw.dtengine.mapping.ModelEndpoint
import de.unistuttgart.isw.dtengine.mapping.ServiceEndpoint
import de.unistuttgart.isw.dtengine.mapping.TransformationSpec
import de.unistuttgart.isw.dtengine.mapping.TransformationType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant

class SqliteMappingRegistry(
    private val jdbcTemplate: JdbcTemplate
) : AbstractMappingRegistry() {

    init {
        SqliteSchema.initialize(jdbcTemplate)
    }

    override suspend fun all(): List<DtMapping> =
        jdbcTemplate.query("SELECT * FROM mappings ORDER BY mapping_id", mappingMapper)

    override suspend fun findById(id: MappingId): DtMapping? =
        jdbcTemplate.query("SELECT * FROM mappings WHERE mapping_id = ?", mappingMapper, id.value).firstOrNull()

    override suspend fun findForSource(source: MappingEndpoint): List<DtMapping> {
        val encoded = encodeEndpoint(source)
        return jdbcTemplate.query(
            "SELECT * FROM mappings WHERE source_type = ? AND source_owner = ? ORDER BY mapping_id",
            mappingMapper,
            encoded.type,
            encoded.owner
        ).filter { it.source == source }
    }

    override suspend fun findForTarget(target: MappingEndpoint): List<DtMapping> {
        val encoded = encodeEndpoint(target)
        return jdbcTemplate.query(
            "SELECT * FROM mappings WHERE target_type = ? AND target_owner = ? ORDER BY mapping_id",
            mappingMapper,
            encoded.type,
            encoded.owner
        ).filter { it.target == target }
    }

    override suspend fun add(mapping: DtMapping): DtMapping {
        upsert(mapping)
        return mapping
    }

    override suspend fun update(mapping: DtMapping): DtMapping {
        upsert(mapping)
        return mapping
    }

    override suspend fun delete(id: MappingId): Boolean =
        jdbcTemplate.update("DELETE FROM mappings WHERE mapping_id = ?", id.value) > 0

    private fun upsert(mapping: DtMapping) {
        val source = encodeEndpoint(mapping.source)
        val target = encodeEndpoint(mapping.target)
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO mappings(
                mapping_id, source_type, source_owner, source_json,
                target_type, target_owner, target_json,
                direction, transformation_json, enabled, metadata_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(mapping_id) DO UPDATE SET
                source_type = excluded.source_type,
                source_owner = excluded.source_owner,
                source_json = excluded.source_json,
                target_type = excluded.target_type,
                target_owner = excluded.target_owner,
                target_json = excluded.target_json,
                direction = excluded.direction,
                transformation_json = excluded.transformation_json,
                enabled = excluded.enabled,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            mapping.id.value,
            source.type,
            source.owner,
            source.payloadJson,
            target.type,
            target.owner,
            target.payloadJson,
            mapping.direction.name,
            SqliteJson.write(
                mapOf(
                    "type" to mapping.transformation.type.name,
                    "expression" to mapping.transformation.expression,
                    "parameters" to mapping.transformation.parameters
                )
            ),
            if (mapping.enabled) 1 else 0,
            SqliteJson.write(mapping.metadata),
            now,
            now
        )
    }

    private val mappingMapper = RowMapper<DtMapping> { rs, _ -> rs.toMapping() }

    private fun ResultSet.toMapping(): DtMapping = DtMapping(
        id = MappingId(getString("mapping_id")),
        source = decodeEndpoint(
            type = getString("source_type"),
            owner = getString("source_owner"),
            payloadJson = getString("source_json")
        ),
        target = decodeEndpoint(
            type = getString("target_type"),
            owner = getString("target_owner"),
            payloadJson = getString("target_json")
        ),
        direction = MappingDirection.valueOf(getString("direction")),
        transformation = decodeTransformation(getString("transformation_json")),
        enabled = getInt("enabled") != 0,
        metadata = SqliteJson.readStringMap(getString("metadata_json"))
    )

    private fun encodeEndpoint(endpoint: MappingEndpoint): EncodedEndpoint = when (endpoint) {
        is GatewayEndpoint -> EncodedEndpoint(
            type = "GATEWAY",
            owner = endpoint.owner.value,
            payloadJson = SqliteJson.write(mapOf("dataPointId" to endpoint.dataPointId.value))
        )
        is ModelEndpoint -> EncodedEndpoint(
            type = "MODEL",
            owner = endpoint.owner.value,
            payloadJson = SqliteJson.write(mapOf("propertyId" to endpoint.propertyId.value))
        )
        is ServiceEndpoint -> EncodedEndpoint(
            type = "SERVICE",
            owner = endpoint.owner.value,
            payloadJson = SqliteJson.write(mapOf("inputOrOutput" to endpoint.inputOrOutput))
        )
        is DatabaseEndpoint -> EncodedEndpoint(
            type = "DATABASE",
            owner = endpoint.owner.value,
            payloadJson = SqliteJson.write(
                mapOf(
                    "table" to endpoint.table,
                    "column" to endpoint.column,
                    "key" to endpoint.key
                )
            )
        )
    }

    private fun decodeEndpoint(type: String, owner: String, payloadJson: String): MappingEndpoint {
        val payload = SqliteJson.readAnyMap(payloadJson)
        val ownerId = ComponentId(owner)
        return when (type) {
            "GATEWAY" -> GatewayEndpoint(ownerId, DataPointId(payload.requiredString("dataPointId")))
            "MODEL" -> ModelEndpoint(ownerId, ModelPropertyId(payload.requiredString("propertyId")))
            "SERVICE" -> ServiceEndpoint(ownerId, payload.requiredString("inputOrOutput"))
            "DATABASE" -> DatabaseEndpoint(
                owner = ownerId,
                table = payload.requiredString("table"),
                column = payload.requiredString("column"),
                key = payload["key"]?.toString()
            )
            else -> throw IllegalArgumentException("Unsupported mapping endpoint type '$type'")
        }
    }

    private fun decodeTransformation(json: String?): TransformationSpec {
        if (json.isNullOrBlank()) return TransformationSpec.identity()
        val payload = SqliteJson.readAnyMap(json)
        val type = payload["type"]?.toString()?.let { TransformationType.valueOf(it) } ?: TransformationType.IDENTITY
        val expression = payload["expression"]?.toString()
        val parameters = when (val raw = payload["parameters"]) {
            is Map<*, *> -> raw.entries.associate { it.key.toString() to it.value.toString() }
            else -> emptyMap()
        }
        return TransformationSpec(type, expression, parameters)
    }

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key]?.toString() ?: throw IllegalArgumentException("Mapping endpoint payload misses '$key'")

    private data class EncodedEndpoint(
        val type: String,
        val owner: String,
        val payloadJson: String
    )
}
