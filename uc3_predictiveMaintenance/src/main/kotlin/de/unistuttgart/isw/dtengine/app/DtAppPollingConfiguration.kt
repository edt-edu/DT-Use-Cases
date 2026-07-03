package de.unistuttgart.isw.dtengine.app

import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicBoolean

@ConfigurationProperties(prefix = "dt.app.polling")
class DtAppPollingProperties {
    /**
     * Runs engine cycles periodically. The MQTT client still receives messages
     * via subscription; this loop pulls the latest cached gateway/database values
     * through mappings and ticks services such as the MAPE-K service.
     */
    var enabled: Boolean = false
    var intervalMillis: Long = 1_000
    var reason: String = "periodic-app-cycle"
}

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DtAppPollingProperties::class)
class DtAppPollingConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "dt.app", name = ["enabled"], havingValue = "true")
    fun dtAppCyclePoller(
        lifecycle: DtAppLifecycleService,
        properties: DtAppPollingProperties
    ): DtAppCyclePoller = DtAppCyclePoller(lifecycle, properties)
}

class DtAppCyclePoller(
    private val lifecycle: DtAppLifecycleService,
    private val properties: DtAppPollingProperties
) {
    private val running = AtomicBoolean(false)

    @Scheduled(
        fixedDelayString = "\${dt.app.polling.interval-millis:1000}",
        initialDelayString = "\${dt.app.polling.interval-millis:1000}"
    )
    fun poll() {
        if (!properties.enabled || !lifecycle.isRunning()) return
        if (!running.compareAndSet(false, true)) return
        try {
            runBlocking { lifecycle.runEngineCycle(properties.reason) }
        } finally {
            running.set(false)
        }
    }
}
