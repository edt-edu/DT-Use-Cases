package de.unistuttgart.isw.dtengine.service

import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.database.ServiceRequirementStore
import de.unistuttgart.isw.dtengine.database.ServiceRegistryStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DtServiceManagerSpringConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dt.services.manager", name = ["enabled"], havingValue = "true")
    fun dtServiceManager(
        dataStore: ObjectProvider<DtDataStore>,
        requirementStore: ObjectProvider<ServiceRequirementStore>,
        registryStore: ObjectProvider<ServiceRegistryStore>,
        eventBus: ObjectProvider<DtEventBus>
    ): DtServiceManager = DtServiceManager(
        dataStore = dataStore.getIfAvailable(),
        requirementStore = requirementStore.getIfAvailable(),
        registryStore = registryStore.getIfAvailable(),
        eventBus = eventBus.getIfAvailable()
    )
}
