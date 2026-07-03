package de.unistuttgart.isw.dtengine.gateway.mqtt

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.database.DtDataStore

/**
 * Registers all MQTT topic catalog entries as known data points in the database.
 *
 * This allows service readiness checks before the first live MQTT value arrives.
 */
class MqttCatalogDataPointRegistrar(
    private val catalog: MqttTopicCatalog = MqttTopicCatalog.default(),
    private val dataStore: DtDataStore
) {
    suspend fun register(
        machineId: String,
        machineType: MachineType,
        topicPrefix: String = "",
        sourceComponent: ComponentId? = null,
        includeWritableCommands: Boolean = true
    ) {
        catalog.toDataPointDefinitions(
            machineType = machineType,
            machineId = machineId,
            topicPrefix = topicPrefix,
            sourceComponent = sourceComponent,
            includeWritableCommands = includeWritableCommands
        ).forEach { dataStore.upsertDataPoint(it) }
    }
}
