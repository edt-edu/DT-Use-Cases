package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.ServiceDataPointRequirement
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Path

class SqliteDtStoreTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun storesAndReadsLatestValue() = runBlocking {
        val store = newStore()
        store.start()

        val dataPointId = DataPointId("1-1-conveyor.phototransistor-feed-station")
        store.writeValue(
            DataValue(
                id = dataPointId,
                value = true,
                source = ComponentId("mqtt-gateway"),
                metadata = mapOf("machineId" to "1-1-conveyor", "valueType" to "BOOLEAN")
            )
        )

        val latest = store.readLatest(dataPointId)

        assertNotNull(latest)
        assertEquals(true, latest?.value)
        assertTrue(dataPointId in store.knownDataPoints())
        assertEquals(1, store.history(dataPointId, limit = 10).size)
    }

    @Test
    fun checksServiceRequirementsAgainstKnownDataPoints() = runBlocking {
        val store = newStore()
        store.start()

        val required = DataPointId("1-1-conveyor.phototransistor-feed-station")
        val missing = DataPointId("1-1-conveyor.phototransistor-swap-station")
        val serviceId = ServiceId("monitoring-service")

        store.registerRequirement(ServiceDataPointRequirement(serviceId, required))
        store.registerRequirement(ServiceDataPointRequirement(serviceId, missing))
        store.writeValue(DataValue(id = required, value = true))

        val check = store.checkServiceReadiness(serviceId)

        assertFalse(check.ready)
        assertEquals(setOf(missing), check.missingDataPoints)
    }

    private fun newStore(): SqliteDtStore {
        val dataSource = SQLiteDataSource().apply {
            setUrl("jdbc:sqlite:${tempDir.resolve("test.sqlite")}")
        }
        return SqliteDtStore(jdbcTemplate = JdbcTemplate(dataSource))
    }
}
