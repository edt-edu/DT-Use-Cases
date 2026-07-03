package de.unistuttgart.isw.dtengine.mapping

import de.unistuttgart.isw.dtengine.core.DataValue

/**
 * Small built-in transformer for mapping values.
 *
 * It deliberately supports only deterministic transformations that are useful
 * for the first DT prototype. More domain-specific mapping logic can later be
 * plugged in by replacing the ValueTransformer bean.
 */
class DefaultValueTransformer : ValueTransformer {
    override suspend fun transform(value: DataValue, mapping: DtMapping): DataValue = when (mapping.transformation.type) {
        TransformationType.IDENTITY -> value
        TransformationType.SCALE -> value.withNumeric { number -> number * mapping.transformation.parameter("factor", 1.0) }
        TransformationType.OFFSET -> value.withNumeric { number -> number + mapping.transformation.parameter("offset", 0.0) }
        TransformationType.UNIT_CONVERSION -> convertUnit(value, mapping.transformation)
        TransformationType.JSON_PATH -> jsonPath(value, mapping.transformation.expression)
        TransformationType.CUSTOM -> value.copy(
            metadata = value.metadata + mapOf(
                "transformation" to "CUSTOM",
                "expression" to mapping.transformation.expression.orEmpty()
            )
        )
    }.copy(
        metadata = value.metadata + mapOf(
            "mappingId" to mapping.id.value,
            "mappingDirection" to mapping.direction.name
        )
    )

    private fun TransformationSpec.parameter(name: String, default: Double): Double =
        parameters[name]?.toDoubleOrNull() ?: default

    private fun DataValue.withNumeric(operation: (Double) -> Double): DataValue {
        val numeric = value.toDoubleOrNull() ?: return this
        return copy(value = operation(numeric))
    }

    private fun convertUnit(value: DataValue, transformation: TransformationSpec): DataValue {
        val fromUnit = transformation.parameters["from"]?.lowercase()
        val toUnit = transformation.parameters["to"]?.lowercase()
        val numeric = value.value.toDoubleOrNull() ?: return value
        val converted = when (fromUnit to toUnit) {
            "ms" to "s", "millisecond" to "second", "milliseconds" to "seconds" -> numeric / 1000.0
            "s" to "ms", "second" to "millisecond", "seconds" to "milliseconds" -> numeric * 1000.0
            "percent" to "ratio", "%" to "ratio" -> numeric / 100.0
            "ratio" to "percent", "ratio" to "%" -> numeric * 100.0
            else -> numeric
        }
        return value.copy(
            value = converted,
            metadata = value.metadata + mapOf("unitFrom" to fromUnit.orEmpty(), "unitTo" to toUnit.orEmpty())
        )
    }

    private fun jsonPath(value: DataValue, expression: String?): DataValue {
        val key = expression
            ?.removePrefix("$.")
            ?.removePrefix(".")
            ?.takeIf { it.isNotBlank() }
            ?: return value
        val mapped = when (val raw = value.value) {
            is Map<*, *> -> raw[key]
            else -> null
        }
        return if (mapped == null) value else value.copy(value = mapped)
    }

    private fun Any?.toDoubleOrNull(): Double? = when (this) {
        null -> null
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> toString().toDoubleOrNull()
    }
}
