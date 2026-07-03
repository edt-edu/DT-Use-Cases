package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.CorrelationId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.database.DtEventStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

class SqliteEventStore(
    private val jdbcTemplate: JdbcTemplate
) : DtEventStore {

    init {
        SqliteSchema.initialize(jdbcTemplate)
    }

    override suspend fun append(event: DtEvent): DtEvent {
        val now = Instant.now().toString()
        jdbcTemplate.update(
            """
            INSERT INTO dt_events(event_id, event_type, source_component_id, payload_json, timestamp, correlation_id, severity, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(event_id) DO UPDATE SET
                event_type = excluded.event_type,
                source_component_id = excluded.source_component_id,
                payload_json = excluded.payload_json,
                timestamp = excluded.timestamp,
                correlation_id = excluded.correlation_id,
                severity = excluded.severity
            """.trimIndent(),
            event.id.toString(),
            event.type.name,
            event.source.value,
            SqliteJson.write(event.payload),
            event.timestamp.toString(),
            event.correlationId?.value,
            event.severity.name,
            now
        )
        return event
    }

    override suspend fun recent(limit: Int): List<DtEvent> =
        jdbcTemplate.query(
            """
            SELECT * FROM dt_events
            ORDER BY timestamp DESC
            LIMIT ?
            """.trimIndent(),
            eventMapper,
            limit.coerceAtLeast(1)
        )

    override suspend fun byType(type: EventType, limit: Int): List<DtEvent> =
        jdbcTemplate.query(
            """
            SELECT * FROM dt_events
            WHERE event_type = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """.trimIndent(),
            eventMapper,
            type.name,
            limit.coerceAtLeast(1)
        )

    private val eventMapper = RowMapper<DtEvent> { rs, _ -> rs.toEvent() }

    private fun ResultSet.toEvent(): DtEvent = DtEvent(
        id = UUID.fromString(getString("event_id")),
        type = EventType.valueOf(getString("event_type")),
        source = ComponentId(getString("source_component_id")),
        payload = SqliteJson.readAnyMap(getString("payload_json")),
        timestamp = Instant.parse(getString("timestamp")),
        correlationId = getString("correlation_id")?.let { CorrelationId(it) },
        severity = EventSeverity.valueOf(getString("severity"))
    )
}
