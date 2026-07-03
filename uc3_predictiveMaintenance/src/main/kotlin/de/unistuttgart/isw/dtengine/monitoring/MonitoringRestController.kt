package de.unistuttgart.isw.dtengine.monitoring

import de.unistuttgart.isw.dtengine.core.DataPointId
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/monitoring")
@CrossOrigin(origins = ["*"])
class MonitoringRestController(
    private val monitoringService: RestMonitoringService
) {
    @GetMapping("/snapshot")
    fun snapshot(): MonitoringDashboardSnapshot = runBlocking {
        monitoringService.dashboardSnapshot()
    }

    @GetMapping("/data-points")
    fun dataPoints(): List<MachineDataPoint> = runBlocking {
        monitoringService.machineDataPoints()
    }

    @GetMapping("/data-points/{id}/history")
    fun dataPointHistory(
        @PathVariable id: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): List<MachineDataHistoryEntry> = runBlocking {
        monitoringService.history(DataPointId(id), limit)
    }

    @GetMapping("/services")
    fun services(): List<MonitoringServiceDescriptor> = runBlocking {
        monitoringService.serviceDescriptors()
    }

    @GetMapping("/aas/properties")
    fun aasProperties(): List<MonitoringAasProperty> = runBlocking {
        monitoringService.aasProperties()
    }

    @GetMapping("/mappings")
    fun mappings(): List<MonitoringMappingDescriptor> = runBlocking {
        monitoringService.mappings()
    }

    @GetMapping("/events")
    fun recentEvents(@RequestParam(defaultValue = "100") limit: Int): List<MonitoringEventDescriptor> = runBlocking {
        monitoringService.recentEvents(limit)
    }

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any?>> = runBlocking {
        val health = monitoringService.health()
        ResponseEntity.ok(
            mapOf(
                "alive" to health.alive,
                "status" to health.status.name,
                "message" to health.message,
                "details" to health.details
            )
        )
    }
}
