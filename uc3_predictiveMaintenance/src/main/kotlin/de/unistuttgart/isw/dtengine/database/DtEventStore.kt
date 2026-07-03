package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.DtEvent
import de.unistuttgart.isw.dtengine.core.EventType

interface DtEventStore {
    suspend fun append(event: DtEvent): DtEvent

    suspend fun recent(limit: Int = 100): List<DtEvent>

    suspend fun byType(type: EventType, limit: Int = 100): List<DtEvent>
}
