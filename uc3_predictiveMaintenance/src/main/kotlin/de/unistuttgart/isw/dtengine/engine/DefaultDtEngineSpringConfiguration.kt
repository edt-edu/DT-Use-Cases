package de.unistuttgart.isw.dtengine.engine

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.synchronization.AbstractSynchronizer
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dt.engine")
class DtEngineProperties {
    var enabled: Boolean = true
    var autoStart: Boolean = false
    var componentId: String = "dt-engine"
    var description: String = "Default Digital Twin engine"
}

@Configuration
@EnableConfigurationProperties(DtEngineProperties::class)
class DefaultDtEngineSpringConfiguration {

    @Bean
    @ConditionalOnMissingBean(AbstractDtEngine::class)
    @ConditionalOnProperty(prefix = "dt.engine", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(DtEventBus::class, AbstractModelManager::class, AbstractMappingRegistry::class, AbstractSynchronizer::class)
    fun defaultDtEngine(
        properties: DtEngineProperties,
        eventBus: DtEventBus,
        modelManager: AbstractModelManager,
        mappingRegistry: AbstractMappingRegistry,
        synchronizer: AbstractSynchronizer,
        serviceManager: ObjectProvider<DtServiceManager>,
        gateways: ObjectProvider<AbstractGateway>,
        services: ObjectProvider<AbstractDtService>
    ): DefaultDtEngine = DefaultDtEngine(
        id = ComponentId(properties.componentId),
        description = properties.description,
        eventBus = eventBus,
        modelManager = modelManager,
        mappingRegistry = mappingRegistry,
        synchronizer = synchronizer,
        serviceManager = serviceManager.getIfAvailable(),
        initialGateways = gateways.orderedStream().toList(),
        initialServices = services.orderedStream().toList()
    )

    @Bean
    @ConditionalOnBean(DefaultDtEngine::class)
    @ConditionalOnProperty(prefix = "dt.engine", name = ["auto-start"], havingValue = "true")
    fun defaultDtEngineRunner(engine: DefaultDtEngine): ApplicationRunner = ApplicationRunner {
        runBlocking { engine.start() }
    }
}
