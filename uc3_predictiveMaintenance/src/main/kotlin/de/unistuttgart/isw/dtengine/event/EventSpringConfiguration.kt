package de.unistuttgart.isw.dtengine.event

import de.unistuttgart.isw.dtengine.core.DtEventBus
import de.unistuttgart.isw.dtengine.database.DtEventStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dt.events")
class EventBusProperties {
    var enabled: Boolean = true
    var persist: Boolean = true
    var maxBufferedEvents: Int = 500
}

@Configuration
@EnableConfigurationProperties(EventBusProperties::class)
class EventSpringConfiguration {

    @Bean
    @ConditionalOnMissingBean(DtEventBus::class)
    fun dtEventBus(
        properties: EventBusProperties,
        eventStore: ObjectProvider<DtEventStore>
    ): DtEventBus = InMemoryDtEventBus(
        eventStore = if (properties.enabled && properties.persist) eventStore.getIfAvailable() else null,
        maxBufferedEvents = properties.maxBufferedEvents
    )
}
