package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.core.ServiceId

interface ServiceRegistryStore {
    suspend fun registerServiceDescriptor(descriptor: ServiceDescriptor): ServiceDescriptor

    suspend fun serviceDescriptor(serviceId: ServiceId): ServiceDescriptor?

    suspend fun serviceDescriptors(): List<ServiceDescriptor>

    suspend fun registerModelRequirement(requirement: ServiceModelRequirement): ServiceModelRequirement

    suspend fun registerModelRequirements(serviceId: ServiceId, modelPropertyIds: Set<ModelPropertyId>)

    suspend fun modelRequirements(serviceId: ServiceId): List<ServiceModelRequirement>

    suspend fun registerFunctionRequirement(requirement: ServiceFunctionRequirement): ServiceFunctionRequirement

    suspend fun registerFunctionRequirements(serviceId: ServiceId, functionNames: Set<String>)

    suspend fun functionRequirements(serviceId: ServiceId): List<ServiceFunctionRequirement>

    suspend fun removeServiceDescriptor(serviceId: ServiceId): Boolean
}
