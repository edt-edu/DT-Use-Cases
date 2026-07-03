package de.unistuttgart.isw.dtengine.service.mapek

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.database.DataPointDefinition
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.StoredDataValue
import de.unistuttgart.isw.dtengine.service.ServiceTickContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.Instant

class ConveyorKpiMapekServiceTests {

    @Test
    fun calculatesWarningStateFromTransportTime() = runBlocking {
        val store = InMemoryDtDataStore()
        val service = ConveyorKpiMapekService(
            config = ConveyorKpiMapekConfig(
                nominalTransportTimeSeconds = 5.0,
                warningIntegrityThresholdPercent = 30.0,
                emergencyIntegrityThresholdPercent = 10.0
            ),
            dataStore = store
        )
        service.start()

        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = start.plusSeconds(20)
        service.handleEvent(gatewayEvent("1-1-conveyor.phototransistor-feed-station", true, start))
        service.handleEvent(gatewayEvent("1-1-conveyor.phototransistor-swap-station", true, end))

        service.tick(ServiceTickContext(cycleId = 1, now = end.plusSeconds(1)))

        val state = store.readLatest(DataPointId("conveyor.state"))
        val integrity = store.readLatest(DataPointId("conveyor.integrity-percent"))
        assertEquals("WARNING", state?.value)
        assertEquals(25.0, integrity?.value as Double, absoluteTolerance = 0.001)
    }

    @Test
    fun calculatesEmergencyStateFromVerySlowTransportTime() = runBlocking {
        val store = InMemoryDtDataStore()
        val service = ConveyorKpiMapekService(
            config = ConveyorKpiMapekConfig(nominalTransportTimeSeconds = 5.0),
            dataStore = store
        )
        service.start()

        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = start.plusSeconds(60)
        service.handleEvent(gatewayEvent("1-1-conveyor.phototransistor-feed-station", true, start))
        service.handleEvent(gatewayEvent("1-1-conveyor.phototransistor-swap-station", true, end))

        val emitted = service.tick(ServiceTickContext(cycleId = 1, now = end.plusSeconds(1)))

        assertEquals("EMERGENCY", store.readLatest(DataPointId("conveyor.state"))?.value)
        assertNotNull(emitted.firstOrNull { it.payload["conveyorState"] == "EMERGENCY" })
    }

    private fun gatewayEvent(dataPointId: String, value: Boolean, timestamp: Instant): DtEvent = DtEvent(
        type = EventType.GATEWAY_DATA_RECEIVED,
        source = ComponentId("mqtt-gateway"),
        timestamp = timestamp,
        payload = mapOf(
            "dataPointId" to dataPointId,
            "value" to value,
            "quality" to "GOOD"
        )
    )
}

private class InMemoryDtDataStore : DtDataStore {
    private val definitions = linkedMapOf<DataPointId, DataPointDefinition>()
    private val latest = linkedMapOf<DataPointId, DataValue>()
    private val history = linkedMapOf<DataPointId, MutableList<StoredDataValue>>()
    private var sequence = 0L

    override suspend fun upsertDataPoint(definition: DataPointDefinition): DataPointDefinition {
        definitions[definition.id] = definition
        return definition
    }

    override suspend fun getDataPoint(id: DataPointId): DataPointDefinition? = definitions[id]

    override suspend fun knownDataPoints(): Set<DataPointId> = definitions.keys + latest.keys

    override suspend fun writeValue(value: DataValue): DataValue {
        latest[value.id] = value
        definitions.putIfAbsent(value.id, value.toDefinition())
        history.getOrPut(value.id) { mutableListOf() }.add(StoredDataValue(++sequence, value))
        return value
    }

    override suspend fun readLatest(id: DataPointId): DataValue? = latest[id]

    override suspend fun readLatest(ids: Set<DataPointId>): List<DataValue> = ids.mapNotNull { latest[it] }

    override suspend fun history(id: DataPointId, limit: Int): List<StoredDataValue> =
        history[id].orEmpty().takeLast(limit).asReversed()

    override suspend fun deleteDataPoint(id: DataPointId): Boolean = definitions.remove(id) != null || latest.remove(id) != null
}

private fun DataValue.toDefinition(): DataPointDefinition = DataPointDefinition(
    id = id,
    sourceComponent = source,
    valueType = metadata["valueType"] ?: value?.let { it::class.simpleName },
    metadata = metadata
)
