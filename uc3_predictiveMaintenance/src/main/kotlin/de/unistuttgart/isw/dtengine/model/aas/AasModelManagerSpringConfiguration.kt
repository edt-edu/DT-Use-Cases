package de.unistuttgart.isw.dtengine.model.aas

import de.unistuttgart.isw.dtengine.core.ComponentId
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@ConfigurationProperties(prefix = "dt.aas")
class AasModelManagerProperties {
    var enabled: Boolean = false
    var autoStart: Boolean = false
    var componentId: String = "aas-model-manager"
    var description: String = "AAS-backed model manager"
    var shellId: String = "dt-engine-aas"
    var shellIdShort: String = "DTEngineAas"
    var dataSubmodelId: String = "dt-engine-aas:data"
    var dataSubmodelIdShort: String = "Data"
    var stateSubmodelId: String = "dt-engine-aas:state"
    var stateSubmodelIdShort: String = "State"
    var defaultParentPath: String? = null
    var autoCreatePropertiesFromGateway: Boolean = true
}

@Configuration
@EnableConfigurationProperties(AasModelManagerProperties::class)
class AasModelManagerSpringConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "dt.aas", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(AasRepositoryClient::class)
    fun aasRepositoryClient(): AasRepositoryClient = InMemoryAasRepositoryClient()

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "dt.aas", name = ["enabled"], havingValue = "true")
    fun aasModelManager(
        properties: AasModelManagerProperties,
        repositoryClient: AasRepositoryClient
    ): AasModelManager = AasModelManager(
        id = ComponentId(properties.componentId),
        description = properties.description,
        repository = repositoryClient,
        shellId = properties.shellId,
        shellIdShort = properties.shellIdShort,
        dataSubmodelId = properties.dataSubmodelId,
        dataSubmodelIdShort = properties.dataSubmodelIdShort,
        stateSubmodelId = properties.stateSubmodelId,
        stateSubmodelIdShort = properties.stateSubmodelIdShort,
        defaultParentPath = properties.defaultParentPath,
        autoCreatePropertiesFromGateway = properties.autoCreatePropertiesFromGateway
    )

    @Bean
    @ConditionalOnBean(AasModelManager::class)
    @ConditionalOnProperty(prefix = "dt.aas", name = ["auto-start"], havingValue = "true")
    fun aasModelManagerRunner(modelManager: AasModelManager): ApplicationRunner = ApplicationRunner {
        runBlocking { modelManager.start() }
    }
}
