package de.unistuttgart.isw.dtengine.model.aas

import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.model.ModelProperty

/**
 * Converts DT-internal identifiers to AAS-compatible idShort paths.
 *
 * AAS idShort values are kept conservative here: letters, digits, and
 * underscores only, with a leading letter. This avoids invalid paths when MQTT
 * topic suffixes contain dashes or when machine ids start with a number.
 */
object AasPathCodec {
    private val nonIdShortCharacter = Regex("[^A-Za-z0-9_]")
    private val repeatedUnderscore = Regex("_+")

    fun sanitizeIdShort(raw: String, fallback: String = "property"): String {
        val trimmed = raw.trim().ifBlank { fallback }
        val cleaned = trimmed
            .replace(nonIdShortCharacter, "_")
            .replace(repeatedUnderscore, "_")
            .trim('_')
            .ifBlank { fallback }
        return if (cleaned.first().isLetter()) cleaned else "p_$cleaned"
    }

    fun splitParentPath(parentPath: String?): List<String> = parentPath
        ?.split('/', '.', ':')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.map { sanitizeIdShort(it) }
        ?: emptyList()

    fun addressForProperty(
        property: ModelProperty,
        shellId: String,
        defaultSubmodelId: String,
        defaultParentPath: String? = null
    ): AasPropertyAddress {
        val submodelId = property.metadata["aasSubmodelId"] ?: defaultSubmodelId
        val explicitPath = property.metadata["aasIdShortPath"]
        val idShortPath = if (!explicitPath.isNullOrBlank()) {
            splitParentPath(explicitPath)
        } else {
            val parent = property.metadata["aasParentPath"] ?: property.parentPath ?: defaultParentPath
            val idShort = property.metadata["aasIdShort"]
                ?: property.name.ifBlank { property.id.value.substringAfterLast('.') }
            splitParentPath(parent) + sanitizeIdShort(idShort)
        }
        return AasPropertyAddress(shellId, submodelId, idShortPath)
    }

    fun addressForModelPropertyId(
        propertyId: ModelPropertyId,
        shellId: String,
        defaultSubmodelId: String,
        defaultParentPath: String? = null
    ): AasPropertyAddress {
        val tokens = propertyId.value.split('.', '/', ':')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val idShort = tokens.lastOrNull() ?: propertyId.value
        val parentTokens = tokens.dropLast(1)
        val parentPath = if (defaultParentPath.isNullOrBlank()) {
            parentTokens.joinToString("/")
        } else {
            listOf(defaultParentPath, parentTokens.joinToString("/")).filter { it.isNotBlank() }.joinToString("/")
        }
        return AasPropertyAddress(shellId, defaultSubmodelId, splitParentPath(parentPath) + sanitizeIdShort(idShort))
    }

    fun dataPointIdToModelPropertyId(dataPointId: String): ModelPropertyId = ModelPropertyId(dataPointId)
}
