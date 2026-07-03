package de.unistuttgart.isw.dtengine.gateway.mqtt

import de.unistuttgart.isw.dtengine.core.ComponentId
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dt.mqtt")
class MqttGatewayProperties {
    var enabled: Boolean = false
    var autoStart: Boolean = false
    var componentId: String = "mqtt-gateway"
    var description: String = "MQTT gateway"
    var brokerUri: String = "tcp://localhost:1883"
    var clientId: String = "dt-engine-mqtt-gateway"
    var machineId: String = "1-1-conveyor"
    var machineType: String = "CONVEYOR_BELT"
    var topicPrefix: String = ""
    var qos: Int = 0
    var retained: Boolean = false
    var cleanSession: Boolean = true
    var username: String? = null
    var password: String? = null
    var connectionTimeoutSeconds: Int = 10
    var keepAliveIntervalSeconds: Int = 30
    var operationTimeoutMillis: Long = 5_000
    var allowUnknownCommandTopics: Boolean = false
    var discoveryEnabled: Boolean = false
    var discoveryTopicFilters: List<String> = emptyList()
    var registerUnknownTopics: Boolean = false
    var inferUnknownTopicValueType: Boolean = true
    var unknownTopicValueType: String = "STRING"

    fun toConfig(): MqttGatewayConfig = MqttGatewayConfig(
        brokerUri = brokerUri,
        clientId = clientId,
        machineId = machineId,
        machineType = MachineType.parse(machineType),
        topicPrefix = topicPrefix,
        qos = qos,
        retained = retained,
        cleanSession = cleanSession,
        username = username,
        password = password,
        connectionTimeoutSeconds = connectionTimeoutSeconds,
        keepAliveIntervalSeconds = keepAliveIntervalSeconds,
        operationTimeoutMillis = operationTimeoutMillis,
        allowUnknownCommandTopics = allowUnknownCommandTopics,
        discoveryEnabled = discoveryEnabled,
        discoveryTopicFilters = discoveryTopicFilters,
        registerUnknownTopics = registerUnknownTopics,
        inferUnknownTopicValueType = inferUnknownTopicValueType,
        unknownTopicValueType = MqttValueType.valueOf(unknownTopicValueType.trim().uppercase())
    )
}

@Configuration
@EnableConfigurationProperties(MqttGatewayProperties::class)
class MqttGatewaySpringConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "dt.mqtt", name = ["enabled"], havingValue = "true")
    fun mqttGateway(properties: MqttGatewayProperties): MqttGateway = MqttGateway(
        id = ComponentId(properties.componentId),
        description = properties.description,
        config = properties.toConfig()
    )

    @Bean
    @ConditionalOnBean(MqttGateway::class)
    @ConditionalOnProperty(prefix = "dt.mqtt", name = ["auto-start"], havingValue = "true")
    fun mqttGatewayRunner(gateway: MqttGateway): ApplicationRunner = ApplicationRunner {
        runBlocking { gateway.start() }
    }
}
