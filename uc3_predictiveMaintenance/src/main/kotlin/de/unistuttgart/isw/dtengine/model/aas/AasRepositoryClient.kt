package de.unistuttgart.isw.dtengine.model.aas

/**
 * Repository boundary for AAS storage.
 *
 * The first implementation is local and in-memory. A BaSyx adapter can implement
 * this interface without touching the DT model manager or the services.
 */
interface AasRepositoryClient {
    suspend fun start() {}
    suspend fun stop() {}
    suspend fun health(): AasRepositoryHealth

    suspend fun ensureShell(shell: AasShellDescriptor)
    suspend fun ensureSubmodel(shellId: String, submodel: AasSubmodelDescriptor)

    suspend fun getProperty(address: AasPropertyAddress): AasProperty?
    suspend fun upsertProperty(property: AasProperty): AasProperty
    suspend fun deleteProperty(address: AasPropertyAddress): Boolean

    suspend fun findPropertyByMetadata(key: String, value: String): AasProperty?
    suspend fun listProperties(shellId: String? = null, submodelId: String? = null): List<AasProperty>
}
