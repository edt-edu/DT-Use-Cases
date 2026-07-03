package de.unistuttgart.isw.dtengine.gateway

import de.unistuttgart.isw.dtengine.core.CommandRequest
import de.unistuttgart.isw.dtengine.core.CommandResult
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtObserver

/**
 * Abstract adapter to the cyber-physical system or an external data source.
 *
 * Later concrete gateways can implement MQTT, OPC UA, REST, file replay, simulation, etc.
 */
abstract class AbstractGateway : DtComponent {
    abstract val capabilities: Set<GatewayCapability>
    abstract val readableDataPoints: Set<DataPointId>
    abstract val writableCommands: Set<String>

    abstract suspend fun read(dataPointId: DataPointId): DataValue?

    abstract suspend fun write(command: CommandRequest): CommandResult

    abstract suspend fun subscribe(observer: DtObserver)

    abstract suspend fun unsubscribe(observer: DtObserver)

    /**
     * Converts raw gateway input into the engine-internal event format.
     */
    abstract suspend fun toGatewayEvent(rawPayload: Map<String, Any?>): DtEvent
}

enum class GatewayCapability {
    READ_DATA,
    WRITE_COMMAND,
    SUBSCRIBE_DATA,
    PUBLISH_EVENT,
    LIVENESS_CHECK,
    FAULT_INJECTION
}
