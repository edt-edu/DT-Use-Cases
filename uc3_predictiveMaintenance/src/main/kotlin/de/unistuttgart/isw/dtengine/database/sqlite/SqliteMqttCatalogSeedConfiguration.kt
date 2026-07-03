package de.unistuttgart.isw.dtengine.database.sqlite

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.gateway.mqtt.MachineType
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttCatalogDataPointRegistrar
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttGatewayProperties
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SqliteMqttCatalogSeedConfiguration {
    @Bean
    @ConditionalOnBean(SqliteDtStore::class)
    @ConditionalOnProperty(prefix = "dt.sqlite", name = ["seed-mqtt-catalog"], havingValue = "true")
    fun mqttCatalogDataPointRunner(
        sqliteProperties: SqliteProperties,
        mqttProperties: MqttGatewayProperties,
        store: SqliteDtStore
    ): ApplicationRunner = ApplicationRunner {
        runBlocking {
            MqttCatalogDataPointRegistrar(dataStore = store).register(
                machineId = mqttProperties.machineId,
                machineType = MachineType.parse(mqttProperties.machineType),
                topicPrefix = mqttProperties.topicPrefix,
                sourceComponent = ComponentId(mqttProperties.componentId),
                includeWritableCommands = sqliteProperties.seedMqttWritableCommands
            )
        }
    }
}
