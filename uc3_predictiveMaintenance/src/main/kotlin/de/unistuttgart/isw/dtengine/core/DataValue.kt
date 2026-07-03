package de.unistuttgart.isw.dtengine.core

import java.time.Instant

data class DataValue(
    val id: DataPointId,
    val value: Any?,
    val timestamp: Instant = Instant.now(),
    val quality: DataQuality = DataQuality.GOOD,
    val source: ComponentId? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class DataQuality {
    GOOD,
    UNCERTAIN,
    BAD,
    FAULT_INJECTED,
    UNKNOWN
}

data class CommandRequest(
    val commandId: String,
    val target: ComponentId,
    val command: String,
    val payload: Map<String, Any?> = emptyMap(),
    val correlationId: CorrelationId? = null,
    val requestedAt: Instant = Instant.now()
)

data class CommandResult(
    val commandId: String,
    val accepted: Boolean,
    val executed: Boolean = false,
    val message: String = "",
    val responsePayload: Map<String, Any?> = emptyMap(),
    val timestamp: Instant = Instant.now()
)
