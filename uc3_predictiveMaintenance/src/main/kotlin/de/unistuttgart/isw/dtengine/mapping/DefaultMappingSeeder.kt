package de.unistuttgart.isw.dtengine.mapping

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.MappingId
import de.unistuttgart.isw.dtengine.core.ModelPropertyId
import de.unistuttgart.isw.dtengine.gateway.mqtt.MachineType
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttGatewayProperties
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttPortDirection
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttTopicCatalog
import de.unistuttgart.isw.dtengine.service.mapek.ConveyorKpiMapekConfig

/**
 * Seeds the standard wiring for the current prototype:
 * MQTT gateway values -> SQLite runtime data + AAS properties,
 * service outputs -> SQLite runtime data + AAS state properties.
 */
class DefaultMappingSeeder(
    private val registry: AbstractMappingRegistry,
    private val properties: MappingProperties,
    private val mqttProperties: MqttGatewayProperties? = null,
    private val conveyorConfig: ConveyorKpiMapekConfig? = null,
    private val catalog: MqttTopicCatalog = MqttTopicCatalog.default()
) {
    suspend fun seed(): List<DtMapping> {
        if (!properties.enabled || !properties.autoSeed) return emptyList()
        val mappings = buildList {
            if (properties.seedMqttGatewayMappings) addAll(mqttMappings())
            if (properties.seedConveyorMapekMappings) addAll(conveyorMapekMappings())
        }.distinctBy { it.id }
        mappings.forEach { registry.add(it) }
        return mappings
    }

    private fun mqttMappings(): List<DtMapping> {
        val mqtt = mqttProperties ?: return emptyList()
        val machineType = MachineType.parse(mqtt.machineType)
        val gatewayId = ComponentId(mqtt.componentId)
        val databaseId = ComponentId(properties.databaseComponentId)
        val modelId = ComponentId(properties.modelComponentId)
        return catalog.forMachine(machineType)
            .filter { it.direction == MqttPortDirection.OUT || properties.includeWritableMqttDataPoints }
            .flatMap { spec ->
                val dataPointId = spec.dataPointId(mqtt.machineId)
                val commonMetadata = mapOf(
                    "machineId" to mqtt.machineId,
                    "machineType" to machineType.name,
                    "topicSuffix" to spec.topicSuffix,
                    "topic" to spec.topic(mqtt.machineId, mqtt.topicPrefix),
                    "port" to spec.port,
                    "description" to spec.description,
                    "seededBy" to "DefaultMappingSeeder"
                )
                listOf(
                    DtMapping(
                        id = MappingId("mqtt:${dataPointId.value}:to-db"),
                        source = GatewayEndpoint(gatewayId, dataPointId),
                        target = DatabaseEndpoint(databaseId, table = "data_values", column = "value", key = dataPointId.value),
                        direction = MappingDirection.GATEWAY_TO_DATABASE,
                        metadata = commonMetadata + mapOf("targetKind" to "latest-and-history")
                    ),
                    DtMapping(
                        id = MappingId("mqtt:${dataPointId.value}:to-aas"),
                        source = GatewayEndpoint(gatewayId, dataPointId),
                        target = ModelEndpoint(modelId, ModelPropertyId(dataPointId.value)),
                        direction = MappingDirection.GATEWAY_TO_MODEL,
                        metadata = commonMetadata + mapOf("targetKind" to "aas-property")
                    )
                )
            }
    }

    private fun conveyorMapekMappings(): List<DtMapping> {
        val config = conveyorConfig ?: return emptyList()
        val serviceComponent = ComponentId(config.componentId)
        val databaseId = ComponentId(properties.databaseComponentId)
        val modelId = ComponentId(properties.modelComponentId)

        val inputIds = buildSet {
            add(DataPointId(config.startSignalDataPointId))
            add(DataPointId(config.endSignalDataPointId))
            addAll(config.positionDataPointIds.filter { it.isNotBlank() }.map { DataPointId(it) })
        }
        val outputIds = listOf(
            DataPointId(config.outputStateDataPointId),
            DataPointId(config.outputTransportTimeMillisDataPointId),
            DataPointId(config.outputPayloadWeightKgDataPointId),
            DataPointId(config.outputProductivityDataPointId),
            DataPointId(config.outputIntegrityPercentDataPointId)
        )

        val inputMappings = inputIds.flatMap { dataPointId ->
            listOf(
                DtMapping(
                    id = MappingId("db:${dataPointId.value}:to-service:${config.serviceId}"),
                    source = DatabaseEndpoint(databaseId, table = "data_values", column = "value", key = dataPointId.value),
                    target = ServiceEndpoint(serviceComponent, dataPointId.value),
                    direction = MappingDirection.DATABASE_TO_SERVICE,
                    metadata = mapOf("serviceId" to config.serviceId, "role" to "mapek-input")
                ),
                DtMapping(
                    id = MappingId("aas:${dataPointId.value}:to-service:${config.serviceId}"),
                    source = ModelEndpoint(modelId, ModelPropertyId(dataPointId.value)),
                    target = ServiceEndpoint(serviceComponent, dataPointId.value),
                    direction = MappingDirection.MODEL_TO_SERVICE,
                    metadata = mapOf("serviceId" to config.serviceId, "role" to "mapek-input")
                )
            )
        }

        val outputMappings = outputIds.flatMap { dataPointId ->
            listOf(
                DtMapping(
                    id = MappingId("service:${config.serviceId}:${dataPointId.value}:to-db"),
                    source = ServiceEndpoint(serviceComponent, dataPointId.value),
                    target = DatabaseEndpoint(databaseId, table = "data_values", column = "value", key = dataPointId.value),
                    direction = MappingDirection.SERVICE_TO_DATABASE,
                    metadata = mapOf("serviceId" to config.serviceId, "role" to "mapek-output")
                ),
                DtMapping(
                    id = MappingId("service:${config.serviceId}:${dataPointId.value}:to-aas"),
                    source = ServiceEndpoint(serviceComponent, dataPointId.value),
                    target = ModelEndpoint(modelId, ModelPropertyId(dataPointId.value)),
                    direction = MappingDirection.SERVICE_TO_MODEL,
                    metadata = mapOf("serviceId" to config.serviceId, "role" to "mapek-output")
                )
            )
        }
        return inputMappings + outputMappings
    }
}
