package de.unistuttgart.isw.dtengine.engine

import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.service.ServiceRequest

/**
 * Optional narrow contracts that keep implementations replaceable.
 */
interface EngineEventHandler {
    suspend fun handle(event: DtEvent): List<DtEvent>
}

interface EngineRequestHandler {
    suspend fun handle(request: ServiceRequest): Any?
}

interface EngineCycleScheduler {
    suspend fun runOnce(): EngineCycleResult
    suspend fun runForever()
    suspend fun requestStop()
}
