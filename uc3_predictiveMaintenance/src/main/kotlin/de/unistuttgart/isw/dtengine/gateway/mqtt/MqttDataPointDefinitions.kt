package de.unistuttgart.isw.dtengine.gateway.mqtt

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.database.DataPointDefinition
import java.time.Instant

fun MqttTopicSpec.toDataPointDefinition(
    machineId: String,
    topicPrefix: String = "",
    sourceComponent: ComponentId? = null
): DataPointDefinition = DataPointDefinition(
    id = dataPointId(machineId),
    sourceComponent = sourceComponent,
    valueType = valueType.name,
    machineId = machineId,
    topic = topic(machineId, topicPrefix),
    description = description,
    metadata = mapOf(
        "machineType" to machineType.name,
        "machineTypeLabel" to machineType.label,
        "port" to port,
        "direction" to direction.name,
        "topicSuffix" to topicSuffix,
        "valueType" to valueType.name
    ),
    createdAt = Instant.now(),
    updatedAt = Instant.now()
)

fun MqttTopicCatalog.toDataPointDefinitions(
    machineType: MachineType,
    machineId: String,
    topicPrefix: String = "",
    sourceComponent: ComponentId? = null,
    includeWritableCommands: Boolean = true
): List<DataPointDefinition> {
    val specs = if (includeWritableCommands) {
        forMachine(machineType)
    } else {
        readable(machineType)
    }
    return specs.map { it.toDataPointDefinition(machineId, topicPrefix, sourceComponent) }
}
