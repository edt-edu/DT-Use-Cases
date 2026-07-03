package de.unistuttgart.isw.dtengine.gateway.mqtt

import de.unistuttgart.isw.dtengine.core.DataPointId

enum class MachineType(val label: String) {
    VACUUM_GRIPPER("Vacuum Gripper"),
    MULTIPROCESSING_STATION("Multiprocessing Station"),
    THREE_D_GRIPPER("3D-Gripper"),
    CONVEYOR_BELT("Conveyor Belt"),
    HIGH_BAY("High Bay"),
    PUNCHING_MACHINE("Punching Machine"),
    SORTING_LINE("Sorting Line"),
    INDEXED_LINE("Indexed Line");

    companion object {
        fun parse(value: String): MachineType {
            val normalized = value
                .trim()
                .replace("3D", "THREE_D", ignoreCase = true)
                .replace("-", "_")
                .replace(" ", "_")
                .uppercase()

            return entries.firstOrNull { it.name == normalized }
                ?: entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported machine type: $value")
        }
    }
}

enum class MqttPortDirection {
    /** Topic is published by the CPS and read by the DT gateway. */
    OUT,

    /** Topic is written by the DT gateway as command to the CPS. */
    IN
}

enum class MqttValueType {
    BOOLEAN,
    INTEGER,
    STRING
}

data class MqttTopicSpec(
    val machineType: MachineType,
    val description: String,
    val port: String,
    val direction: MqttPortDirection,
    val valueType: MqttValueType,
    val topicSuffix: String
) {
    fun topic(machineId: String, topicPrefix: String = ""): String {
        val parts = listOf(topicPrefix, machineId, topicSuffix)
            .map { it.trim('/') }
            .filter { it.isNotBlank() }
        return parts.joinToString(separator = "/", prefix = "/")
    }

    fun dataPointId(machineId: String): DataPointId = DataPointId("$machineId.$topicSuffix")
}
