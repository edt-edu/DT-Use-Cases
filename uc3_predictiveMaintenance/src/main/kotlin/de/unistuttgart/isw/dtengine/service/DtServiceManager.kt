package de.unistuttgart.isw.dtengine.service

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.ServiceRequirementStore
import de.unistuttgart.isw.dtengine.database.ServiceRegistryStore
import de.unistuttgart.isw.dtengine.database.toServiceDescriptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime registry and dispatcher for DT services.
 *
 * The manager is intentionally small. It registers services and their requirements,
 * checks readiness against the DB dictionary, starts services when possible, forwards
 * events to services, and triggers service ticks during an engine cycle.
 */
class DtServiceManager(
    override val id: ComponentId = ComponentId("dt-service-manager"),
    override val description: String = "Digital Twin service manager",
    private val dataStore: DtDataStore? = null,
    private val requirementStore: ServiceRequirementStore? = null,
    private val registryStore: ServiceRegistryStore? = null,
    private val eventBus: DtEventBus? = null
) : DtComponent, DtObserver {

    private val statusRef = AtomicReference(ComponentStatus.CREATED)
    private val services = ConcurrentHashMap<ServiceId, AbstractDtService>()

    override val status: ComponentStatus
        get() = statusRef.get()

    override val observerId: ComponentId
        get() = id

    override suspend fun onEvent(event: DtEvent) {
        dispatchEvent(event)
    }

    override suspend fun start() {
        statusRef.set(ComponentStatus.STARTING)
        startReadyServices()
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPING)
        services.values.forEach { service ->
            runCatching { service.stop() }
        }
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus = HealthStatus(
        alive = status == ComponentStatus.RUNNING || status == ComponentStatus.CREATED,
        status = status,
        message = "${services.size} service(s) registered",
        details = services.map { (serviceId, service) -> serviceId.value to service.status.name }.toMap()
    )

    suspend fun register(service: AbstractDtService, metadata: Map<String, String> = emptyMap()) {
        services[service.serviceId] = service
        registryStore?.registerServiceDescriptor(service.toServiceDescriptor(metadata))
        requirementStore?.registerService(service)
    }

    suspend fun unregister(serviceId: ServiceId): Boolean {
        val service = services.remove(serviceId) ?: return false
        runCatching { service.stop() }
        registryStore?.removeServiceDescriptor(serviceId)
        return true
    }

    fun service(serviceId: ServiceId): AbstractDtService? = services[serviceId]

    fun registeredServices(): List<AbstractDtService> = services.values.sortedBy { it.serviceId.value }

    suspend fun readiness(service: AbstractDtService): ServiceReadiness {
        val availableDataPoints = dataStore?.knownDataPoints().orEmpty()
        val dbReadiness = requirementStore?.checkServiceReadiness(service)
        val context = ServiceStartContext(
            availableDataPoints = availableDataPoints,
            availableComponents = setOfNotNull(dataStoreId(), requirementStoreId(), registryStoreId())
        )
        val serviceReadiness = service.canStart(context)
        val missing = dbReadiness?.missingDataPoints.orEmpty() + serviceReadiness.missingDataPoints
        return ServiceReadiness(
            ready = missing.isEmpty() && serviceReadiness.ready,
            missingDataPoints = missing,
            reason = listOfNotNull(
                dbReadiness?.takeUnless { it.ready }?.let { "Missing DB data points: ${it.missingDataPoints.joinToString()}" },
                serviceReadiness.reason.takeIf { it.isNotBlank() }
            ).joinToString("; ")
        )
    }

    suspend fun startReadyServices(): List<ServiceId> {
        val started = mutableListOf<ServiceId>()
        services.values.forEach { service ->
            val ready = readiness(service)
            if (ready.ready && service.status != ComponentStatus.RUNNING) {
                service.start()
                started += service.serviceId
                publishManagerEvent(
                    EventType.SERVICE_STARTED,
                    mapOf(
                        "serviceId" to service.serviceId.value,
                        "componentId" to service.id.value,
                        "action" to "started"
                    )
                )
            } else if (!ready.ready) {
                publishManagerEvent(
                    EventType.SERVICE_REQUEST,
                    mapOf(
                        "serviceId" to service.serviceId.value,
                        "action" to "not-started",
                        "missingDataPoints" to ready.missingDataPoints.map { it.value },
                        "reason" to ready.reason
                    ),
                    severity = EventSeverity.WARN
                )
            }
        }
        return started
    }

    suspend fun dispatchEvent(event: DtEvent): List<ServiceRequest> {
        val requests = mutableListOf<ServiceRequest>()
        services.values.forEach { service ->
            val serviceRequests = runCatching { service.handleEvent(event) }.getOrElse { error ->
                publishManagerEvent(
                    EventType.ERROR,
                    mapOf(
                        "serviceId" to service.serviceId.value,
                        "message" to "Service failed while handling event",
                        "cause" to (error.message ?: error::class.simpleName.orEmpty())
                    ),
                    severity = EventSeverity.ERROR
                )
                emptyList()
            }
            requests += serviceRequests

            // Important for short MQTT sensor pulses: the MAPE-K service must be
            // ticked directly after a gateway input event, not only during the
            // periodic polling cycle. Otherwise a true->false sensor pulse can be
            // overwritten in SQLite before the service has a chance to derive a KPI.
            if (shouldTickAfterInputEvent(event) && service.status == ComponentStatus.RUNNING) {
                val emittedEvents = runCatching {
                    service.tick(
                        ServiceTickContext(
                            cycleId = event.timestamp.toEpochMilli(),
                            now = event.timestamp,
                            metadata = mapOf(
                                "triggerEventId" to event.id.toString(),
                                "triggerEventType" to event.type.name,
                                "triggerSource" to event.source.value,
                                "tickMode" to "event-driven"
                            )
                        )
                    )
                }.getOrElse { error ->
                    listOf(
                        DtEvent(
                            type = EventType.ERROR,
                            source = id,
                            severity = EventSeverity.ERROR,
                            payload = mapOf(
                                "serviceId" to service.serviceId.value,
                                "message" to "Service event-driven tick failed",
                                "cause" to (error.message ?: error::class.simpleName.orEmpty()),
                                "triggerEventId" to event.id.toString()
                            )
                        )
                    )
                }
                emittedEvents.forEach { eventBus?.publish(it) }
            }
        }
        return requests
    }

    private fun shouldTickAfterInputEvent(event: DtEvent): Boolean =
        event.type == EventType.GATEWAY_DATA_RECEIVED

    suspend fun tickAll(context: ServiceTickContext): List<DtEvent> {
        val emitted = services.values.flatMap { service ->
            if (service.status != ComponentStatus.RUNNING) {
                emptyList()
            } else {
                runCatching { service.tick(context) }.getOrElse { error ->
                    listOf(
                        DtEvent(
                            type = EventType.ERROR,
                            source = id,
                            severity = EventSeverity.ERROR,
                            payload = mapOf(
                                "serviceId" to service.serviceId.value,
                                "message" to "Service tick failed",
                                "cause" to (error.message ?: error::class.simpleName.orEmpty())
                            )
                        )
                    )
                }
            }
        }
        emitted.forEach { eventBus?.publish(it) }
        return emitted
    }

    suspend fun handleRequest(request: ServiceRequest): ServiceResponse {
        val service = services[request.targetService]
            ?: return ServiceResponse(request, successful = false, message = "Unknown service ${request.targetService}")
        return service.handleRequest(request)
    }

    private suspend fun publishManagerEvent(
        type: EventType,
        payload: Map<String, Any?>,
        severity: EventSeverity = EventSeverity.INFO
    ) {
        eventBus?.publish(
            DtEvent(
                type = type,
                source = id,
                payload = payload,
                severity = severity
            )
        )
    }

    private fun dataStoreId(): ComponentId? = (dataStore as? DtComponent)?.id

    private fun requirementStoreId(): ComponentId? = (requirementStore as? DtComponent)?.id

    private fun registryStoreId(): ComponentId? = (registryStore as? DtComponent)?.id
}
