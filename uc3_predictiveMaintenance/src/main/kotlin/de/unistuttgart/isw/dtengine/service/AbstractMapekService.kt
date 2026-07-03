package de.unistuttgart.isw.dtengine.service

import de.unistuttgart.isw.dtengine.core.CommandRequest
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent

/**
 * Abstract MAPE-K loop.
 *
 * Concrete services implement monitor, analyze, plan, execute, and access to knowledge.
 */
abstract class AbstractMapekService : AbstractDtService() {
    final override val serviceType: ServiceType = ServiceType.MAPEK

    abstract suspend fun monitor(context: ServiceTickContext): MapekObservation

    abstract suspend fun analyze(observation: MapekObservation): MapekAnalysis

    abstract suspend fun plan(analysis: MapekAnalysis): MapekPlan

    abstract suspend fun execute(plan: MapekPlan): MapekExecutionResult

    abstract suspend fun knowledge(): MapekKnowledge

    override suspend fun tick(context: ServiceTickContext): List<DtEvent> {
        val observation = monitor(context)
        val analysis = analyze(observation)
        val plan = plan(analysis)
        val result = execute(plan)
        return result.events
    }
}

data class MapekObservation(
    val values: List<DataValue>,
    val events: List<DtEvent> = emptyList(),
    val notes: List<String> = emptyList()
)

data class MapekAnalysis(
    val deviationDetected: Boolean,
    val severity: String = "none",
    val findings: List<String> = emptyList()
)

data class MapekPlan(
    val actions: List<MapekAction>,
    val rationale: String = ""
)

data class MapekAction(
    val name: String,
    val command: CommandRequest? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class MapekExecutionResult(
    val successful: Boolean,
    val events: List<DtEvent> = emptyList(),
    val messages: List<String> = emptyList()
)

data class MapekKnowledge(
    val rules: List<String> = emptyList(),
    val thresholds: Map<String, Double> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)
