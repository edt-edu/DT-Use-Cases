package de.unistuttgart.isw.dtengine.service.mapek

import de.unistuttgart.isw.dtengine.database.DtDataStore
import de.unistuttgart.isw.dtengine.model.AbstractModelManager
import de.unistuttgart.isw.dtengine.service.DtServiceManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlinx.coroutines.runBlocking

@Configuration
@EnableConfigurationProperties(ConveyorKpiMapekConfig::class)
class ConveyorKpiMapekSpringConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dt.services.conveyor-kpi-mapek", name = ["enabled"], havingValue = "true")
    fun conveyorKpiMapekService(
        config: ConveyorKpiMapekConfig,
        dataStore: ObjectProvider<DtDataStore>,
        modelManager: ObjectProvider<AbstractModelManager>
    ): ConveyorKpiMapekService = ConveyorKpiMapekService(
        config = config,
        dataStore = dataStore.getIfAvailable(),
        modelManager = modelManager.getIfAvailable()
    )

    @Bean
    @ConditionalOnProperty(prefix = "dt.services.conveyor-kpi-mapek", name = ["enabled"], havingValue = "true")
    fun registerConveyorKpiMapekService(
        service: ConveyorKpiMapekService,
        serviceManager: ObjectProvider<DtServiceManager>
    ): ApplicationRunner = ApplicationRunner {
        serviceManager.getIfAvailable()?.let { manager ->
            runBlocking {
                manager.register(
                    service,
                    metadata = mapOf(
                        "useCase" to "conveyor-transport-productivity",
                        "producedModelProperties" to service.producedDataPoints.joinToString(",") { it.value },
                        "mapeK" to "monitor,analyze,plan,execute,knowledge"
                    )
                )
                manager.startReadyServices()
            }
        }
    }
}
