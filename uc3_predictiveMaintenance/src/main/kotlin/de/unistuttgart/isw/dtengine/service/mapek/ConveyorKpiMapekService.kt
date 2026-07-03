package de.unistuttgart.isw.dtengine.service.mapek

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ComponentStatus
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataQuality
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventSeverity
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.core.HealthStatus
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractMapekService
import de.unistuttgart.isw.dtengine.service.MapekAction
import de.unistuttgart.isw.dtengine.service.MapekAnalysis
import de.unistuttgart.isw.dtengine.service.MapekExecutionResult
import de.unistuttgart.isw.dtengine.service.MapekKnowledge
import de.unistuttgart.isw.dtengine.service.MapekObservation
import de.unistuttgart.isw.dtengine.service.MapekPlan
import de.unistuttgart.isw.dtengine.service.ServiceReadiness
import de.unistuttgart.isw.dtengine.service.ServiceRequest
import de.unistuttgart.isw.dtengine.service.ServiceResponse
import de.unistuttgart.isw.dtengine.service.ServiceStartContext
import de.unistuttgart.isw.dtengine.service.ServiceTickContext
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * MAPE-K service for a conveyor segment between two grippers.
 *
 * Monitor: ingest start/end sensor events and optional position values.
 * Analyze: calculate transport time, productivity and conveyor integrity.
 * Plan: map integrity to NORMAL, WARNING or EMERGENCY.
 * Execute: expose/write the current state and call the open sendBack(state) hook.
 */
