package de.unistuttgart.isw.dtengine.mapping

import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttGatewayProperties
import de.unistuttgart.isw.dtengine.service.mapek.ConveyorKpiMapekConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultMappingSeederTests {
    @Test
    fun `seeds mqtt and mapek mappings`() = runBlocking {
        val registry = InMemoryMappingRegistry()
        val mqtt = MqttGatewayProperties().apply {
            machineId = "1-1-conveyor"
            machineType = "CONVEYOR_BELT"
        }
        val seeder = DefaultMappingSeeder(
            registry = registry,
            properties = MappingProperties(),
            mqttProperties = mqtt,
            conveyorConfig = ConveyorKpiMapekConfig(enabled = true)
        )

        val seeded = seeder.seed()

        assertTrue(seeded.any { it.id.value == "mqtt:1-1-conveyor.phototransistor-feed-station:to-db" })
        assertTrue(seeded.any { it.id.value == "mqtt:1-1-conveyor.phototransistor-feed-station:to-aas" })
        assertTrue(seeded.any { it.id.value == "service:conveyor-kpi-mapek:conveyor.state:to-db" })
        assertTrue(registry.all().size == seeded.size)
    }
}
