package de.unistuttgart.isw.dtengine.model.aas

import java.time.Instant

/**
 * Minimal AAS domain model used by the DT engine.
 *
 * The classes are intentionally small and implementation-neutral. They can be
 * mapped to a local in-memory AAS representation, to BaSyx, or to another AAS
 * repository later without changing the DT engine abstraction.
 */
data class AasShellDescriptor(
    val id: String,
    val idShort: String,
    val description: String = "",
    val submodelIds: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
)

data class AasSubmodelDescriptor(
    val id: String,
    val idShort: String,
    val semanticId: String? = null,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap()
)

data class AasPropertyAddress(
    val shellId: String,
    val submodelId: String,
    val idShortPath: List<String>
) {
    init {
        require(shellId.isNotBlank()) { "AAS shell id must not be blank" }
        require(submodelId.isNotBlank()) { "AAS submodel id must not be blank" }
        require(idShortPath.isNotEmpty()) { "AAS idShort path must not be empty" }
    }

    val idShort: String = idShortPath.last()
    val path: String = idShortPath.joinToString("/")
    val externalId: String = "$shellId::$submodelId::$path"
}

data class AasProperty(
    val address: AasPropertyAddress,
    val idShort: String = address.idShort,
    val value: Any?,
    val valueType: AasValueType = AasValueType.fromValue(value),
    val semanticId: String? = null,
    val category: String? = null,
    val description: String = "",
    val observedAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
)

enum class AasValueType {
    BOOLEAN,
    INTEGER,
    LONG,
    DOUBLE,
    STRING,
    JSON,
    UNKNOWN;

    companion object {
        fun fromValue(value: Any?): AasValueType = when (value) {
            null -> UNKNOWN
            is Boolean -> BOOLEAN
            is Int -> INTEGER
            is Long -> LONG
            is Float -> DOUBLE
            is Double -> DOUBLE
            is Number -> DOUBLE
            is String -> STRING
            is Map<*, *> -> JSON
            is List<*> -> JSON
            else -> STRING
        }

        fun fromModelType(type: String?): AasValueType = when (type?.lowercase()) {
            null, "" -> UNKNOWN
            "boolean", "bool" -> BOOLEAN
            "integer", "int" -> INTEGER
            "long" -> LONG
            "double", "float", "number" -> DOUBLE
            "string", "text" -> STRING
            "json", "object", "array" -> JSON
            else -> UNKNOWN
        }
    }
}

data class AasRepositoryHealth(
    val alive: Boolean,
    val message: String = "",
    val details: Map<String, String> = emptyMap()
)
