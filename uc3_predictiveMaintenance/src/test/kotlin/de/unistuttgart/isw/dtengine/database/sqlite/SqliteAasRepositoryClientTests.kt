package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.model.aas.AasProperty
import de.unistuttgart.isw.dtengine.model.aas.AasPropertyAddress
import de.unistuttgart.isw.dtengine.model.aas.AasShellDescriptor
import de.unistuttgart.isw.dtengine.model.aas.AasSubmodelDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Path

class SqliteAasRepositoryClientTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun persistsShellSubmodelAndProperty() = runBlocking {
        val repository = newRepository()
        repository.start()

        repository.ensureShell(
            AasShellDescriptor(
                id = "dt-engine-aas",
                idShort = "DTEngineAas",
                submodelIds = setOf("dt-engine-aas:data")
            )
        )
        repository.ensureSubmodel(
            shellId = "dt-engine-aas",
            submodel = AasSubmodelDescriptor(
                id = "dt-engine-aas:data",
                idShort = "Data"
            )
        )
        val property = repository.upsertProperty(
            AasProperty(
                address = AasPropertyAddress(
                    shellId = "dt-engine-aas",
                    submodelId = "dt-engine-aas:data",
                    idShortPath = listOf("Conveyor", "State")
                ),
                value = "NORMAL",
                metadata = mapOf(
                    "modelPropertyId" to "conveyor.state",
                    "dataPointId" to "conveyor.state"
                )
            )
        )

        val loaded = repository.getProperty(property.address)
        val byMetadata = repository.findPropertyByMetadata("modelPropertyId", "conveyor.state")
        val shell = repository.getShell("dt-engine-aas")

        assertNotNull(loaded)
        assertEquals("NORMAL", loaded?.value)
        assertEquals(property.address.externalId, byMetadata?.address?.externalId)
        assertTrue(shell?.submodelIds?.contains("dt-engine-aas:data") == true)
        assertEquals(1, repository.listProperties(shellId = "dt-engine-aas").size)
    }

    private fun newRepository(): SqliteAasRepositoryClient {
        val dataSource = SQLiteDataSource().apply {
            setUrl("jdbc:sqlite:${tempDir.resolve("aas-test.sqlite")}")
        }
        return SqliteAasRepositoryClient(JdbcTemplate(dataSource))
    }
}
