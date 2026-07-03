package de.unistuttgart.isw.dtengine.model.aas

import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import kotlin.test.Test
import kotlin.test.assertEquals

class AasPathCodecTests {
    @Test
    fun `sanitizes mqtt-like topic suffixes to aas idShorts`() {
        assertEquals("phototransistor_feed_station", AasPathCodec.sanitizeIdShort("phototransistor-feed-station"))
        assertEquals("p_1_1_conveyor", AasPathCodec.sanitizeIdShort("1-1-conveyor"))
    }

    @Test
    fun `converts model property id to aas address`() {
        val address = AasPathCodec.addressForModelPropertyId(
            propertyId = ModelPropertyId("1-1-conveyor.phototransistor-feed-station"),
            shellId = "shell",
            defaultSubmodelId = "submodel"
        )

        assertEquals("shell", address.shellId)
        assertEquals("submodel", address.submodelId)
        assertEquals(listOf("p_1_1_conveyor", "phototransistor_feed_station"), address.idShortPath)
    }
}
