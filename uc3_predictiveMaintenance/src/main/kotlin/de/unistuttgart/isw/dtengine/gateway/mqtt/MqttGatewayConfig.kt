package de.unistuttgart.isw.dtengine.gateway.mqtt

data class MqttGatewayConfig(
    val brokerUri: String = "tcp://localhost:1883",
    val clientId: String = "dt-engine-mqtt-gateway",
    val machineId: String,
    val machineType: MachineType,
    val topicPrefix: String = "",
    val qos: Int = 0,
    val retained: Boolean = false,
    val cleanSession: Boolean = true,
    val username: String? = null,
    val password: String? = null,
    val connectionTimeoutSeconds: Int = 10,
    val keepAliveIntervalSeconds: Int = 30,
    val operationTimeoutMillis: Long = 5_000,
    val allowUnknownCommandTopics: Boolean = false,
    /**
     * Enables a wildcard subscription in addition to the catalog subscriptions.
     * This is useful when the broker receives topics that are not yet part of the
     * static machine catalog. MQTT cannot be queried for all known topics; the
     * gateway can only learn topics when a message is published or retained.
     */
    val discoveryEnabled: Boolean = false,
    val discoveryTopicFilters: List<String> = emptyList(),
    val registerUnknownTopics: Boolean = false,
    val inferUnknownTopicValueType: Boolean = true,
    val unknownTopicValueType: MqttValueType = MqttValueType.STRING
)
