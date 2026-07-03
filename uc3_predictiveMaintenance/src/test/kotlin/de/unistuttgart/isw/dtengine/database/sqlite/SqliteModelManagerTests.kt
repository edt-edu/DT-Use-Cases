package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Path

class SqliteModelManagerTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun createsModelPropertyFromGatewayEvent() = runBlocking {
        val manager = newModelManager()
        manager.start()

        val dataPointId = DataPointId("1-1-conveyor.phototransistor-feed-station")
        val emitted = manager.handleGatewayEvent(
            DtEvent(
                type = EventType.GATEWAY_DATA_RECEIVED,
                source = ComponentId("mqtt-gateway"),
                payload = mapOf(
                    "dataPointId" to dataPointId.value,
                    "value" to true,
                    "quality" to "GOOD",
                    "valueType" to "BOOLEAN"
                )
            )
        )

        val property = manager.getProperty(ModelPropertyId(dataPointId.value))

        assertEquals(EventType.MODEL_PROPERTY_CREATED, emitted.single().type)
        assertNotNull(property)
        assertEquals(true, property?.value?.value)
    }

    private fun newModelManager(): SqliteModelManager {
        val dataSource = SQLiteDataSource().apply {
            setUrl("jdbc:sqlite:${tempDir.resolve("model.sqlite")}")
        }
        return SqliteModelManager(jdbcTemplate = JdbcTemplate(dataSource))
    }
}
