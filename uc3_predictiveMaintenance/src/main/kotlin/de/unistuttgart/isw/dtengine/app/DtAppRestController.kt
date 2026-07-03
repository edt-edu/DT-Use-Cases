package de.unistuttgart.isw.dtengine.app

import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnProperty(prefix = "dt.app", name = ["enabled"], havingValue = "true")
@RequestMapping("/api/app")
class DtAppRestController(
    private val lifecycle: DtAppLifecycleService
) {
    @GetMapping("/status")
    fun status(): DtAppStatusResponse = runBlocking { lifecycle.status() }

    @PostMapping("/start")
    fun start(): ResponseEntity<DtAppActionResponse> = runBlocking {
        val response = lifecycle.start()
        ResponseEntity.status(if (response.successful) 200 else 500).body(response)
    }

    @PostMapping("/stop")
    fun stop(): ResponseEntity<DtAppActionResponse> = runBlocking {
        val response = lifecycle.stop()
        ResponseEntity.status(if (response.successful) 200 else 500).body(response)
    }

    @PostMapping("/cycle")
    fun cycle(): ResponseEntity<DtAppActionResponse> = runBlocking {
        val response = lifecycle.runEngineCycle("rest-manual")
        ResponseEntity.status(if (response.successful) 200 else 500).body(response)
    }

    @PostMapping("/demo/value")
    fun demoValue(@RequestBody request: DemoValueRequest): DtAppActionResponse = runBlocking {
        lifecycle.publishDemoValue(request)
    }

    @PostMapping("/demo/transport")
    fun demoTransport(@RequestBody request: DemoTransportRequest): DtAppActionResponse = runBlocking {
        lifecycle.simulateTransport(request)
    }
}
