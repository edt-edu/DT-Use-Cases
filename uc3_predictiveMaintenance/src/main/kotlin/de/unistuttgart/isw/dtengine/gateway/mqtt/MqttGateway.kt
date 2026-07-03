package de.unistuttgart.isw.dtengine.gateway.mqtt

import de.unistuttgart.isw.dtengine.core.CommandRequest
import de.unistuttgart.isw.dtengine.core.CommandResult
import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.gateway.GatewayCapability
import kotlinx.coroutines.runBlocking
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class MqttGateway(
    override val id: ComponentId,
    override val description: String,
    private val config: MqttGatewayConfig,
    private val catalog: MqttTopicCatalog = MqttTopicCatalog.default(),
    private val codec: MqttPayloadCodec = MqttPayloadCodec(),
    private val clientFactory: MqttClientFactory = PahoMqttClientFactory()
) : AbstractGateway(), MqttCallbackExtended {

    private var statusInternal: ComponentStatus = ComponentStatus.CREATED
    private var client: IMqttAsyncClient? = null
    private val observers: MutableMap<ComponentId, DtObserver> = ConcurrentHashMap()
    private val latestValues: MutableMap<DataPointId, DataValue> = ConcurrentHashMap()
    private val discoveredTopics: MutableMap<String, DiscoveredTopic> = ConcurrentHashMap()

    override val status: ComponentStatus
        get() = statusInternal

    override val capabilities: Set<GatewayCapability> = setOf(
        GatewayCapability.READ_DATA,
        GatewayCapability.WRITE_COMMAND,
        GatewayCapability.SUBSCRIBE_DATA,
        GatewayCapability.PUBLISH_EVENT,
        GatewayCapability.LIVENESS_CHECK,
        GatewayCapability.FAULT_INJECTION
    )

    override val readableDataPoints: Set<DataPointId>
        get() = catalog.readable(config.machineType).map { it.dataPointId(config.machineId) }.toSet() +
            discoveredTopics.values.map { it.dataPointId }.toSet()

    override val writableCommands: Set<String> =
        catalog.writable(config.machineType).map { it.topicSuffix }.toSet()

    override suspend fun start() {
        if (statusInternal == ComponentStatus.RUNNING || statusInternal == ComponentStatus.STARTING) return
        statusInternal = ComponentStatus.STARTING

        val createdClient = client ?: clientFactory.create(config.brokerUri, config.clientId).also { client = it }
        createdClient.setCallback(this)
        createdClient.connect(connectOptions()).waitForCompletion(config.operationTimeoutMillis)
        subscribeReadableTopics(createdClient)
        statusInternal = ComponentStatus.RUNNING
    }

    override suspend fun stop() {
        statusInternal = ComponentStatus.STOPPING
        client?.let {
            if (it.isConnected) {
                it.disconnect().waitForCompletion(config.operationTimeoutMillis)
            }
            it.close()
        }
        client = null
        statusInternal = ComponentStatus.STOPPED
    }

    override suspend fun health(): HealthStatus {
        val connected = client?.isConnected == true
        return HealthStatus(
            alive = statusInternal == ComponentStatus.RUNNING && connected,
            status = statusInternal,
            message = if (connected) "MQTT client connected" else "MQTT client not connected",
            details = mapOf(
                "brokerUri" to config.brokerUri,
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "readableTopics" to readableDataPoints.size.toString(),
                "writableCommands" to writableCommands.size.toString(),
                "discoveryEnabled" to config.discoveryEnabled.toString(),
                "discoveredTopics" to discoveredTopics.size.toString()
            )
        )
    }

    override suspend fun read(dataPointId: DataPointId): DataValue? = latestValues[dataPointId]

    override suspend fun write(command: CommandRequest): CommandResult {
        val target = resolveCommandTarget(command)
            ?: return CommandResult(
                commandId = command.commandId,
                accepted = false,
                executed = false,
                message = "Unknown MQTT command/topic '${command.command}' for ${config.machineType.label}"
            )

        val (topic, spec) = target
        val value = command.payload["value"] ?: command.payload["command"] ?: true
        val payload = codec.encode(value, spec?.valueType ?: MqttValueType.STRING)
        val mqttMessage = MqttMessage(payload).apply {
            qos = config.qos
            isRetained = config.retained
        }

        val currentClient = client
            ?: return CommandResult(command.commandId, accepted = false, executed = false, message = "MQTT client not initialized")
        if (!currentClient.isConnected) {
            return CommandResult(command.commandId, accepted = false, executed = false, message = "MQTT client not connected")
        }

        currentClient.publish(topic, mqttMessage).waitForCompletion(config.operationTimeoutMillis)

        val event = DtEvent(
            type = EventType.GATEWAY_COMMAND_SENT,
            source = id,
            correlationId = command.correlationId,
            payload = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "topic" to topic,
                "command" to command.command,
                "value" to value,
                "dataPointId" to (spec?.dataPointId(config.machineId)?.value ?: command.command),
                "port" to spec?.port,
                "description" to spec?.description
            )
        )
        notifyObservers(event)

        return CommandResult(
            commandId = command.commandId,
            accepted = true,
            executed = true,
            message = "Published MQTT command to $topic",
            responsePayload = event.payload
        )
    }

    override suspend fun subscribe(observer: DtObserver) {
        observers[observer.observerId] = observer
    }

    override suspend fun unsubscribe(observer: DtObserver) {
        observers.remove(observer.observerId)
    }

    override suspend fun toGatewayEvent(rawPayload: Map<String, Any?>): DtEvent {
        val topic = rawPayload["topic"]?.toString()
            ?: throw IllegalArgumentException("rawPayload must contain a 'topic'")
        val payload = when (val raw = rawPayload["payload"] ?: rawPayload["value"]) {
            is ByteArray -> raw
            null -> ByteArray(0)
            else -> raw.toString().toByteArray()
        }
        return buildGatewayDataEvent(topic, payload, Instant.now())
    }

    suspend fun injectFault(dataPointId: DataPointId, value: Any?, reason: String = "manual fault injection"): DtEvent {
        val faulted = DataValue(
            id = dataPointId,
            value = value,
            quality = DataQuality.FAULT_INJECTED,
            source = id,
            metadata = mapOf("reason" to reason)
        )
        latestValues[dataPointId] = faulted
        val event = DtEvent(
            type = EventType.GATEWAY_DATA_RECEIVED,
            source = id,
            severity = EventSeverity.WARN,
            payload = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "dataPointId" to dataPointId.value,
                "value" to value,
                "quality" to faulted.quality.name,
                "reason" to reason
            )
        )
        notifyObservers(event)
        return event
    }

    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
        statusInternal = ComponentStatus.RUNNING
        client?.let { mqttClient ->
            runCatching { subscribeReadableTopics(mqttClient) }
        }
    }

    override fun connectionLost(cause: Throwable?) {
        statusInternal = ComponentStatus.DEGRADED
        val event = DtEvent(
            type = EventType.ERROR,
            source = id,
            severity = EventSeverity.ERROR,
            payload = mapOf(
                "message" to "MQTT connection lost",
                "cause" to (cause?.message ?: "unknown")
            )
        )
        notifyObservers(event)
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        if (topic == null || message == null) return
        val event = runCatching { buildGatewayDataEvent(topic, message.payload, Instant.now()) }
            .getOrElse { error ->
                DtEvent(
                    type = EventType.ERROR,
                    source = id,
                    severity = EventSeverity.ERROR,
                    payload = mapOf(
                        "topic" to topic,
                        "message" to "Failed to decode MQTT payload",
                        "cause" to (error.message ?: error::class.simpleName.orEmpty())
                    )
                )
            }
        notifyObservers(event)
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        // The engine receives the command-sent event directly after publish completion in write().
    }

    private fun connectOptions(): MqttConnectOptions = MqttConnectOptions().apply {
        isAutomaticReconnect = true
        isCleanSession = config.cleanSession
        connectionTimeout = config.connectionTimeoutSeconds
        keepAliveInterval = config.keepAliveIntervalSeconds
        config.username?.let { userName = it }
        config.password?.let { password = it.toCharArray() }
    }

    private fun subscribeReadableTopics(mqttClient: IMqttAsyncClient) {
        catalog.readable(config.machineType).forEach { spec ->
            mqttClient.subscribe(spec.topic(config.machineId, config.topicPrefix), config.qos)
                .waitForCompletion(config.operationTimeoutMillis)
        }

        if (config.discoveryEnabled) {
            effectiveDiscoveryTopicFilters().forEach { filter ->
                mqttClient.subscribe(filter, config.qos)
                    .waitForCompletion(config.operationTimeoutMillis)
            }
        }
    }

    private fun effectiveDiscoveryTopicFilters(): List<String> =
        config.discoveryTopicFilters.takeIf { it.isNotEmpty() }
            ?: listOf(MqttTopicSpec(config.machineType, "discovery", "Virtual", MqttPortDirection.OUT, MqttValueType.STRING, "#")
                .topic(config.machineId, config.topicPrefix))

    private fun buildGatewayDataEvent(topic: String, payload: ByteArray, timestamp: Instant): DtEvent {
        val spec = catalog.byTopic(config.machineType, config.machineId, config.topicPrefix, topic)
        if (spec != null) return buildCatalogGatewayDataEvent(spec, topic, payload, timestamp)
        if (config.registerUnknownTopics) return buildDiscoveredGatewayDataEvent(topic, payload, timestamp)
        throw IllegalArgumentException("Unknown MQTT topic '$topic' for ${config.machineType.label}")
    }

    private fun buildCatalogGatewayDataEvent(
        spec: MqttTopicSpec,
        topic: String,
        payload: ByteArray,
        timestamp: Instant
    ): DtEvent {
        val decodedValue = codec.decode(payload, spec.valueType)
        val dataValue = DataValue(
            id = spec.dataPointId(config.machineId),
            value = decodedValue,
            timestamp = timestamp,
            source = id,
            metadata = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "topic" to topic,
                "topicSuffix" to spec.topicSuffix,
                "port" to spec.port,
                "description" to spec.description,
                "valueType" to spec.valueType.name,
                "discovered" to "false"
            )
        )
        latestValues[dataValue.id] = dataValue

        return DtEvent(
            type = EventType.GATEWAY_DATA_RECEIVED,
            source = id,
            payload = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "topic" to topic,
                "topicSuffix" to spec.topicSuffix,
                "dataPointId" to dataValue.id.value,
                "port" to spec.port,
                "description" to spec.description,
                "value" to decodedValue,
                "valueType" to spec.valueType.name,
                "quality" to dataValue.quality.name,
                "discovered" to false
            )
        )
    }

    private fun buildDiscoveredGatewayDataEvent(topic: String, payload: ByteArray, timestamp: Instant): DtEvent {
        val valueType = if (config.inferUnknownTopicValueType) {
            codec.inferValueType(payload)
        } else {
            config.unknownTopicValueType
        }
        val decodedValue = codec.decode(payload, valueType)
        val dataPointId = dataPointIdFromTopic(topic)
        discoveredTopics[topic] = DiscoveredTopic(topic, dataPointId, valueType, timestamp)
        val dataValue = DataValue(
            id = dataPointId,
            value = decodedValue,
            timestamp = timestamp,
            source = id,
            metadata = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "topic" to topic,
                "topicSuffix" to topicSuffixFromTopic(topic),
                "port" to "discovered",
                "description" to "Discovered MQTT topic $topic",
                "valueType" to valueType.name,
                "discovered" to "true"
            )
        )
        latestValues[dataValue.id] = dataValue

        return DtEvent(
            type = EventType.GATEWAY_DATA_RECEIVED,
            source = id,
            payload = mapOf(
                "machineId" to config.machineId,
                "machineType" to config.machineType.name,
                "topic" to topic,
                "topicSuffix" to topicSuffixFromTopic(topic),
                "dataPointId" to dataValue.id.value,
                "port" to "discovered",
                "description" to "Discovered MQTT topic $topic",
                "value" to decodedValue,
                "valueType" to valueType.name,
                "quality" to dataValue.quality.name,
                "discovered" to true
            )
        )
    }

    private fun dataPointIdFromTopic(topic: String): DataPointId = DataPointId(
        "${config.machineId}.${topicSuffixFromTopic(topic).replace('/', '.')}"
    )

    private fun topicSuffixFromTopic(topic: String): String {
        val normalizedTopic = topic.trim('/')
        val normalizedPrefix = config.topicPrefix.trim('/')
        val machinePath = listOf(normalizedPrefix, config.machineId.trim('/'))
            .filter { it.isNotBlank() }
            .joinToString("/")
        return normalizedTopic
            .removePrefix(machinePath)
            .trim('/')
            .ifBlank { normalizedTopic.replace('/', '.') }
    }

    private fun resolveCommandTarget(command: CommandRequest): Pair<String, MqttTopicSpec?>? {
        val explicitTopic = command.payload["topic"]?.toString()
        if (!explicitTopic.isNullOrBlank()) {
            val spec = catalog.byTopic(config.machineType, config.machineId, config.topicPrefix, explicitTopic)
            if (spec != null || config.allowUnknownCommandTopics) return explicitTopic to spec
        }

        val dataPointCandidate = command.payload["dataPointId"]?.toString()
        if (!dataPointCandidate.isNullOrBlank()) {
            catalog.byDataPointId(config.machineType, config.machineId, dataPointCandidate)
                ?.takeIf { it.direction == MqttPortDirection.IN }
                ?.let { return it.topic(config.machineId, config.topicPrefix) to it }
        }

        val suffixCandidate = command.command.trim().trim('/')
        catalog.bySuffix(config.machineType, suffixCandidate)
            ?.takeIf { it.direction == MqttPortDirection.IN }
            ?.let { return it.topic(config.machineId, config.topicPrefix) to it }

        catalog.byDataPointId(config.machineType, config.machineId, command.command)
            ?.takeIf { it.direction == MqttPortDirection.IN }
            ?.let { return it.topic(config.machineId, config.topicPrefix) to it }

        if (command.command.startsWith("/") && config.allowUnknownCommandTopics) {
            return command.command to null
        }
        return null
    }

    private fun notifyObservers(event: DtEvent) {
        if (observers.isEmpty()) return
        runBlocking {
            observers.values.forEach { observer -> observer.onEvent(event) }
        }
    }
}

private data class DiscoveredTopic(
    val topic: String,
    val dataPointId: DataPointId,
    val valueType: MqttValueType,
    val firstSeenAt: Instant
)
