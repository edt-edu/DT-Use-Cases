package de.unistuttgart.isw.dtengine.synchronization

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.core.DtObserver
import de.unistuttgart.isw.dtengine.core.EventFilter
import de.unistuttgart.isw.dtengine.core.EventType
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.gateway.mqtt.MqttGatewayProperties
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.mapping.DefaultMappingSeeder
import de.unistuttgart.isw.dtengine.mapping.DefaultValueTransformer
import de.unistuttgart.isw.dtengine.mapping.InMemoryMappingRegistry
import de.unistuttgart.isw.dtengine.mapping.MappingProperties
import de.unistuttgart.isw.dtengine.mapping.ValueTransformer
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.service.mapek.ConveyorKpiMapekConfig
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dt.synchronization")
class SynchronizationProperties {
    var enabled: Boolean = true
    var autoStart: Boolean = true
    var subscribeToEvents: Boolean = true
    var bridgeGatewaysToEventBus: Boolean = true
    var seedMappingsOnStartup: Boolean = true
    var runCycleOnStartup: Boolean = false
}

@Configuration
@EnableConfigurationProperties(MappingProperties::class, SynchronizationProperties::class)
class SynchronizationSpringConfiguration {

    @Bean
    @ConditionalOnMissingBean(AbstractMappingRegistry::class)
    fun inMemoryMappingRegistry(): AbstractMappingRegistry = InMemoryMappingRegistry()

    @Bean
    @ConditionalOnMissingBean(ValueTransformer::class)
    fun defaultValueTransformer(): ValueTransformer = DefaultValueTransformer()

    @Bean
    @ConditionalOnMissingBean(AbstractSynchronizer::class)
    fun mappingSynchronizer(
        mappingRegistry: AbstractMappingRegistry,
        transformer: ValueTransformer,
        dataStore: ObjectProvider<DtDataStore>,
        modelManager: ObjectProvider<AbstractModelManager>,
        mappingProperties: MappingProperties,
        gateways: ObjectProvider<AbstractGateway>,
        serviceManager: ObjectProvider<DtServiceManager>,
        eventBus: ObjectProvider<DtEventBus>
    ): MappingSynchronizer = MappingSynchronizer(
        mappingRegistry = mappingRegistry,
        transformer = transformer,
        dataStore = dataStore.getIfAvailable(),
        modelManager = modelManager.getIfAvailable(),
        mappingProperties = mappingProperties,
        gateways = gateways.orderedStream().toList(),
        serviceManager = serviceManager.getIfAvailable(),
        eventBus = eventBus.getIfAvailable()
    )

    @Bean
    fun defaultMappingSeeder(
        mappingRegistry: AbstractMappingRegistry,
        mappingProperties: MappingProperties,
        mqttProperties: ObjectProvider<MqttGatewayProperties>,
        conveyorConfig: ObjectProvider<ConveyorKpiMapekConfig>
    ): DefaultMappingSeeder = DefaultMappingSeeder(
        registry = mappingRegistry,
        properties = mappingProperties,
        mqttProperties = mqttProperties.getIfAvailable(),
        conveyorConfig = conveyorConfig.getIfAvailable()
    )

    @Bean
    fun synchronizationRunner(
        synchronizationProperties: SynchronizationProperties,
        eventBusProvider: ObjectProvider<DtEventBus>,
        synchronizer: MappingSynchronizer,
        serviceManagerProvider: ObjectProvider<DtServiceManager>,
        gateways: ObjectProvider<AbstractGateway>,
        mappingSeeder: DefaultMappingSeeder
    ): ApplicationRunner = ApplicationRunner {
        if (!synchronizationProperties.enabled) return@ApplicationRunner
        runBlocking {
            if (synchronizationProperties.seedMappingsOnStartup) {
                mappingSeeder.seed().forEach { mapping ->
                    eventBusProvider.getIfAvailable()?.publish(
                        DtEvent(
                            type = EventType.MAPPING_CREATED,
                            source = ComponentId("mapping-seeder"),
                            payload = mapOf(
                                "mappingId" to mapping.id.value,
                                "direction" to mapping.direction.name,
                                "source" to mapping.source.toString(),
                                "target" to mapping.target.toString()
                            )
                        )
                    )
                }
            }

            if (synchronizationProperties.autoStart) synchronizer.start()

            val bus = eventBusProvider.getIfAvailable()
            if (bus != null && synchronizationProperties.subscribeToEvents) {
                bus.subscribe(
                    synchronizer,
                    EventFilter(
                        acceptedTypes = setOf(
                            EventType.GATEWAY_DATA_RECEIVED,
                            EventType.GATEWAY_COMMAND_SENT,
                            EventType.MODEL_PROPERTY_CREATED,
                            EventType.MODEL_PROPERTY_UPDATED,
                            EventType.SERVICE_REQUEST,
                            EventType.SERVICE_ANSWER
                        )
                    )
                )
                serviceManagerProvider.getIfAvailable()?.let { serviceManager ->
                    bus.subscribe(serviceManager, EventFilter.all())
                }
            }

            if (bus != null && synchronizationProperties.bridgeGatewaysToEventBus) {
                val bridge = EventBusForwardingObserver(ComponentId("gateway-event-bridge"), bus)
                gateways.orderedStream().toList().forEach { gateway ->
                    synchronizer.registerGateway(gateway)
                    gateway.subscribe(bridge)
                }
            }

            if (synchronizationProperties.runCycleOnStartup) {
                val result = synchronizer.synchronize(
                    SynchronizationContext(cycleId = System.currentTimeMillis(), requestedBy = ComponentId("startup"), reason = "startup")
                )
                synchronizer.toEvents(result).forEach { bus?.publish(it) }
            }
        }
    }
}

private class EventBusForwardingObserver(
    override val observerId: ComponentId,
    private val eventBus: DtEventBus
) : DtObserver {
    override suspend fun onEvent(event: DtEvent) {
        eventBus.publish(event)
    }
}
