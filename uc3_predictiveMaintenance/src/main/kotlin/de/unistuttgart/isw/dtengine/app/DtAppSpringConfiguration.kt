package de.unistuttgart.isw.dtengine.app

import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.engine.AbstractDtEngine
import de.unistuttgart.isw.dtengine.gateway.AbstractGateway
import de.unistuttgart.isw.dtengine.mapping.DefaultMappingSeeder
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.AbstractDtService
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import de.unistuttgart.isw.dtengine.service.mapek.ConveyorKpiMapekConfig
import de.unistuttgart.isw.dtengine.synchronization.AbstractSynchronizer
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@ConfigurationProperties(prefix = "dt.app")
class DtAppProperties {
    var enabled: Boolean = false
    var autoStart: Boolean = true
    var startGateways: Boolean = false
    var seedMappings: Boolean = true
    var runStartupCycle: Boolean = false
    var demoSourceComponentId: String = "mqtt-gateway"
}

@Configuration
@EnableConfigurationProperties(DtAppProperties::class)
class DtAppSpringConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dt.app", name = ["enabled"], havingValue = "true")
    fun dtAppLifecycleService(
        properties: DtAppProperties,
        eventBus: DtEventBus,
        mappingSeeder: ObjectProvider<DefaultMappingSeeder>,
        dataStore: ObjectProvider<DtDataStore>,
        modelManager: ObjectProvider<AbstractModelManager>,
        synchronizer: ObjectProvider<AbstractSynchronizer>,
        serviceManager: ObjectProvider<DtServiceManager>,
        engine: ObjectProvider<AbstractDtEngine>,
        gateways: ObjectProvider<AbstractGateway>,
        services: ObjectProvider<AbstractDtService>,
        conveyorConfig: ConveyorKpiMapekConfig
    ): DtAppLifecycleService = DtAppLifecycleService(
        properties = properties,
        eventBus = eventBus,
        mappingSeeder = mappingSeeder.getIfAvailable(),
        dataStore = dataStore.getIfAvailable(),
        modelManager = modelManager.getIfAvailable(),
        synchronizer = synchronizer.getIfAvailable(),
        serviceManager = serviceManager.getIfAvailable(),
        engine = engine.getIfAvailable(),
        gateways = gateways.orderedStream().toList(),
        services = services.orderedStream().toList(),
        conveyorConfig = conveyorConfig
    )

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnProperty(prefix = "dt.app", name = ["enabled"], havingValue = "true")
    fun dtIntegratedAppRunner(
        properties: DtAppProperties,
        lifecycle: DtAppLifecycleService
    ): ApplicationRunner = ApplicationRunner {
        if (properties.autoStart) {
            runBlocking { lifecycle.start() }
        }
    }
}
