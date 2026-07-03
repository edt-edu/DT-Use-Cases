package de.unistuttgart.isw.dtengine.mapping

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.MappingId
import de.unistuttgart.isw.dtengine.core.ModelPropertyId

/**
 * A mapping defines how a value moves between gateway, model manager, service, or database.
 */
data class DtMapping(
    val id: MappingId,
    val source: MappingEndpoint,
    val target: MappingEndpoint,
    val direction: MappingDirection,
    val transformation: TransformationSpec = TransformationSpec.identity(),
    val enabled: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

sealed interface MappingEndpoint {
    val owner: ComponentId
}

data class GatewayEndpoint(
    override val owner: ComponentId,
    val dataPointId: DataPointId
) : MappingEndpoint

data class ModelEndpoint(
    override val owner: ComponentId,
    val propertyId: ModelPropertyId
) : MappingEndpoint

data class ServiceEndpoint(
    override val owner: ComponentId,
    val inputOrOutput: String
) : MappingEndpoint

data class DatabaseEndpoint(
    override val owner: ComponentId,
    val table: String,
    val column: String,
    val key: String? = null
) : MappingEndpoint

enum class MappingDirection {
    GATEWAY_TO_MODEL,
    GATEWAY_TO_DATABASE,
    GATEWAY_TO_SERVICE,
    MODEL_TO_GATEWAY,
    MODEL_TO_SERVICE,
    MODEL_TO_DATABASE,
    SERVICE_TO_MODEL,
    SERVICE_TO_DATABASE,
    SERVICE_TO_GATEWAY,
    DATABASE_TO_MODEL,
    DATABASE_TO_SERVICE,
    DATABASE_TO_GATEWAY,
    BIDIRECTIONAL
}

data class TransformationSpec(
    val type: TransformationType,
    val expression: String? = null,
    val parameters: Map<String, String> = emptyMap()
) {
    companion object {
        fun identity(): TransformationSpec = TransformationSpec(TransformationType.IDENTITY)
    }
}

enum class TransformationType {
    IDENTITY,
    SCALE,
    OFFSET,
    UNIT_CONVERSION,
    JSON_PATH,
    CUSTOM
}

interface ValueTransformer {
    suspend fun transform(value: DataValue, mapping: DtMapping): DataValue
}

abstract class AbstractMappingRegistry {
    abstract suspend fun all(): List<DtMapping>
    abstract suspend fun findById(id: MappingId): DtMapping?
    abstract suspend fun findForSource(source: MappingEndpoint): List<DtMapping>
    abstract suspend fun findForTarget(target: MappingEndpoint): List<DtMapping>
    abstract suspend fun add(mapping: DtMapping): DtMapping
    abstract suspend fun update(mapping: DtMapping): DtMapping
    abstract suspend fun delete(id: MappingId): Boolean
}