class ConveyorKpiMapekService(
    private val config: ConveyorKpiMapekConfig = ConveyorKpiMapekConfig(),
    private val dataStore: DtDataStore? = null,
    private val modelManager: AbstractModelManager? = null
) : AbstractMapekService() {

    override val id: ComponentId = ComponentId(config.componentId)
    override val serviceId: ServiceId = ServiceId(config.serviceId)
    override val description: String = config.description

    override val requiredDataPoints: Set<DataPointId> = buildSet {
        add(DataPointId(config.startSignalDataPointId))
        add(DataPointId(config.endSignalDataPointId))
        addAll(config.positionDataPointIds.filter { it.isNotBlank() }.map { DataPointId(it) })
    }

    override val requiredModelProperties: Set<ModelPropertyId> =
        config.requiredModelPropertyIds.filter { it.isNotBlank() }.map { ModelPropertyId(it) }.toSet()

    override val requiredFunctions: Set<String> = setOf("sendBack(state)")

    override val producedDataPoints: Set<DataPointId> = setOf(
        DataPointId(config.outputStateDataPointId),
        DataPointId(config.outputTransportTimeMillisDataPointId),
        DataPointId(config.outputPayloadWeightKgDataPointId),
        DataPointId(config.outputProductivityDataPointId),
        DataPointId(config.outputIntegrityPercentDataPointId)
    )

    private val statusRef = AtomicReference(ComponentStatus.CREATED)
    private var startSignalWasActive: Boolean = false
    private var endSignalWasActive: Boolean = false
    private var activeTransportStart: ProcessEndpoint? = null
    private var latestMeasurement: ConveyorTransportMeasurement? = null
    private var latestAnalysis: ConveyorKpiAnalysis? = null
    private var currentState: ConveyorAlarmState = ConveyorAlarmState.UNKNOWN
    private var lastExecutedMeasurementKey: String? = null
    private var payloadWeightKg: Double = config.payloadWeightKg

    override val status: ComponentStatus
        get() = statusRef.get()

    override suspend fun start() {
        statusRef.set(ComponentStatus.RUNNING)
    }

    override suspend fun stop() {
        statusRef.set(ComponentStatus.STOPPED)
    }

    override suspend fun health(): HealthStatus = HealthStatus(
        alive = status == ComponentStatus.RUNNING,
        status = status,
        message = "Conveyor state is $currentState",
        details = mapOf(
            "serviceId" to serviceId.value,
            "currentState" to currentState.name,
            "payloadWeightKg" to payloadWeightKg.toString(),
            "latestTransportTimeMillis" to (latestMeasurement?.durationMillis?.toString() ?: "n/a"),
            "latestIntegrityPercent" to (latestAnalysis?.integrityPercent?.toString() ?: "n/a")
        )
    )

    override suspend fun canStart(context: ServiceStartContext): ServiceReadiness {
        val missing = requiredDataPoints.filterNot { it in context.availableDataPoints }.toSet()
        return ServiceReadiness(
            ready = missing.isEmpty(),
            missingDataPoints = missing,
            reason = if (missing.isEmpty()) "" else "Missing conveyor KPI input data points"
        )
    }

    override suspend fun handleEvent(event: DtEvent): List<ServiceRequest> {
        if (event.type != EventType.GATEWAY_DATA_RECEIVED && event.type != EventType.MODEL_PROPERTY_UPDATED) return emptyList()
        val dataPointId = event.payload["dataPointId"]?.toString()
            ?: event.payload["propertyId"]?.toString()
            ?: return emptyList()
        val value = DataValue(
            id = DataPointId(dataPointId),
            value = event.payload["value"],
            timestamp = event.timestamp,
            quality = event.payload["quality"]?.toString()?.let { runCatching { DataQuality.valueOf(it) }.getOrNull() } ?: DataQuality.GOOD,
            source = event.source,
            metadata = event.payload.mapValues { it.value?.toString().orEmpty() }
        )
        ingest(value)
        return emptyList()
    }

    override suspend fun handleRequest(request: ServiceRequest): ServiceResponse = when (request.operation) {
        "current-state", "get-state" -> ServiceResponse(
            request = request,
            successful = true,
            payload = statePayload(),
            message = "Current conveyor state"
        )
        "latest-analysis" -> ServiceResponse(
            request = request,
            successful = latestAnalysis != null,
            payload = statePayload() + mapOf(
                "analysisAvailable" to (latestAnalysis != null),
                "measurementAvailable" to (latestMeasurement != null)
            ),
            message = if (latestAnalysis != null) "Latest conveyor KPI analysis" else "No completed transport measurement yet"
        )
        "set-payload-weight" -> {
            val newWeight = request.payload["payloadWeightKg"]?.toString()?.toDoubleOrNull()
            if (newWeight == null || newWeight <= 0.0) {
                ServiceResponse(request, successful = false, message = "payloadWeightKg must be a positive number")
            } else {
                payloadWeightKg = newWeight
                ServiceResponse(
                    request = request,
                    successful = true,
                    payload = mapOf("payloadWeightKg" to payloadWeightKg),
                    message = "Payload weight updated"
                )
            }
        }
        "mapped-value", "ingest" -> {
            val dataPointId = request.payload["dataPointId"]?.toString()
                ?: request.payload["inputOrOutput"]?.toString()
            if (dataPointId.isNullOrBlank()) {
                ServiceResponse(request, successful = false, message = "mapped-value requires dataPointId")
            } else {
                val value = DataValue(
                    id = DataPointId(dataPointId),
                    value = request.payload["value"],
                    timestamp = request.payload["timestamp"]?.toString()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
                    quality = request.payload["quality"]?.toString()?.let { runCatching { DataQuality.valueOf(it) }.getOrNull() } ?: DataQuality.GOOD,
                    source = request.requestedBy,
                    metadata = request.payload.mapValues { it.value?.toString().orEmpty() }
                )
                ingest(value)
                ServiceResponse(
                    request = request,
                    successful = true,
                    payload = statePayload(),
                    message = "Value ingested by conveyor MAPE-K service"
                )
            }
        }
        else -> ServiceResponse(request, successful = false, message = "Unsupported operation '${request.operation}'")
    }

    override suspend fun monitor(context: ServiceTickContext): MapekObservation {
        val latestValues = dataStore?.readLatest(requiredDataPoints).orEmpty()
        latestValues.forEach { ingest(it) }
        return MapekObservation(
            values = latestValues,
            notes = listOfNotNull(
                latestMeasurement?.let { "Latest transport measurement: ${it.durationMillis} ms" },
                activeTransportStart?.let { "Transport in progress since ${it.timestamp}" }
            )
        )
    }

    override suspend fun analyze(observation: MapekObservation): MapekAnalysis {
        val measurement = latestMeasurement
            ?: return MapekAnalysis(
                deviationDetected = false,
                severity = "none",
                findings = listOf("No completed conveyor transport measurement yet")
            )

        val durationSeconds = max(measurement.durationMillis / 1000.0, 0.001)
        val productivity = payloadWeightKg / durationSeconds
        val baselineProductivity = config.baselinePayloadWeightKg / max(config.nominalTransportTimeSeconds, 0.001)
        val integrity = clamp((productivity / baselineProductivity) * 100.0, 0.0, 100.0)
        val state = stateForIntegrity(integrity)

        val analysis = ConveyorKpiAnalysis(
            measurement = measurement,
            payloadWeightKg = payloadWeightKg,
            productivityKgPerSecond = productivity,
            baselineProductivityKgPerSecond = baselineProductivity,
            integrityPercent = integrity,
            state = state
        )
        latestAnalysis = analysis
        currentState = state

        return MapekAnalysis(
            deviationDetected = state != ConveyorAlarmState.NORMAL,
            severity = state.severityLabel,
            findings = listOf(
                "transportTimeMillis=${measurement.durationMillis}",
                "payloadWeightKg=$payloadWeightKg",
                "productivityKgPerSecond=$productivity",
                "integrityPercent=$integrity",
                "state=${state.name}"
            )
        )
    }

    override suspend fun plan(analysis: MapekAnalysis): MapekPlan {
        val kpi = latestAnalysis ?: return MapekPlan(emptyList(), "No completed measurement available")
        if (lastExecutedMeasurementKey == kpi.measurement.measurementKey) {
            return MapekPlan(emptyList(), "Measurement was already executed")
        }
        return MapekPlan(
            actions = listOf(
                MapekAction(
                    name = "publish-conveyor-state",
                    payload = statePayload(kpi) + mapOf("targetFunction" to "sendBack(state)")
                )
            ),
            rationale = "Integrity ${kpi.integrityPercent.format(2)}% leads to ${kpi.state.name}"
        )
    }

    override suspend fun execute(plan: MapekPlan): MapekExecutionResult {
        val kpi = latestAnalysis ?: return MapekExecutionResult(successful = true, messages = listOf("No analysis available"))
        if (plan.actions.isEmpty()) return MapekExecutionResult(successful = true, messages = listOf(plan.rationale))

        val values = buildOutputValues(kpi)
        values.forEach { value ->
            dataStore?.writeValue(value)
            modelManager?.updateProperty(ModelPropertyId(value.id.value), value)
        }
        sendBack(kpi.state, statePayload(kpi))
        lastExecutedMeasurementKey = kpi.measurement.measurementKey

        val events = values.map { value ->
            DtEvent(
                type = EventType.MODEL_PROPERTY_UPDATED,
                source = id,
                severity = kpi.state.eventSeverity,
                payload = mapOf(
                    "serviceId" to serviceId.value,
                    "dataPointId" to value.id.value,
                    "propertyId" to value.id.value,
                    "value" to value.value,
                    "quality" to value.quality.name,
                    "conveyorState" to kpi.state.name,
                    "integrityPercent" to kpi.integrityPercent
                )
            )
        }
        return MapekExecutionResult(
            successful = true,
            events = events,
            messages = listOf("Conveyor state ${kpi.state.name} written back to DT")
        )
    }

    override suspend fun knowledge(): MapekKnowledge = MapekKnowledge(
        rules = listOf(
            "productivity = payloadWeightKg / transportTimeSeconds",
            "baselineProductivity = baselinePayloadWeightKg / nominalTransportTimeSeconds",
            "integrityPercent = productivity / baselineProductivity * 100",
            "if integrity < ${config.emergencyIntegrityThresholdPercent}% then EMERGENCY",
            "else if integrity < ${config.warningIntegrityThresholdPercent}% then WARNING",
            "else NORMAL"
        ),
        thresholds = mapOf(
            "warningIntegrityThresholdPercent" to config.warningIntegrityThresholdPercent,
            "emergencyIntegrityThresholdPercent" to config.emergencyIntegrityThresholdPercent,
            "nominalTransportTimeSeconds" to config.nominalTransportTimeSeconds,
            "baselinePayloadWeightKg" to config.baselinePayloadWeightKg
        ),
        metadata = mapOf(
            "startSignalDataPointId" to config.startSignalDataPointId,
            "endSignalDataPointId" to config.endSignalDataPointId,
            "outputStateDataPointId" to config.outputStateDataPointId
        )
    )

    /**
     * Extension point for a later engine/model/AAS integration.
     * The current implementation writes DT data/model values and leaves concrete dispatch open.
     */
    protected open suspend fun sendBack(state: ConveyorAlarmState, payload: Map<String, Any?>) {
        // Intentionally empty. Override in a concrete integration to call the engine, AAS, REST API, etc.
    }

    private suspend fun ingest(value: DataValue) {
        if (value.id.value.endsWith(".sim.cycle-end")) {
            ingestCycleEndDiagnostic(value)
            return
        }

        val signal = value.value.toBooleanSignal() ?: return
        when (value.id.value) {
            config.startSignalDataPointId -> ingestStartSignal(signal, value.timestamp)
            config.endSignalDataPointId -> ingestEndSignal(signal, value.timestamp)
        }
    }

    private suspend fun ingestCycleEndDiagnostic(value: DataValue) {
        val payload = value.value?.toString().orEmpty()
        val travelMillis = extractLongJsonField(payload, "actual_travel_ms")
            ?: extractLongJsonField(payload, "measured_cycle_until_end_ms")
            ?: return

        val endTimestamp = value.timestamp
        val startTimestamp = endTimestamp.minusMillis(travelMillis.coerceAtLeast(0L))
        latestMeasurement = ConveyorTransportMeasurement(
            start = ProcessEndpoint(
                dataPointId = DataPointId(config.startSignalDataPointId),
                timestamp = startTimestamp,
                positions = readPositions()
            ),
            end = ProcessEndpoint(
                dataPointId = DataPointId(config.endSignalDataPointId),
                timestamp = endTimestamp,
                positions = readPositions()
            )
        )
        activeTransportStart = null
    }

    private fun extractLongJsonField(payload: String, fieldName: String): Long? {
        val pattern = Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(payload)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private suspend fun ingestStartSignal(active: Boolean, timestamp: Instant) {
        val risingEdge = active && !startSignalWasActive
        startSignalWasActive = active
        if (risingEdge) {
            activeTransportStart = ProcessEndpoint(
                dataPointId = DataPointId(config.startSignalDataPointId),
                timestamp = timestamp,
                positions = readPositions()
            )
        }
    }

    private suspend fun ingestEndSignal(active: Boolean, timestamp: Instant) {
        val risingEdge = active && !endSignalWasActive
        endSignalWasActive = active
        if (!risingEdge) return
        val start = activeTransportStart ?: return
        if (timestamp.isBefore(start.timestamp)) return
        latestMeasurement = ConveyorTransportMeasurement(
            start = start,
            end = ProcessEndpoint(
                dataPointId = DataPointId(config.endSignalDataPointId),
                timestamp = timestamp,
                positions = readPositions()
            )
        )
        activeTransportStart = null
    }

    private suspend fun readPositions(): Map<DataPointId, Any?> = config.positionDataPointIds
        .filter { it.isNotBlank() }
        .map { DataPointId(it) }
        .associateWith { dataStore?.readLatest(it)?.value }
        .filterValues { it != null }

    private fun stateForIntegrity(integrity: Double): ConveyorAlarmState = when {
        integrity < config.emergencyIntegrityThresholdPercent -> ConveyorAlarmState.EMERGENCY
        integrity < config.warningIntegrityThresholdPercent -> ConveyorAlarmState.WARNING
        else -> ConveyorAlarmState.NORMAL
    }

    private fun buildOutputValues(kpi: ConveyorKpiAnalysis): List<DataValue> = listOf(
        outputValue(config.outputStateDataPointId, kpi.state.name, "STRING", kpi),
        outputValue(config.outputTransportTimeMillisDataPointId, kpi.measurement.durationMillis, "LONG", kpi),
        outputValue(config.outputPayloadWeightKgDataPointId, kpi.payloadWeightKg, "DOUBLE", kpi),
        outputValue(config.outputProductivityDataPointId, kpi.productivityKgPerSecond, "DOUBLE", kpi),
        outputValue(config.outputIntegrityPercentDataPointId, kpi.integrityPercent, "DOUBLE", kpi)
    )

    private fun outputValue(dataPointId: String, value: Any?, valueType: String, kpi: ConveyorKpiAnalysis): DataValue = DataValue(
        id = DataPointId(dataPointId),
        value = value,
        timestamp = Instant.now(),
        quality = DataQuality.GOOD,
        source = id,
        metadata = mapOf(
            "serviceId" to serviceId.value,
            "valueType" to valueType,
            "transportMeasurementKey" to kpi.measurement.measurementKey,
            "startDataPointId" to kpi.measurement.start.dataPointId.value,
            "endDataPointId" to kpi.measurement.end.dataPointId.value,
            "state" to kpi.state.name,
            "integrityPercent" to kpi.integrityPercent.toString()
        )
    )

    private fun statePayload(kpi: ConveyorKpiAnalysis? = latestAnalysis): Map<String, Any?> = mapOf(
        "serviceId" to serviceId.value,
        "state" to currentState.name,
        "payloadWeightKg" to payloadWeightKg,
        "transportTimeMillis" to kpi?.measurement?.durationMillis,
        "productivityKgPerSecond" to kpi?.productivityKgPerSecond,
        "baselineProductivityKgPerSecond" to kpi?.baselineProductivityKgPerSecond,
        "integrityPercent" to kpi?.integrityPercent,
        "startTimestamp" to kpi?.measurement?.start?.timestamp?.toString(),
        "endTimestamp" to kpi?.measurement?.end?.timestamp?.toString()
    )

    private fun Any?.toBooleanSignal(): Boolean? = when (this) {
        is Boolean -> this
        is Number -> this.toInt() != 0
        is String -> when (trim().lowercase()) {
            "true", "1", "on", "active", "yes" -> true
            "false", "0", "off", "inactive", "no" -> false
            else -> null
        }
        else -> null
    }

    private fun clamp(value: Double, lower: Double, upper: Double): Double = min(max(value, lower), upper)

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}

@ConfigurationProperties(prefix = "dt.services.conveyor-kpi-mapek")
data class ConveyorKpiMapekConfig(
    val enabled: Boolean = false,
    val serviceId: String = "conveyor-kpi-mapek",
    val componentId: String = "conveyor-kpi-mapek-service",
    val description: String = "MAPE-K service for conveyor transport time, productivity and integrity",
    val startSignalDataPointId: String = "1-1-conveyor.phototransistor-feed-station",
    val endSignalDataPointId: String = "1-1-conveyor.phototransistor-swap-station",
    val positionDataPointIds: List<String> = emptyList(),
    val requiredModelPropertyIds: List<String> = emptyList(),
    val payloadWeightKg: Double = 1.0,
    val baselinePayloadWeightKg: Double = 1.0,
    val nominalTransportTimeSeconds: Double = 5.0,
    val warningIntegrityThresholdPercent: Double = 30.0,
    val emergencyIntegrityThresholdPercent: Double = 10.0,
    val outputStateDataPointId: String = "conveyor.state",
    val outputTransportTimeMillisDataPointId: String = "conveyor.transport-time-ms",
    val outputPayloadWeightKgDataPointId: String = "conveyor.payload-weight-kg",
    val outputProductivityDataPointId: String = "conveyor.productivity-kg-per-second",
    val outputIntegrityPercentDataPointId: String = "conveyor.integrity-percent"
)

data class ProcessEndpoint(
    val dataPointId: DataPointId,
    val timestamp: Instant,
    val positions: Map<DataPointId, Any?> = emptyMap()
)

data class ConveyorTransportMeasurement(
    val start: ProcessEndpoint,
    val end: ProcessEndpoint
) {
    val durationMillis: Long = Duration.between(start.timestamp, end.timestamp).toMillis()
    val measurementKey: String = "${start.timestamp}-${end.timestamp}"
}

data class ConveyorKpiAnalysis(
    val measurement: ConveyorTransportMeasurement,
    val payloadWeightKg: Double,
    val productivityKgPerSecond: Double,
    val baselineProductivityKgPerSecond: Double,
    val integrityPercent: Double,
    val state: ConveyorAlarmState
)

enum class ConveyorAlarmState(
    val severityLabel: String,
    val eventSeverity: EventSeverity
) {
    UNKNOWN("none", EventSeverity.DEBUG),
    NORMAL("none", EventSeverity.INFO),
    WARNING("warning", EventSeverity.WARN),
    EMERGENCY("emergency", EventSeverity.CRITICAL)
}
