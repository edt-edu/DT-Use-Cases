package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.ServiceType
import java.time.Instant

/**
 * Persistent description of one service known to the DT engine.
 *
 * The registry is intentionally technical and explicit: it records the service id,
 * service type, required data points, required model properties, required functions,
 * and produced data points. Later AAS/BaSyx descriptions can be generated from this.
 */
data class ServiceDescriptor(
    val serviceId: ServiceId,
    val componentId: ComponentId,
    val serviceType: ServiceType,
    val description: String = "",
    val requiredDataPoints: Set<DataPointId> = emptySet(),
    val requiredModelProperties: Set<ModelPropertyId> = emptySet(),
    val requiredFunctions: Set<String> = emptySet(),
    val producedDataPoints: Set<DataPointId> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class ServiceModelRequirement(
    val serviceId: ServiceId,
    val modelPropertyId: ModelPropertyId,
    val required: Boolean = true,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap()
)

data class ServiceFunctionRequirement(
    val serviceId: ServiceId,
    val functionName: String,
    val required: Boolean = true,
    val description: String = "",
    val metadata: Map<String, String> = emptyMap()
)

fun AbstractDtService.toServiceDescriptor(
    metadata: Map<String, String> = emptyMap()
): ServiceDescriptor = ServiceDescriptor(
    serviceId = serviceId,
    componentId = id,
    serviceType = serviceType,
    description = description,
    requiredDataPoints = requiredDataPoints,
    requiredModelProperties = requiredModelProperties,
    requiredFunctions = requiredFunctions,
    producedDataPoints = producedDataPoints,
    metadata = metadata
)
