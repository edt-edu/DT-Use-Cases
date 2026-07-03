package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.service.AbstractDtService

interface ServiceRequirementStore {
    suspend fun registerRequirement(requirement: ServiceDataPointRequirement): ServiceDataPointRequirement

    suspend fun registerRequirements(serviceId: ServiceId, dataPointIds: Set<DataPointId>)

    suspend fun registerService(service: AbstractDtService)

    suspend fun requirements(serviceId: ServiceId): List<ServiceDataPointRequirement>

    suspend fun removeRequirement(serviceId: ServiceId, dataPointId: DataPointId): Boolean

    suspend fun checkServiceReadiness(serviceId: ServiceId): ServiceStartCheck

    suspend fun checkServiceReadiness(service: AbstractDtService): ServiceStartCheck
}
