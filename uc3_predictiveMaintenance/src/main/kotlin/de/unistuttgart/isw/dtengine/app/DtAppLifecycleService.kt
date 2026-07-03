package de.unistuttgart.isw.dtengine.app

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventFilter
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.engine.AbstractDtEngine
import de.unistuttgart.isw.dtengine.engine.EngineCycleContext
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.DefaultMappingSeeder
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import de.unistuttgart.isw.dtengine.service.mapek.ConveyorKpiMapekConfig
import de.unistuttgart.isw.dtengine.synchronization.AbstractSynchronizer
import de.unistuttgart.isw.dtengine.synchronization.MappingSynchronizer
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Small application-level lifecycle for the integrated prototype.
 *
 * The individual components remain independently replaceable. This class only
 * provides a deterministic startup sequence for the runnable app profile:
 * SQLite -> AAS -> mappings/events/synchronizer -> services -> gateways -> engine.
 */
class DtAppLifecycleService(
    private val properties: DtAppProperties,
    private val eventBus: DtEventBus,
    private val mappingSeeder: DefaultMappingSeeder?,
    private val dataStore: DtDataStore?,
    private val modelManager: AbstractModelManager?,
    private val synchronizer: AbstractSynchronizer?,
    private val serviceManager: DtServiceManager?,
    private val engine: AbstractDtEngine?,
    private val gateways: List<AbstractGateway>,
    private val services: List<AbstractDtService>,
    private val conveyorConfig: ConveyorKpiMapekConfig
) {
    private val running = AtomicBoolean(false)
    private val startedAt = AtomicReference<Instant?>(null)
    private val gatewayBridge = EventBusForwardingObserver(ComponentId("app-gateway-event-bridge"), eventBus)

    fun isRunning(): Boolean = running.get()

    suspend fun start(): DtAppActionResponse {
        if (running.get()) {
            return DtAppActionResponse(true, "DT app is already running", details = mapOf("startedAt" to startedAt.get()?.toString()))
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        startComponent(dataStore as? DtComponent, "SQLite data store", messages, errors)
        startComponent(modelManager, "AAS model manager", messages, errors)

        if (properties.seedMappings) {
            val seeded = runCatching { mappingSeeder?.seed().orEmpty() }
                .onFailure { errors += "Mapping seeding failed: ${it.message ?: it::class.simpleName.orEmpty()}" }
                .getOrDefault(emptyList())
            messages += "Seeded ${seeded.size} mapping(s)"
            seeded.forEach { mapping ->
                eventBus.publish(
                    DtEvent(
                        type = EventType.MAPPING_CREATED,
                        source = ComponentId("app-lifecycle"),
                        severity = EventSeverity.DEBUG,
                        payload = mapOf(
                            "mappingId" to mapping.id.value,
                            "direction" to mapping.direction.name,
                            "source" to mapping.source.toString(),
                            "target" to mapping.target.toString()
                        )
                    )
                )
            }
        }

        wireEvents(messages, errors)
        startComponent(synchronizer, "mapping synchronizer", messages, errors)

        serviceManager?.let { manager ->
            services.forEach { service ->
                runCatching { manager.register(service, metadata = mapOf("registeredBy" to "dt-app-lifecycle")) }
                    .onFailure { errors += "Could not register service ${service.serviceId.value}: ${it.message ?: it::class.simpleName.orEmpty()}" }
            }
            startComponent(manager, "service manager", messages, errors)
            val startedServices = runCatching { manager.startReadyServices() }
                .onFailure { errors += "Could not start ready services: ${it.message ?: it::class.simpleName.orEmpty()}" }
                .getOrDefault(emptyList())
            messages += "Started ready service(s): ${startedServices.joinToString(",") { it.value }.ifBlank { "none" }}"
        }

        if (properties.startGateways) {
            gateways.forEach { gateway -> startComponent(gateway, "gateway ${gateway.id.value}", messages, errors) }
        } else if (gateways.isNotEmpty()) {
            messages += "Gateway startup skipped by dt.app.start-gateways=false"
        }

        startComponent(engine, "DT engine", messages, errors)

        if (properties.runStartupCycle) {
            val cycle = runEngineCycle("app-startup")
            messages += cycle.message
        }

        running.set(errors.isEmpty())
        if (errors.isEmpty()) startedAt.set(Instant.now())

        eventBus.publish(
            DtEvent(
                type = if (errors.isEmpty()) EventType.SERVICE_STARTED else EventType.ERROR,
                source = ComponentId("app-lifecycle"),
                severity = if (errors.isEmpty()) EventSeverity.INFO else EventSeverity.ERROR,
                payload = mapOf(
                    "componentId" to "dt-integrated-app",
                    "successful" to errors.isEmpty(),
                    "messages" to messages,
                    "errors" to errors
                )
            )
        )

        return DtAppActionResponse(
            successful = errors.isEmpty(),
            message = if (errors.isEmpty()) "Integrated DT app started" else "Integrated DT app started with errors",
            details = mapOf("messages" to messages, "errors" to errors)
        )
    }

    suspend fun stop(): DtAppActionResponse {
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        stopComponent(engine, "DT engine", messages, errors)
        gateways.forEach { stopComponent(it, "gateway ${it.id.value}", messages, errors) }
        stopComponent(serviceManager, "service manager", messages, errors)
        stopComponent(synchronizer, "mapping synchronizer", messages, errors)
        stopComponent(modelManager, "AAS model manager", messages, errors)
        stopComponent(dataStore as? DtComponent, "SQLite data store", messages, errors)

        eventBus.unsubscribe(gatewayBridge.observerId)
        running.set(false)
        startedAt.set(null)
        return DtAppActionResponse(
            successful = errors.isEmpty(),
            message = if (errors.isEmpty()) "Integrated DT app stopped" else "Integrated DT app stopped with errors",
            details = mapOf("messages" to messages, "errors" to errors)
        )
    }

    suspend fun status(): DtAppStatusResponse {
        val components = mutableListOf<DtComponentHealthResponse>()
        componentHealth(dataStore as? DtComponent, "database")?.let { components += it }
        componentHealth(modelManager, "model-manager")?.let { components += it }
        componentHealth(synchronizer, "synchronizer")?.let { components += it }
        componentHealth(serviceManager, "service-manager")?.let { components += it }
        gateways.forEach { componentHealth(it, "gateway")?.let { health -> components += health } }
        services.forEach { componentHealth(it, "service")?.let { health -> components += health } }
        componentHealth(engine, "engine")?.let { components += it }
        return DtAppStatusResponse(
            appRunning = running.get(),
            startedAt = startedAt.get()?.toString(),
            profileHint = "Use --spring.profiles.active=integrated for local REST/demo mode or integrated,mqtt for real MQTT.",
            components = components
        )
    }

    suspend fun runEngineCycle(reason: String = "manual"): DtAppActionResponse {
        val activeEngine = engine ?: return DtAppActionResponse(false, "No DT engine bean configured")
        val result = activeEngine.cycle(
            EngineCycleContext(
                cycleId = System.currentTimeMillis(),
                reason = reason,
                metadata = mapOf("requestedBy" to "dt-app")
            )
        )
        return DtAppActionResponse(
            successful = result.successful,
            message = "Engine cycle ${result.cycleId} finished with ${result.emittedEvents.size} event(s)",
            details = mapOf(
                "cycleId" to result.cycleId,
                "successful" to result.successful,
                "errors" to result.errors,
                "emittedEvents" to result.emittedEvents.size
            )
        )
    }

    suspend fun publishDemoValue(request: DemoValueRequest): DtAppActionResponse {
        val source = ComponentId(request.sourceComponentId)
        val event = DtEvent(
            type = EventType.GATEWAY_DATA_RECEIVED,
            source = source,
            payload = mapOf(
                "dataPointId" to request.dataPointId,
                "value" to request.value,
                "quality" to DataQuality.GOOD.name,
                "source" to "rest-demo"
            )
        )
        eventBus.publish(event)
        val cycle = if (request.runEngineCycle) runEngineCycle("demo-value") else null
        return DtAppActionResponse(
            successful = true,
            message = "Published demo value for ${request.dataPointId}",
            details = buildMap {
                put("eventId", event.id.toString())
                put("dataPointId", request.dataPointId)
                put("value", request.value)
                if (cycle != null) put("cycle", cycle.details)
            }
        )
    }

    suspend fun simulateTransport(request: DemoTransportRequest): DtAppActionResponse {
        request.payloadWeightKg?.let { weight ->
            serviceManager?.handleRequest(
                ServiceRequest(
                    targetService = de.unistuttgart.isw.dtengine.core.ServiceId(conveyorConfig.serviceId),
                    operation = "set-payload-weight",
                    payload = mapOf("payloadWeightKg" to weight),
                    requestedBy = ComponentId("app-demo")
                )
            )
        }

        val now = Instant.now()
        val end = now.plusMillis((request.durationSeconds.coerceAtLeast(0.001) * 1000.0).toLong())
        val startEvent = gatewayLikeDemoEvent(conveyorConfig.startSignalDataPointId, request.startValue, now)
        val endEvent = gatewayLikeDemoEvent(conveyorConfig.endSignalDataPointId, request.endValue, end)
        eventBus.publish(startEvent)
        eventBus.publish(endEvent)
        val cycle = if (request.runEngineCycle) runEngineCycle("demo-transport") else null

        return DtAppActionResponse(
            successful = true,
            message = "Simulated conveyor transport in ${request.durationSeconds} s",
            details = buildMap {
                put("startDataPointId", conveyorConfig.startSignalDataPointId)
                put("endDataPointId", conveyorConfig.endSignalDataPointId)
                put("durationSeconds", request.durationSeconds)
                put("payloadWeightKg", request.payloadWeightKg ?: conveyorConfig.payloadWeightKg)
                put("startEventId", startEvent.id.toString())
                put("endEventId", endEvent.id.toString())
                if (cycle != null) put("cycle", cycle.details)
            }
        )
    }

    private fun gatewayLikeDemoEvent(dataPointId: String, value: Any?, timestamp: Instant): DtEvent = DtEvent(
        type = EventType.GATEWAY_DATA_RECEIVED,
        source = ComponentId(properties.demoSourceComponentId),
        timestamp = timestamp,
        payload = mapOf(
            "dataPointId" to dataPointId,
            "value" to value,
            "quality" to DataQuality.GOOD.name,
            "source" to "rest-demo"
        )
    )

    private suspend fun wireEvents(messages: MutableList<String>, errors: MutableList<String>) {
        (synchronizer as? DtObserver)?.let { sync ->
            eventBus.subscribe(
                sync,
                EventFilter(
                    acceptedTypes = setOf(
                        EventType.GATEWAY_DATA_RECEIVED,
                        EventType.GATEWAY_COMMAND_SENT,
                        EventType.MODEL_PROPERTY_CREATED,
                        EventType.MODEL_PROPERTY_UPDATED,
                        EventType.SERVICE_REQUEST,
                        EventType.SERVICE_ANSWER
                    )
                )
            )
            messages += "Subscribed synchronizer to event bus"
        } ?: messages.add("Synchronizer is not event-observable")

        serviceManager?.let { manager ->
            eventBus.subscribe(manager, EventFilter.all())
            messages += "Subscribed service manager to event bus"
        }

        gateways.forEach { gateway ->
            runCatching {
                (synchronizer as? MappingSynchronizer)?.registerGateway(gateway)
                gateway.subscribe(gatewayBridge)
            }.onFailure { error ->
                errors += "Could not bridge gateway ${gateway.id.value}: ${error.message ?: error::class.simpleName.orEmpty()}"
            }
        }
        if (gateways.isNotEmpty()) messages += "Bridged ${gateways.size} gateway(s) to event bus"
    }

    private suspend fun startComponent(
        component: DtComponent?,
        label: String,
        messages: MutableList<String>,
        errors: MutableList<String>
    ) {
        if (component == null) {
            messages += "$label not configured"
            return
        }
        if (component.status == ComponentStatus.RUNNING) {
            messages += "$label already running"
            return
        }
        runCatching { component.start() }
            .onSuccess { messages += "Started $label" }
            .onFailure { errors += "Failed to start $label: ${it.message ?: it::class.simpleName.orEmpty()}" }
    }

    private suspend fun stopComponent(
        component: DtComponent?,
        label: String,
        messages: MutableList<String>,
        errors: MutableList<String>
    ) {
        if (component == null || component.status == ComponentStatus.STOPPED) return
        runCatching { component.stop() }
            .onSuccess { messages += "Stopped $label" }
            .onFailure { errors += "Failed to stop $label: ${it.message ?: it::class.simpleName.orEmpty()}" }
    }

    private suspend fun componentHealth(component: DtComponent?, type: String): DtComponentHealthResponse? {
        if (component == null) return null
        val health = runCatching { component.health() }.getOrElse {
            de.unistuttgart.isw.dtengine.core.HealthStatus(
                alive = false,
                status = ComponentStatus.FAILED,
                message = it.message ?: it::class.simpleName.orEmpty()
            )
        }
        return DtComponentHealthResponse(
            id = component.id.value,
            type = type,
            status = health.status.name,
            alive = health.alive,
            message = health.message,
            details = health.details
        )
    }
}

private class EventBusForwardingObserver(
    override val observerId: ComponentId,
    private val eventBus: DtEventBus
) : DtObserver {
    override suspend fun onEvent(event: DtEvent) {
        eventBus.publish(event)
    }
}
