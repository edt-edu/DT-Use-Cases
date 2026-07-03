package de.unistuttgart.isw.dtengine.database

import de.unistuttgart.isw.dtengine.core.DataPointId
import de.unistuttgart.isw.dtengine.core.DataValue

interface DtDataStore {
    suspend fun upsertDataPoint(definition: DataPointDefinition): DataPointDefinition

    suspend fun getDataPoint(id: DataPointId): DataPointDefinition?

    suspend fun knownDataPoints(): Set<DataPointId>

    suspend fun writeValue(value: DataValue): DataValue

    suspend fun readLatest(id: DataPointId): DataValue?

    suspend fun readLatest(ids: Set<DataPointId>): List<DataValue>

    suspend fun history(id: DataPointId, limit: Int = 100): List<StoredDataValue>

    suspend fun deleteDataPoint(id: DataPointId): Boolean
}
