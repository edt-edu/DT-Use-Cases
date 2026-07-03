package de.unistuttgart.isw.dtengine.mapping

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dt.mappings")
class MappingProperties {
    var enabled: Boolean = true
    var autoSeed: Boolean = true
    var seedMqttGatewayMappings: Boolean = true
    var seedConveyorMapekMappings: Boolean = true
    var includeWritableMqttDataPoints: Boolean = false
    var gatewayComponentId: String = "mqtt-gateway"
    var modelComponentId: String = "aas-model-manager"
    var databaseComponentId: String = "sqlite-db"
    /**
     * When true, an unknown/discovered MQTT data point is automatically mapped
     * to SQLite and the AAS model on its first event. This keeps the DT useful
     * even when not every broker topic was known during startup.
     */
    var autoMapDiscoveredGatewayDataPoints: Boolean = true
}
