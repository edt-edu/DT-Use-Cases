package de.unistuttgart.isw.dtengine.engine

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import de.unistuttgart.isw.dtengine.service.ServiceResponse
import de.unistuttgart.isw.dtengine.service.ServiceTickContext
import de.unistuttgart.isw.dtengine.synchronization.AbstractSynchronizer
import de.unistuttgart.isw.dtengine.synchronization.SynchronizationContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Default orchestration engine for the current DT prototype.
 *
 * The engine is intentionally light-weight: concrete components still own their
 * specific behavior, while the engine coordinates registration, gateway event
 * forwarding, service requests and explicit cycles.
 */
class DefaultDtEngine(
    override val id: ComponentId = ComponentId("dt-engine"),
    override val description: String = "Default Digital Twin engine",
    override val eventBus: DtEventBus,
    override val modelManager: AbstractModelManager,
    override val mappingRegistry: AbstractMappingRegistry,
    override val synchronizer: AbstractSynchronizer,
    private val serviceManager: DtServiceManager? = null,
    initialGateways: List<AbstractGateway> = emptyList(),
    initialServices: List<AbstractDtService> = emptyList()
) : AbstractDtEngine(), DtObserver {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)
    private val gatewayRegistry = ConcurrentHashMap<ComponentId, AbstractGateway>()
    private val serviceRegistry = ConcurrentHashMap<ComponentId, AbstractDtService>()

    init {
        initialGateways.forEach { gatewayRegistry[it.id] = it }
        initialServices.forEach { serviceRegistry[it.id] = it }
    }

    override val status: ComponentStatus
        get() = statusRef.get()

    override val observerId: ComponentId
        get() = id

    override suspend fun start() {
        statusRef.set(ComponentStatus.STARTING)
        gatewayRegistry.values.forEach { gateway -> gateway.subscribe(this) }
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPING)
        gatewayRegistry.values.forEach { gateway -> gateway.unsubscribe(this) }
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus = HealthStatus(
        alive = status == ComponentStatus.RUNNING || status == ComponentStatus.CREATED,
        status = status,
        message = "${gatewayRegistry.size} gateway(s), ${services().size} service(s)",
        details = mapOf(
            "gateways" to gatewayRegistry.keys.joinToString(",") { it.value },
            "mappings" to mappingRegistry.all().size.toString(),
            "synchronizer" to synchronizer.status.name
        )
    )

    override suspend fun gateways(): List<AbstractGateway> = gatewayRegistry.values.sortedBy { it.id.value }

    override suspend fun services(): List<AbstractDtService> =
        serviceManager?.registeredServices() ?: serviceRegistry.values.sortedBy { it.serviceId.value }

    override suspend fun registerGateway(gateway: AbstractGateway) {
        gatewayRegistry[gateway.id] = gateway
        if (status == ComponentStatus.RUNNING) gateway.subscribe(this)
    }

    override suspend fun unregisterGateway(id: ComponentId): Boolean {
        val removed = gatewayRegistry.remove(id) ?: return false
        removed.unsubscribe(this)
        return true
    }

    override suspend fun registerService(service: AbstractDtService) {
        serviceRegistry[service.id] = service
        serviceManager?.register(service)
    }

    override suspend fun unregisterService(id: ComponentId): Boolean {
        val removed = serviceRegistry.remove(id) ?: return false
        serviceManager?.unregister(removed.serviceId)
        return true
    }

    override suspend fun onEvent(event: DtEvent) {
        handleIncomingEvent(event)
    }

    override suspend fun handleIncomingEvent(event: DtEvent): List<DtEvent> {
        eventBus.publish(event)
        return listOf(event)
    }

    override suspend fun handleServiceRequest(request: ServiceRequest): ServiceResponse =
        serviceManager?.handleRequest(request)
            ?: ServiceResponse(request, successful = false, message = "No service manager configured")

    override suspend fun cycle(context: EngineCycleContext): EngineCycleResult {
        val emitted = mutableListOf<DtEvent>()
        val errors = mutableListOf<String>()
        val syncResult = runCatching {
            synchronizer.synchronize(
                SynchronizationContext(
                    cycleId = context.cycleId,
                    startedAt = context.startedAt,
                    requestedBy = id,
                    reason = context.reason,
                    metadata = context.metadata
                )
            )
        }.getOrElse { error ->
            errors += error.message ?: error::class.simpleName.orEmpty()
            null
        }
        if (syncResult != null) {
            val syncEvents = synchronizer.toEvents(syncResult)
            syncEvents.forEach { eventBus.publish(it) }
            emitted += syncEvents
            errors += syncResult.errors
        }

        serviceManager?.let { manager ->
            val serviceEvents = manager.tickAll(ServiceTickContext(context.cycleId, Instant.now(), context.metadata))
            emitted += serviceEvents
        }

        val resultEvent = DtEvent(
            type = EventType.SYNCHRONIZATION_COMPLETED,
            source = id,
            severity = if (errors.isEmpty()) EventSeverity.DEBUG else EventSeverity.WARN,
            payload = mapOf(
                "cycleId" to context.cycleId,
                "reason" to context.reason,
                "successful" to errors.isEmpty(),
                "emittedEvents" to emitted.size,
                "errors" to errors
            )
        )
        eventBus.publish(resultEvent)
        emitted += resultEvent
        return EngineCycleResult(
            cycleId = context.cycleId,
            startedAt = context.startedAt,
            finishedAt = Instant.now(),
            emittedEvents = emitted,
            errors = errors
        )
    }
}
