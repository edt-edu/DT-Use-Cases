package de.unistuttgart.isw.dtengine.gateway.mqtt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MqttTopicCatalogTests {
    private val catalog = MqttTopicCatalog.default()

    @Test
    fun conveyorBeltContainsReadableAndWritableTopics() {
        val readable = catalog.readable(MachineType.CONVEYOR_BELT)
        val writable = catalog.writable(MachineType.CONVEYOR_BELT)

        assertEquals(3, readable.size)
        assertEquals(2, writable.size)
        assertTrue(writable.any { it.topicSuffix == "move-conveyor-forward" })
    }

    @Test
    fun topicCanBeResolvedToSpec() {
        val spec = catalog.byTopic(
            machineType = MachineType.CONVEYOR_BELT,
            machineId = "1-1-conveyor",
            topicPrefix = "",
            topic = "/1-1-conveyor/phototransistor-feed-station"
        )

        assertNotNull(spec)
        assertEquals("I1", spec.port)
        assertEquals(MqttValueType.BOOLEAN, spec.valueType)
    }

    @Test
    fun topicPrefixIsSupported() {
        val spec = catalog.byTopic(
            machineType = MachineType.PUNCHING_MACHINE,
            machineId = "3-3-punchingMachine",
            topicPrefix = "/factory/cell-a",
            topic = "/factory/cell-a/3-3-punchingMachine/switch-punching-machine-up"
        )

        assertNotNull(spec)
        assertEquals("switch-punching-machine-up", spec.topicSuffix)
    }
}
