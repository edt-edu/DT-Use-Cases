package de.unistuttgart.isw.dtengine.synchronization

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.mapping.DtMapping
import java.time.Instant

/**
 * Synchronizes mapped values between gateway, model manager, database, and services.
 *
 * This corresponds to the cyclic synchronization step in the engine diagram.
 */
abstract class AbstractSynchronizer : DtComponent {
    abstract suspend fun synchronize(context: SynchronizationContext): SynchronizationResult

    abstract suspend fun synchronizeMapping(mapping: DtMapping, context: SynchronizationContext): SynchronizationStepResult

    abstract suspend fun checkLiveness(mapping: DtMapping): LivenessResult

    abstract suspend fun readSourceValue(mapping: DtMapping): DataValue?

    abstract suspend fun writeTargetValue(mapping: DtMapping, value: DataValue): SynchronizationStepResult

    abstract suspend fun toEvents(result: SynchronizationResult): List<DtEvent>
}

data class SynchronizationContext(
    val cycleId: Long,
    val startedAt: Instant = Instant.now(),
    val requestedBy: ComponentId? = null,
    val reason: String = "cycle",
    val metadata: Map<String, String> = emptyMap()
)

data class SynchronizationResult(
    val cycleId: Long,
    val startedAt: Instant,
    val finishedAt: Instant = Instant.now(),
    val steps: List<SynchronizationStepResult>,
    val errors: List<String> = emptyList()
) {
    val successful: Boolean get() = errors.isEmpty() && steps.all { it.successful }
}

data class SynchronizationStepResult(
    val mapping: DtMapping,
    val successful: Boolean,
    val value: DataValue? = null,
    val message: String = ""
)

data class LivenessResult(
    val sourceAlive: Boolean,
    val targetAlive: Boolean,
    val message: String = ""
) {
    val bothAlive: Boolean get() = sourceAlive && targetAlive
}
