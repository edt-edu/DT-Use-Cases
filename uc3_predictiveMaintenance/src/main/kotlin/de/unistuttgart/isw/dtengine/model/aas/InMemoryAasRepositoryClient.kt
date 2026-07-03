package de.unistuttgart.isw.dtengine.model.aas

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class InMemoryAasRepositoryClient : AasRepositoryClient {
    private val running = AtomicBoolean(false)
    private val shells = ConcurrentHashMap<String, AasShellDescriptor>()
    private val submodels = ConcurrentHashMap<String, AasSubmodelDescriptor>()
    private val shellToSubmodels = ConcurrentHashMap<String, MutableSet<String>>()
    private val properties = ConcurrentHashMap<String, AasProperty>()

    override suspend fun start() {
        running.set(true)
    }

    override suspend fun stop() {
        running.set(false)
    }

    override suspend fun health(): AasRepositoryHealth = AasRepositoryHealth(
        alive = running.get(),
        message = if (running.get()) "local AAS repository is running" else "local AAS repository is stopped",
        details = mapOf(
            "shells" to shells.size.toString(),
            "submodels" to submodels.size.toString(),
            "properties" to properties.size.toString()
        )
    )

    override suspend fun ensureShell(shell: AasShellDescriptor) {
        shells[shell.id] = shell.copy(
            submodelIds = shell.submodelIds + shellToSubmodels.getOrDefault(shell.id, mutableSetOf())
        )
        shellToSubmodels.computeIfAbsent(shell.id) { mutableSetOf() }.addAll(shell.submodelIds)
    }

    override suspend fun ensureSubmodel(shellId: String, submodel: AasSubmodelDescriptor) {
        require(shells.containsKey(shellId)) { "AAS shell '$shellId' does not exist" }
        submodels[submodel.id] = submodel
        shellToSubmodels.computeIfAbsent(shellId) { mutableSetOf() }.add(submodel.id)
        shells.computeIfPresent(shellId) { _, shell -> shell.copy(submodelIds = shell.submodelIds + submodel.id) }
    }

    override suspend fun getProperty(address: AasPropertyAddress): AasProperty? = properties[address.externalId]

    override suspend fun upsertProperty(property: AasProperty): AasProperty {
        require(shells.containsKey(property.address.shellId)) {
            "AAS shell '${property.address.shellId}' does not exist"
        }
        require(shellToSubmodels[property.address.shellId]?.contains(property.address.submodelId) == true) {
            "AAS submodel '${property.address.submodelId}' is not attached to shell '${property.address.shellId}'"
        }
        properties[property.address.externalId] = property
        return property
    }

    override suspend fun deleteProperty(address: AasPropertyAddress): Boolean = properties.remove(address.externalId) != null

    override suspend fun findPropertyByMetadata(key: String, value: String): AasProperty? = properties.values.firstOrNull {
        it.metadata[key] == value
    }

    override suspend fun listProperties(shellId: String?, submodelId: String?): List<AasProperty> = properties.values
        .asSequence()
        .filter { shellId == null || it.address.shellId == shellId }
        .filter { submodelId == null || it.address.submodelId == submodelId }
        .sortedBy { it.address.externalId }
        .toList()
}
