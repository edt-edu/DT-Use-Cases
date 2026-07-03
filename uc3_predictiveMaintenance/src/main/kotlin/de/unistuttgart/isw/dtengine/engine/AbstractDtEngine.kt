package de.unistuttgart.isw.dtengine.engine

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import de.unistuttgart.isw.dtengine.service.ServiceResponse
import de.unistuttgart.isw.dtengine.synchronization.AbstractSynchronizer
import java.time.Instant

/**
 * Abstract Digital Twin engine.
 *
 * The concrete engine will later wire:
 * - MQTT gateway
 * - SQLite-backed model/data store
 * - AAS model access
 * - MAPE-K and monitoring services
 * - mappings between all components
 */
abstract class AbstractDtEngine : DtComponent {
    abstract val eventBus: DtEventBus
    abstract val modelManager: AbstractModelManager
    abstract val mappingRegistry: AbstractMappingRegistry
    abstract val synchronizer: AbstractSynchronizer

    abstract suspend fun gateways(): List<AbstractGateway>

    abstract suspend fun services(): List<AbstractDtService>

    abstract suspend fun registerGateway(gateway: AbstractGateway)

    abstract suspend fun unregisterGateway(id: ComponentId): Boolean

    abstract suspend fun registerService(service: AbstractDtService)

    abstract suspend fun unregisterService(id: ComponentId): Boolean

    abstract suspend fun handleIncomingEvent(event: DtEvent): List<DtEvent>

    abstract suspend fun handleServiceRequest(request: ServiceRequest): ServiceResponse

    abstract suspend fun cycle(context: EngineCycleContext): EngineCycleResult
}

data class EngineCycleContext(
    val cycleId: Long,
    val startedAt: Instant = Instant.now(),
    val reason: String = "scheduled-cycle",
    val metadata: Map<String, String> = emptyMap()
)

data class EngineCycleResult(
    val cycleId: Long,
    val startedAt: Instant,
    val finishedAt: Instant = Instant.now(),
    val emittedEvents: List<DtEvent> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val successful: Boolean get() = errors.isEmpty()
}
