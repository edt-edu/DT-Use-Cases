package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.model.aas.AasProperty
import de.unistuttgart.isw.dtengine.model.aas.AasPropertyAddress
import de.unistuttgart.isw.dtengine.model.aas.AasRepositoryClient
import de.unistuttgart.isw.dtengine.model.aas.AasRepositoryHealth
import de.unistuttgart.isw.dtengine.model.aas.AasShellDescriptor
import de.unistuttgart.isw.dtengine.model.aas.AasSubmodelDescriptor
import de.unistuttgart.isw.dtengine.model.aas.AasValueType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLite-backed AAS repository adapter.
 *
 * This adapter keeps the AAS model manager persistent without binding the DT
 * engine to a concrete BaSyx repository yet. Later, the same AasRepositoryClient
 * boundary can be implemented by a BaSyx client while the database still keeps
 * service registries, mappings, event logs, and runtime values.
 */
class SqliteAasRepositoryClient(
    private val jdbcTemplate: JdbcTemplate
) : AasRepositoryClient {

    private val running = AtomicBoolean(false)

    override suspend fun start() {
        SqliteSchema.initialize(jdbcTemplate)
        running.set(true)
    }

    override suspend fun stop() {
        running.set(false)
    }

    override suspend fun health(): AasRepositoryHealth {
        val alive = runCatching { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 }
            .getOrDefault(false)
        return AasRepositoryHealth(
            alive = alive && running.get(),
            message = when {
                !alive -> "SQLite AAS repository is not reachable"
                running.get() -> "SQLite AAS repository is running"
                else -> "SQLite AAS repository is reachable but stopped"
            },
            details = mapOf(
                "shells" to runCatching { count("aas_shells") }.getOrDefault(-1).toString(),
                "submodels" to runCatching { count("aas_submodels") }.getOrDefault(-1).toString(),
                "properties" to runCatching { count("aas_properties") }.getOrDefault(-1).toString()
            )
        )
    }

    override suspend fun ensureShell(shell: AasShellDescriptor) {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO aas_shells(id, id_short, description, submodel_ids_json, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                id_short = excluded.id_short,
                description = excluded.description,
                submodel_ids_json = excluded.submodel_ids_json,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            shell.id,
            shell.idShort,
            shell.description,
            SqliteJson.write(shell.submodelIds.sorted()),
            SqliteJson.write(shell.metadata),
            now,
            now
        )
        shell.submodelIds.forEach { submodelId ->
            jdbcTemplate.update(
                """
                INSERT OR IGNORE INTO aas_shell_submodels(shell_id, submodel_id, created_at)
                SELECT ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM aas_submodels WHERE id = ?)
                """.trimIndent(),
                shell.id,
                submodelId,
                now,
                submodelId
            )
        }
    }

    override suspend fun ensureSubmodel(shellId: String, submodel: AasSubmodelDescriptor) {
        require(shellExists(shellId)) { "AAS shell '$shellId' does not exist" }
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO aas_submodels(id, id_short, semantic_id, description, metadata_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                id_short = excluded.id_short,
                semantic_id = excluded.semantic_id,
                description = excluded.description,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            submodel.id,
            submodel.idShort,
            submodel.semanticId,
            submodel.description,
            SqliteJson.write(submodel.metadata),
            now,
            now
        )
        jdbcTemplate.update(
            """
            INSERT OR IGNORE INTO aas_shell_submodels(shell_id, submodel_id, created_at)
            VALUES (?, ?, ?)
            """.trimIndent(),
            shellId,
            submodel.id,
            now
        )
        refreshShellSubmodelList(shellId)
    }

    override suspend fun getProperty(address: AasPropertyAddress): AasProperty? = jdbcTemplate.query(
        "SELECT * FROM aas_properties WHERE external_id = ?",
        propertyMapper,
        address.externalId
    ).firstOrNull()

    override suspend fun upsertProperty(property: AasProperty): AasProperty {
        require(shellExists(property.address.shellId)) {
            "AAS shell '${property.address.shellId}' does not exist"
        }
        require(shellContainsSubmodel(property.address.shellId, property.address.submodelId)) {
            "AAS submodel '${property.address.submodelId}' is not attached to shell '${property.address.shellId}'"
        }
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO aas_properties(
                external_id, shell_id, submodel_id, id_short_path_json, id_short,
                value_json, value_type, semantic_id, category, description,
                observed_at, metadata_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(external_id) DO UPDATE SET
                shell_id = excluded.shell_id,
                submodel_id = excluded.submodel_id,
                id_short_path_json = excluded.id_short_path_json,
                id_short = excluded.id_short,
                value_json = excluded.value_json,
                value_type = excluded.value_type,
                semantic_id = excluded.semantic_id,
                category = excluded.category,
                description = excluded.description,
                observed_at = excluded.observed_at,
                metadata_json = excluded.metadata_json,
                updated_at = excluded.updated_at
            """.trimIndent(),
            property.address.externalId,
            property.address.shellId,
            property.address.submodelId,
            SqliteJson.write(property.address.idShortPath),
            property.idShort,
            SqliteJson.write(property.value),
            property.valueType.name,
            property.semanticId,
            property.category,
            property.description,
            property.observedAt.toString(),
            SqliteJson.write(property.metadata),
            now,
            now
        )
        return property
    }

    override suspend fun deleteProperty(address: AasPropertyAddress): Boolean =
        jdbcTemplate.update("DELETE FROM aas_properties WHERE external_id = ?", address.externalId) > 0

    override suspend fun findPropertyByMetadata(key: String, value: String): AasProperty? =
        listProperties().firstOrNull { it.metadata[key] == value }

    override suspend fun listProperties(shellId: String?, submodelId: String?): List<AasProperty> {
        val sql = buildString {
            append("SELECT * FROM aas_properties")
            val conditions = mutableListOf<String>()
            if (shellId != null) conditions += "shell_id = ?"
            if (submodelId != null) conditions += "submodel_id = ?"
            if (conditions.isNotEmpty()) append(" WHERE ").append(conditions.joinToString(" AND "))
            append(" ORDER BY external_id")
        }
        val args = listOfNotNull(shellId, submodelId).toTypedArray()
        return jdbcTemplate.query(sql, propertyMapper, *args)
    }

    suspend fun getShell(shellId: String): AasShellDescriptor? = jdbcTemplate.query(
        "SELECT * FROM aas_shells WHERE id = ?",
        shellMapper,
        shellId
    ).firstOrNull()

    suspend fun getSubmodel(submodelId: String): AasSubmodelDescriptor? = jdbcTemplate.query(
        "SELECT * FROM aas_submodels WHERE id = ?",
        submodelMapper,
        submodelId
    ).firstOrNull()

    private fun shellExists(shellId: String): Boolean = (jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM aas_shells WHERE id = ?",
        Int::class.java,
        shellId
    ) ?: 0) != 0

    private fun shellContainsSubmodel(shellId: String, submodelId: String): Boolean = (jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM aas_shell_submodels WHERE shell_id = ? AND submodel_id = ?",
        Int::class.java,
        shellId,
        submodelId
    ) ?: 0) != 0

    private fun refreshShellSubmodelList(shellId: String) {
        val submodelIds = jdbcTemplate.query(
            "SELECT submodel_id FROM aas_shell_submodels WHERE shell_id = ? ORDER BY submodel_id",
            { rs, _ -> rs.getString("submodel_id") },
            shellId
        )
        jdbcTemplate.update(
            "UPDATE aas_shells SET submodel_ids_json = ?, updated_at = ? WHERE id = ?",
            SqliteJson.write(submodelIds),
            Instant.now().toString(),
            shellId
        )
    }

    private fun count(table: String): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java) ?: 0

    private val propertyMapper = RowMapper<AasProperty> { rs, _ -> rs.toAasProperty() }
    private val shellMapper = RowMapper<AasShellDescriptor> { rs, _ -> rs.toAasShellDescriptor() }
    private val submodelMapper = RowMapper<AasSubmodelDescriptor> { rs, _ -> rs.toAasSubmodelDescriptor() }

    private fun ResultSet.toAasProperty(): AasProperty {
        val idShortPath = SqliteJson.readStringList(getString("id_short_path_json"))
        val address = AasPropertyAddress(
            shellId = getString("shell_id"),
            submodelId = getString("submodel_id"),
            idShortPath = idShortPath
        )
        return AasProperty(
            address = address,
            idShort = getString("id_short"),
            value = SqliteJson.readAny(getString("value_json")),
            valueType = getString("value_type")?.let { runCatching { AasValueType.valueOf(it) }.getOrNull() }
                ?: AasValueType.UNKNOWN,
            semanticId = getString("semantic_id"),
            category = getString("category"),
            description = getString("description") ?: "",
            observedAt = Instant.parse(getString("observed_at")),
            metadata = SqliteJson.readStringMap(getString("metadata_json"))
        )
    }

    private fun ResultSet.toAasShellDescriptor(): AasShellDescriptor = AasShellDescriptor(
        id = getString("id"),
        idShort = getString("id_short"),
        description = getString("description") ?: "",
        submodelIds = SqliteJson.readStringSet(getString("submodel_ids_json")),
        metadata = SqliteJson.readStringMap(getString("metadata_json"))
    )

    private fun ResultSet.toAasSubmodelDescriptor(): AasSubmodelDescriptor = AasSubmodelDescriptor(
        id = getString("id"),
        idShort = getString("id_short"),
        semanticId = getString("semantic_id"),
        description = getString("description") ?: "",
        metadata = SqliteJson.readStringMap(getString("metadata_json"))
    )
}
