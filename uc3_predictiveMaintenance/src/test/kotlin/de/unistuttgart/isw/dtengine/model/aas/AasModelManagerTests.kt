package de.unistuttgart.isw.dtengine.model.aas

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.model.ModelProperty
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AasModelManagerTests {
    @Test
    fun `creates and reads aas model property`() = runBlocking {
        val manager = AasModelManager()
        manager.start()

        manager.createProperty(
            ModelProperty(
                id = ModelPropertyId("conveyor.state"),
                name = "Conveyor State",
                value = DataValue(
                    id = DataPointId("conveyor.state"),
                    value = "NORMAL",
                    source = ComponentId("test")
                ),
                type = "STRING"
            )
        )

        val property = manager.getProperty(ModelPropertyId("conveyor.state"))
        assertNotNull(property)
        assertEquals("NORMAL", property.value?.value)
        assertTrue(property.metadata["aasExternalId"]?.contains("dt-engine-aas") == true)
    }

    @Test
    fun `handles gateway data event by creating aas property`() = runBlocking {
        val manager = AasModelManager()
        manager.start()

        val resultEvents = manager.handleGatewayEvent(
            DtEvent(
                type = EventType.GATEWAY_DATA_RECEIVED,
                source = ComponentId("mqtt-gateway"),
                payload = mapOf(
                    "dataPointId" to "1-1-conveyor.phototransistor-feed-station",
                    "value" to true,
                    "quality" to "GOOD"
                )
            )
        )

        assertEquals(EventType.MODEL_PROPERTY_CREATED, resultEvents.single().type)
        val property = manager.getProperty(ModelPropertyId("1-1-conveyor.phototransistor-feed-station"))
        assertNotNull(property)
        assertEquals(true, property.value?.value)
        assertEquals("1-1-conveyor.phototransistor-feed-station", manager.resolvePropertyId("1-1-conveyor.phototransistor-feed-station")?.value)
    }

    @Test
    fun `builds service answer from aas property`() = runBlocking {
        val manager = AasModelManager()
        manager.start()
        manager.updateProperty(
            propertyId = ModelPropertyId("conveyor.integrity-percent"),
            value = DataValue(
                id = DataPointId("conveyor.integrity-percent"),
                value = 42.0,
                source = ComponentId("conveyor-kpi-mapek-service")
            )
        )

        val answer = manager.buildServiceAnswerEvent(
            DtEvent(
                type = EventType.SERVICE_REQUEST,
                source = ComponentId("ui"),
                payload = mapOf("propertyId" to "conveyor.integrity-percent")
            )
        )

        assertEquals(EventType.SERVICE_ANSWER, answer.type)
        assertEquals(true, answer.payload["found"])
        assertEquals(42.0, answer.payload["value"])
        assertEquals("dt-engine-aas:state", answer.payload["aasSubmodelId"])
    }
}
