package de.unistuttgart.isw.dtengine.app

import java.time.Instant

data class DtAppStatusResponse(
    val appRunning: Boolean,
    val startedAt: String?,
    val profileHint: String,
    val components: List<DtComponentHealthResponse>
)

data class DtComponentHealthResponse(
    val id: String,
    val type: String,
    val status: String,
    val alive: Boolean,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

data class DtAppActionResponse(
    val successful: Boolean,
    val message: String,
    val timestamp: String = Instant.now().toString(),
    val details: Map<String, Any?> = emptyMap()
)

data class DemoTransportRequest(
    val durationSeconds: Double = 5.0,
    val payloadWeightKg: Double? = null,
    val startValue: Boolean = true,
    val endValue: Boolean = true,
    val runEngineCycle: Boolean = true
)

data class DemoValueRequest(
    val dataPointId: String,
    val value: Any?,
    val sourceComponentId: String = "app-demo-source",
    val runEngineCycle: Boolean = false
)
