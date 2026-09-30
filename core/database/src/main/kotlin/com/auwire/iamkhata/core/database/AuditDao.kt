package com.auwire.iamkhata.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Audit and transformation metadata access. */
@Dao
interface AuditDao {
    @Insert
    suspend fun insertAuditEvent(event: AuditEventEntity): Long

    @Query("SELECT * FROM audit_events ORDER BY id DESC LIMIT 1")
    suspend fun latestAuditEvent(): AuditEventEntity?

    @Insert
    suspend fun insertTransformEvent(event: TransformEventEntity): Long
}
