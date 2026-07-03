package de.unistuttgart.isw.dtengine.model

import de.unistuttgart.isw.dtengine.core.DataValue
import de.unistuttgart.isw.dtengine.core.DtComponent
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.ModelPropertyId

/**
 * Abstract manager for the Digital Twin model/data representation.
 *
 * Later implementations can use SQLite, BaSyx/AAS, an in-memory model, a graph model,
 * or a combination of model storage and runtime cache.
 */
abstract class AbstractModelManager : DtComponent {
    abstract suspend fun validateSyntax(candidate: ModelMutation): ValidationResult

    abstract suspend fun validateConformance(candidate: ModelMutation): ValidationResult

    abstract suspend fun getProperty(propertyId: ModelPropertyId): ModelProperty?

    abstract suspend fun createProperty(property: ModelProperty): ModelProperty

    abstract suspend fun updateProperty(propertyId: ModelPropertyId, value: DataValue): ModelProperty

    abstract suspend fun deleteProperty(propertyId: ModelPropertyId): Boolean

    abstract suspend fun resolvePropertyId(dataPointOrExternalId: String): ModelPropertyId?

    abstract suspend fun handleGatewayEvent(event: DtEvent): List<DtEvent>

    abstract suspend fun buildServiceAnswerEvent(requestEvent: DtEvent): DtEvent
}

data class ModelProperty(
    val id: ModelPropertyId,
    val name: String,
    val value: DataValue? = null,
    val type: String? = null,
    val semanticId: String? = null,
    val parentPath: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ModelMutation(
    val operation: ModelOperation,
    val property: ModelProperty,
    val reason: String = ""
)

enum class ModelOperation {
    CREATE,
    UPDATE,
    DELETE
}

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
