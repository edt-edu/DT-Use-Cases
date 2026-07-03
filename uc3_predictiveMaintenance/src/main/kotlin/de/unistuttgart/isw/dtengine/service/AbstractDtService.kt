package de.unistuttgart.isw.dtengine.service

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.ServiceId
import java.time.Instant

/**
 * Base abstraction for all services connected to the Digital Twin engine.
 *
 * Examples added later: GUI service, simulator service, monitoring service,
 * MAPE-K service, deviation recognition, automated behavior service.
 */
abstract class AbstractDtService : DtComponent {
    abstract val serviceId: ServiceId
    abstract val serviceType: ServiceType
    abstract val requiredDataPoints: Set<DataPointId>
    abstract val producedDataPoints: Set<DataPointId>

    /**
     * Model properties the service needs to read before it can provide useful results.
     * Produced model properties can be described in the service registry metadata.
     */
    open val requiredModelProperties: Set<ModelPropertyId> = emptySet()

    /**
     * Named engine/model functions or extension points required by the service.
     * Example: sendBack(state).
     */
    open val requiredFunctions: Set<String> = emptySet()

    abstract suspend fun canStart(context: ServiceStartContext): ServiceReadiness

    abstract suspend fun handleRequest(request: ServiceRequest): ServiceResponse

    abstract suspend fun handleEvent(event: DtEvent): List<ServiceRequest>

    abstract suspend fun tick(context: ServiceTickContext): List<DtEvent>
}

enum class ServiceType {
    GUI,
    SIMULATOR,
    MONITORING,
    MAPEK,
    AUTONOMOUS_SYSTEM,
    DEVIATION_RECOGNITION,
    CUSTOM
}

data class ServiceStartContext(
    val availableDataPoints: Set<DataPointId>,
    val availableComponents: Set<ComponentId>,
    val metadata: Map<String, String> = emptyMap()
)

data class ServiceReadiness(
    val ready: Boolean,
    val missingDataPoints: Set<DataPointId> = emptySet(),
    val reason: String = ""
)

data class ServiceRequest(
    val targetService: ServiceId,
    val operation: String,
    val payload: Map<String, Any?> = emptyMap(),
    val requestedBy: ComponentId? = null,
    val requestedAt: Instant = Instant.now()
)

data class ServiceResponse(
    val request: ServiceRequest,
    val successful: Boolean,
    val payload: Map<String, Any?> = emptyMap(),
    val message: String = "",
    val respondedAt: Instant = Instant.now()
)

data class ServiceTickContext(
    val cycleId: Long,
    val now: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
)
