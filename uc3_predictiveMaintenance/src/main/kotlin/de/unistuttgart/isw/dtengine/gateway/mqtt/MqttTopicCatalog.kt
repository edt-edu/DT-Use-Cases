package de.unistuttgart.isw.dtengine.gateway.mqtt

/**
 * Catalog of MQTT topic definitions for the fischertechnik station types.
 *
 * Direction follows the machine perspective from the station table:
 * - OUT: values produced by the machine/CPS and received by the DT gateway
 * - IN: commands produced by the DT gateway and consumed by the machine/CPS
 */
class MqttTopicCatalog(
    private val topics: List<MqttTopicSpec> = defaultTopics
) {
    fun all(): List<MqttTopicSpec> = topics

    fun forMachine(machineType: MachineType): List<MqttTopicSpec> =
        topics.filter { it.machineType == machineType }

    fun readable(machineType: MachineType): List<MqttTopicSpec> =
        forMachine(machineType).filter { it.direction == MqttPortDirection.OUT }

    fun writable(machineType: MachineType): List<MqttTopicSpec> =
        forMachine(machineType).filter { it.direction == MqttPortDirection.IN }

    fun bySuffix(machineType: MachineType, suffix: String): MqttTopicSpec? {
        val normalized = suffix.trim().trim('/')
        return forMachine(machineType).firstOrNull { it.topicSuffix == normalized }
    }

    fun byDataPointId(machineType: MachineType, machineId: String, dataPointId: String): MqttTopicSpec? {
        val normalized = dataPointId.trim()
        return forMachine(machineType).firstOrNull {
            it.dataPointId(machineId).value == normalized || it.topicSuffix == normalized.trim('/')
        }
    }

    fun byTopic(machineType: MachineType, machineId: String, topicPrefix: String, topic: String): MqttTopicSpec? {
        val suffix = extractSuffix(machineId, topicPrefix, topic) ?: return null
        return bySuffix(machineType, suffix)
    }

    fun extractSuffix(machineId: String, topicPrefix: String, topic: String): String? {
        val topicParts = topic.trim().trim('/').split('/').filter { it.isNotBlank() }
        val prefixParts = topicPrefix.trim().trim('/').split('/').filter { it.isNotBlank() }
        val expectedPrefix = prefixParts + machineId.trim('/')

        if (topicParts.size <= expectedPrefix.size) return null
        if (topicParts.take(expectedPrefix.size) != expectedPrefix) return null
        return topicParts.drop(expectedPrefix.size).joinToString("/")
    }

    companion object {
        fun default(): MqttTopicCatalog = MqttTopicCatalog(defaultTopics)

        private fun t(
            machineType: MachineType,
            description: String,
            port: String,
            direction: MqttPortDirection,
            valueType: MqttValueType,
            topicSuffix: String
        ): MqttTopicSpec = MqttTopicSpec(
            machineType = machineType,
            description = description,
            port = port,
            direction = direction,
            valueType = valueType,
            topicSuffix = topicSuffix.trim('/')
        )

        val defaultTopics: List<MqttTopicSpec> = listOf(
            // Vacuum Gripper
            t(MachineType.VACUUM_GRIPPER, "reference switch vertical axis", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-vertical"),
            t(MachineType.VACUUM_GRIPPER, "reference switch horizontal axis", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-horizontal"),
            t(MachineType.VACUUM_GRIPPER, "reference switch rotate", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-rotation"),
            t(MachineType.VACUUM_GRIPPER, "encoder vertical axis impulse 1", "B1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-vertical-enc-1"),
            t(MachineType.VACUUM_GRIPPER, "encoder vertical axis impulse 2", "B2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-vertical-enc-2"),
            t(MachineType.VACUUM_GRIPPER, "encoder horizontal axis impulse 1", "B3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-horizontal-enc-1"),
            t(MachineType.VACUUM_GRIPPER, "encoder horizontal axis impulse 2", "B4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-horizontal-enc-2"),
            t(MachineType.VACUUM_GRIPPER, "encoder rotate impulse 1", "B5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-rotation-enc-1"),
            t(MachineType.VACUUM_GRIPPER, "encoder rotate impulse 2", "B6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-rotation-enc-2"),
            t(MachineType.VACUUM_GRIPPER, "vertical position", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "motor-vertical-pos"),
            t(MachineType.VACUUM_GRIPPER, "horizontal position", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "motor-horizontal-pos"),
            t(MachineType.VACUUM_GRIPPER, "rotation position", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "motor-rotation-pos"),
            t(MachineType.VACUUM_GRIPPER, "motor vertical axis up", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-up"),
            t(MachineType.VACUUM_GRIPPER, "motor vertical axis down", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-down"),
            t(MachineType.VACUUM_GRIPPER, "motor horizontal axis backward", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-horizontal-up"),
            t(MachineType.VACUUM_GRIPPER, "motor horizontal axis forward", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-horizontal-down"),
            t(MachineType.VACUUM_GRIPPER, "motor rotate clockwise", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-rotation-clockwise"),
            t(MachineType.VACUUM_GRIPPER, "motor rotate counterclockwise", "Q6", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-rotation-counterclockwise"),
            t(MachineType.VACUUM_GRIPPER, "compressor", "Q7", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-compressor"),
            t(MachineType.VACUUM_GRIPPER, "valve vacuum", "Q8", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve"),

            // Multiprocessing Station
            t(MachineType.MULTIPROCESSING_STATION, "reference switch turn-table position vacuum", "I1", MqttPortDirection.OUT, MqttValueType.INTEGER, "ref-switch-rotation-at-vacuum"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch turn-table position belt", "I2", MqttPortDirection.OUT, MqttValueType.INTEGER, "ref-switch-rotation-at-belt"),
            t(MachineType.MULTIPROCESSING_STATION, "light-barrier end of conveyor belt", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-conveyor-end"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch turn-table position saw", "I4", MqttPortDirection.OUT, MqttValueType.INTEGER, "ref-switch-turn-table-at-saw"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch vacuum position turn-table", "I5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-vacuum-at-turn-table"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch oven feeder inside", "I6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-feeder-inside"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch oven feeder outside", "I7", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-feeder-outside"),
            t(MachineType.MULTIPROCESSING_STATION, "reference switch vacuum position oven", "I8", MqttPortDirection.OUT, MqttValueType.INTEGER, "ref-switch-vacuum-at-oven"),
            t(MachineType.MULTIPROCESSING_STATION, "light-barrier oven", "I9", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-oven"),
            t(MachineType.MULTIPROCESSING_STATION, "motor turn-table clockwise", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-turn-table-clockwise"),
            t(MachineType.MULTIPROCESSING_STATION, "motor turn-table counterclockwise", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-turn-table-counterclockwise"),
            t(MachineType.MULTIPROCESSING_STATION, "motor conveyor belt forward", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-converyor-forward"),
            t(MachineType.MULTIPROCESSING_STATION, "motor saw", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-saw"),
            t(MachineType.MULTIPROCESSING_STATION, "motor oven feeder retract", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-oven-feeder-retract"),
            t(MachineType.MULTIPROCESSING_STATION, "motor oven feeder extend", "Q6", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-oven-feeder-extend"),
            t(MachineType.MULTIPROCESSING_STATION, "motor vacuum towards oven", "Q7", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vacuum-to-oven"),
            t(MachineType.MULTIPROCESSING_STATION, "motor vacuum towards turn-table", "Q8", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vacuum-to-turn-table"),
            t(MachineType.MULTIPROCESSING_STATION, "light oven", "Q9", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-oven-light"),
            t(MachineType.MULTIPROCESSING_STATION, "compressor", "Q10", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-compressor"),
            t(MachineType.MULTIPROCESSING_STATION, "valve vacuum", "Q11", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-vacuum"),
            t(MachineType.MULTIPROCESSING_STATION, "valve lowering", "Q12", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-move-vacuum"),
            t(MachineType.MULTIPROCESSING_STATION, "valve oven door", "Q13", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-move-oven-door"),
            t(MachineType.MULTIPROCESSING_STATION, "valve feeder", "Q14", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-move-feeder"),

            // 3D-Gripper
            t(MachineType.THREE_D_GRIPPER, "reference switch claw", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-claw"),
            t(MachineType.THREE_D_GRIPPER, "pulse counter gripper", "I2", MqttPortDirection.OUT, MqttValueType.INTEGER, "enc-counter-claw"),
            t(MachineType.THREE_D_GRIPPER, "reference switch grip arm", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-gripper"),
            t(MachineType.THREE_D_GRIPPER, "pulse counter grip arm", "I4", MqttPortDirection.OUT, MqttValueType.INTEGER, "enc-counter-gripper"),
            t(MachineType.THREE_D_GRIPPER, "reference switch vertical axis", "I5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-vertical"),
            t(MachineType.THREE_D_GRIPPER, "reference switch turntable", "I6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-rotation"),
            t(MachineType.THREE_D_GRIPPER, "encoder vertical axis impulse 1", "B1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-vertical-enc-1"),
            t(MachineType.THREE_D_GRIPPER, "encoder vertical axis impulse 2", "B2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-vertical-enc-2"),
            t(MachineType.THREE_D_GRIPPER, "encoder turntable impulse 1", "B3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-rotation-enc-1"),
            t(MachineType.THREE_D_GRIPPER, "encoder turntable impulse 2", "B4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "motor-rotation-enc-2"),
            t(MachineType.THREE_D_GRIPPER, "position vertical", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "motor-vertical-pos"),
            t(MachineType.THREE_D_GRIPPER, "position rotation", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "motor-rotation-pos"),
            t(MachineType.THREE_D_GRIPPER, "motor gripper open", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "open-gripper"),
            t(MachineType.THREE_D_GRIPPER, "motor gripper close", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "close-gripper"),
            t(MachineType.THREE_D_GRIPPER, "motor grip arm forward", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-horizontal-forward"),
            t(MachineType.THREE_D_GRIPPER, "motor grip arm back", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-horizontal-backward"),
            t(MachineType.THREE_D_GRIPPER, "motor vertical axis down", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-up"),
            t(MachineType.THREE_D_GRIPPER, "motor vertical axis up", "Q6", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-down"),
            t(MachineType.THREE_D_GRIPPER, "motor turntable right", "Q7", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-rotation-clockwise"),
            t(MachineType.THREE_D_GRIPPER, "motor turntable left", "Q8", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-rotation-counterclockwise"),

            // Conveyor Belt
            t(MachineType.CONVEYOR_BELT, "phototransistor feed station", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-feed-station"),
            t(MachineType.CONVEYOR_BELT, "phototransistor swap station", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-swap-station"),
            t(MachineType.CONVEYOR_BELT, "pulse button", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "pulse-button"),
            t(MachineType.CONVEYOR_BELT, "motor conveyor belt forward", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-forward"),
            t(MachineType.CONVEYOR_BELT, "motor conveyor belt backward", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-backward"),

            // High Bay
            t(MachineType.HIGH_BAY, "reference switch horizontal axis", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-horizontal"),
            t(MachineType.HIGH_BAY, "light-barrier inside", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-inside"),
            t(MachineType.HIGH_BAY, "light-barrier outside", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-outside"),
            t(MachineType.HIGH_BAY, "reference switch vertical axis", "I4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-vertical"),
            t(MachineType.HIGH_BAY, "trail sensor lower", "A1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "trail-sensor-lower"),
            t(MachineType.HIGH_BAY, "trail sensor upper", "A2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "trail-sensor-upper"),
            t(MachineType.HIGH_BAY, "encoder horizontal impulse 1", "B1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "horizontal-enc-1"),
            t(MachineType.HIGH_BAY, "encoder horizontal impulse 2", "B2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "horizontal-enc-2"),
            t(MachineType.HIGH_BAY, "encoder vertical impulse 1", "B3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "vertical-enc-1"),
            t(MachineType.HIGH_BAY, "encoder vertical impulse 2", "B4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "vertical-enc-2"),
            t(MachineType.HIGH_BAY, "reference switch cantilever front", "I5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-cantilever-front"),
            t(MachineType.HIGH_BAY, "reference switch cantilever back", "I6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "ref-switch-cantilever-back"),
            t(MachineType.HIGH_BAY, "position vertical", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "vertical-pos"),
            t(MachineType.HIGH_BAY, "position horizontal", "Virtual", MqttPortDirection.OUT, MqttValueType.INTEGER, "horizontal-pos"),
            t(MachineType.HIGH_BAY, "motor conveyor belt forward", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-forward"),
            t(MachineType.HIGH_BAY, "motor conveyor belt backward", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-backward"),
            t(MachineType.HIGH_BAY, "motor horizontal towards rack", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-arm-to-rack"),
            t(MachineType.HIGH_BAY, "motor horizontal towards conveyor belt", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-arm-to-conveyor"),
            t(MachineType.HIGH_BAY, "motor vertical axis downward", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-down"),
            t(MachineType.HIGH_BAY, "motor vertical axis upward", "Q6", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-vertical-up"),
            t(MachineType.HIGH_BAY, "motor cantilever forward", "Q7", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-cantilever-forward"),
            t(MachineType.HIGH_BAY, "motor cantilever backward", "Q8", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-cantilever-backward"),

            // Punching Machine
            t(MachineType.PUNCHING_MACHINE, "phototransistor goods in/out", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-goods-in-out"),
            t(MachineType.PUNCHING_MACHINE, "phototransistor punching machine", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-punching-machine"),
            t(MachineType.PUNCHING_MACHINE, "switch punching machine up", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "switch-punching-machine-up"),
            t(MachineType.PUNCHING_MACHINE, "switch punching machine down", "I4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "switch-punching-machine-down"),
            t(MachineType.PUNCHING_MACHINE, "motor conveyor belt forward", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-forward"),
            t(MachineType.PUNCHING_MACHINE, "motor conveyor belt backward", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-backward"),
            t(MachineType.PUNCHING_MACHINE, "motor punching machine up", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-punching-machine-up"),
            t(MachineType.PUNCHING_MACHINE, "motor punching machine down", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-punching-machine-down"),

            // Sorting Line
            t(MachineType.SORTING_LINE, "pulse counter", "I1", MqttPortDirection.OUT, MqttValueType.INTEGER, "pulse-counter"),
            t(MachineType.SORTING_LINE, "light-barrier inlet", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-inlet"),
            t(MachineType.SORTING_LINE, "light-barrier behind color sensor", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-behind-color-sensor"),
            t(MachineType.SORTING_LINE, "color sensor", "A4", MqttPortDirection.OUT, MqttValueType.INTEGER, "color-sensor"),
            t(MachineType.SORTING_LINE, "light-barrier white", "I5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-white"),
            t(MachineType.SORTING_LINE, "light-barrier red", "I6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-red"),
            t(MachineType.SORTING_LINE, "light-barrier blue", "I7", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "light-barrier-blue"),
            t(MachineType.SORTING_LINE, "motor conveyor belt", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor"),
            t(MachineType.SORTING_LINE, "compressor", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-compressor"),
            t(MachineType.SORTING_LINE, "valve first ejector", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-first-ejector"),
            t(MachineType.SORTING_LINE, "valve second ejector", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-second-ejector"),
            t(MachineType.SORTING_LINE, "valve third ejector", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "enable-valve-third-ejector"),

            // Indexed Line
            t(MachineType.INDEXED_LINE, "push-button slider 1 front", "I1", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "button-slider-1-front"),
            t(MachineType.INDEXED_LINE, "push-button slider 1 rear", "I2", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "button-slider-1-rear"),
            t(MachineType.INDEXED_LINE, "push-button slider 2 front", "I3", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "button-slider-2-front"),
            t(MachineType.INDEXED_LINE, "push-button slider 2 rear", "I4", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "button-slider-2-rear"),
            t(MachineType.INDEXED_LINE, "phototransistor slider 1", "I5", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-slider-1"),
            t(MachineType.INDEXED_LINE, "phototransistor milling machine", "I6", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-milling-machine"),
            t(MachineType.INDEXED_LINE, "phototransistor loading station", "I7", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-loading-station"),
            t(MachineType.INDEXED_LINE, "phototransistor drilling machine", "I8", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-drilling-machine"),
            t(MachineType.INDEXED_LINE, "phototransistor conveyor belt swap", "I9", MqttPortDirection.OUT, MqttValueType.BOOLEAN, "phototransistor-conveyor-swap"),
            t(MachineType.INDEXED_LINE, "motor slider 1 backward", "Q1", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-slider-1-backward"),
            t(MachineType.INDEXED_LINE, "motor slider 1 forward", "Q2", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-slider-1-forward"),
            t(MachineType.INDEXED_LINE, "motor slider 2 backward", "Q3", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-slider-2-backward"),
            t(MachineType.INDEXED_LINE, "motor slider 2 forward", "Q4", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-slider-2-forward"),
            t(MachineType.INDEXED_LINE, "motor conveyor belt feed", "Q5", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-feed"),
            t(MachineType.INDEXED_LINE, "motor conveyor belt milling machine", "Q6", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-milling-machine"),
            t(MachineType.INDEXED_LINE, "motor milling machine", "Q7", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-milling-machine"),
            t(MachineType.INDEXED_LINE, "motor conveyor belt drilling machine", "Q8", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-drilling-machine"),
            t(MachineType.INDEXED_LINE, "motor drilling machine", "Q9", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-drilling-machine"),
            t(MachineType.INDEXED_LINE, "motor conveyor belt swap", "Q10", MqttPortDirection.IN, MqttValueType.BOOLEAN, "move-conveyor-swap")
        )
    }
}
