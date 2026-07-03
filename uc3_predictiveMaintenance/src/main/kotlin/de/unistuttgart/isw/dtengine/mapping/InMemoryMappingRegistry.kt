package de.unistuttgart.isw.dtengine.mapping

import de.unistuttgart.isw.dtengine.core.MappingId
import java.util.concurrent.ConcurrentHashMap

class InMemoryMappingRegistry : AbstractMappingRegistry() {
    private val mappings = ConcurrentHashMap<MappingId, DtMapping>()

    override suspend fun all(): List<DtMapping> = mappings.values.sortedBy { it.id.value }

    override suspend fun findById(id: MappingId): DtMapping? = mappings[id]

    override suspend fun findForSource(source: MappingEndpoint): List<DtMapping> =
        mappings.values.filter { it.source == source }.sortedBy { it.id.value }

    override suspend fun findForTarget(target: MappingEndpoint): List<DtMapping> =
        mappings.values.filter { it.target == target }.sortedBy { it.id.value }

    override suspend fun add(mapping: DtMapping): DtMapping {
        mappings[mapping.id] = mapping
        return mapping
    }

    override suspend fun update(mapping: DtMapping): DtMapping {
        mappings[mapping.id] = mapping
        return mapping
    }

    override suspend fun delete(id: MappingId): Boolean = mappings.remove(id) != null
}
