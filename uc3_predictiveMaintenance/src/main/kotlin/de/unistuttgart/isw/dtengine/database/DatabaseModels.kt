package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.ServiceId
import java.time.Instant

/**
 * Persistent description of a data point known to the DT engine.
 *
 * The value history is stored separately. This record is the database-side
 * dictionary entry that can later be matched against service requirements.
 */
data class DataPointDefinition(
    val id: DataPointId,
    val sourceComponent: ComponentId? = null,
    val valueType: String? = null,
    val machineId: String? = null,
    val topic: String? = null,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class StoredDataValue(
    val sequenceId: Long,
    val value: DataValue
)

data class ServiceDataPointRequirement(
    val serviceId: ServiceId,
    val dataPointId: DataPointId,
    val required: Boolean = true,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap()
)

data class ServiceStartCheck(
    val serviceId: ServiceId,
    val ready: Boolean,
    val requiredDataPoints: Set<DataPointId>,
    val availableDataPoints: Set<DataPointId>,
    val missingDataPoints: Set<DataPointId>,
    val checkedAt: Instant = Instant.now()
)

fun DataValue.toDefinition(
    description: String = "",
    machineId: String? = metadata["machineId"],
    topic: String? = metadata["topic"],
): DataPointDefinition = DataPointDefinition(
    id = id,
    sourceComponent = source,
    valueType = metadata["valueType"] ?: value?.let { it::class.simpleName },
    machineId = machineId,
    topic = topic,
    description = description.ifBlank { metadata["description"].orEmpty() },
    metadata = metadata,
    updatedAt = timestamp
)

fun qualityFromString(value: String?): DataQuality = value
    ?.let { runCatching { DataQuality.valueOf(it) }.getOrNull() }
    ?: DataQuality.UNKNOWN
