package de.unistuttgart.isw.dtengine.monitoring

import de.unistuttgart.isw.dtengine.core.ComponentId
import de.unistuttgart.isw.dtengine.core.ServiceId
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.DtEventStore
import de.unistuttgart.isw.dtengine.database.ServiceRequirementStore
import de.unistuttgart.isw.dtengine.database.ServiceRegistryStore
import de.unistuttgart.isw.dtengine.mapping.AbstractMappingRegistry
import de.unistuttgart.isw.dtengine.model.aas.AasRepositoryClient
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dt.monitoring")
class MonitoringProperties {
    var enabled: Boolean = true
    var autoStart: Boolean = true
    var registerWithServiceManager: Boolean = true
    var serviceId: String = "monitoring-rest"
    var componentId: String = "monitoring-rest-service"
    var description: String = "REST monitoring service for DT state and machine data"
    var maxRecentEvents: Int = 100
    var defaultHistoryLimit: Int = 50
}

@Configuration
@EnableConfigurationProperties(MonitoringProperties::class)
class MonitoringSpringConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dt.monitoring", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun restMonitoringService(
        properties: MonitoringProperties,
        dataStoreProvider: ObjectProvider<DtDataStore>,
        requirementStoreProvider: ObjectProvider<ServiceRequirementStore>,
        registryStoreProvider: ObjectProvider<ServiceRegistryStore>,
        aasRepositoryProvider: ObjectProvider<AasRepositoryClient>,
        mappingRegistryProvider: ObjectProvider<AbstractMappingRegistry>,
        eventStoreProvider: ObjectProvider<DtEventStore>
    ): RestMonitoringService = RestMonitoringService(
        serviceId = ServiceId(properties.serviceId),
        id = ComponentId(properties.componentId),
        description = properties.description,
        maxRecentEvents = properties.maxRecentEvents,
        defaultHistoryLimit = properties.defaultHistoryLimit,
        dataStoreProvider = dataStoreProvider,
        requirementStoreProvider = requirementStoreProvider,
        registryStoreProvider = registryStoreProvider,
        aasRepositoryProvider = aasRepositoryProvider,
        mappingRegistryProvider = mappingRegistryProvider,
        eventStoreProvider = eventStoreProvider
    )

    @Bean
    @ConditionalOnBean(RestMonitoringService::class)
    @ConditionalOnProperty(prefix = "dt.monitoring", name = ["auto-start"], havingValue = "true", matchIfMissing = true)
    fun monitoringRunner(service: RestMonitoringService): ApplicationRunner = ApplicationRunner {
        runBlocking { service.start() }
    }

    @Bean
    @ConditionalOnBean(RestMonitoringService::class, DtServiceManager::class)
    @ConditionalOnProperty(prefix = "dt.monitoring", name = ["register-with-service-manager"], havingValue = "true", matchIfMissing = true)
    fun monitoringServiceManagerRegistration(
        service: RestMonitoringService,
        serviceManager: DtServiceManager
    ): ApplicationRunner = ApplicationRunner {
        runBlocking {
            serviceManager.register(
                service,
                metadata = mapOf(
                    "api" to "/api/monitoring",
                    "frontend" to "frontend/monitoring-ui",
                    "role" to "machine-data-and-state-monitoring"
                )
            )
        }
    }
}
