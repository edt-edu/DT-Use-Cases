package de.unistuttgart.isw.dtengine.core

interface DtComponent {
    val id: ComponentId
    val description: String
    val status: ComponentStatus

    suspend fun start()
    suspend fun stop()
    suspend fun health(): HealthStatus
}

enum class ComponentStatus {
    CREATED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    DEGRADED,
    FAILED
}

data class HealthStatus(
    val alive: Boolean,
    val status: ComponentStatus,
    val message: String = "",
    val details: Map<String, String> = emptyMap()
)
