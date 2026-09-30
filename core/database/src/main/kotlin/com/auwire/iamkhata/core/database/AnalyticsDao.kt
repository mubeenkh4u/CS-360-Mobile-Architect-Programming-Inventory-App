package com.auwire.iamkhata.core.database

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Read-only dynamic analytics entry point.
 *
 * Query construction lives in the repository where identifiers are selected
 * from trusted metadata and all user values remain bound parameters.
 */
@Dao
interface AnalyticsDao {
    @RawQuery(observedEntities = [RowEntity::class, CellEntity::class])
    suspend fun queryRowIds(query: SupportSQLiteQuery): List<RowIdProjection>

    @RawQuery(observedEntities = [RowEntity::class, CellEntity::class])
    suspend fun queryLong(query: SupportSQLiteQuery): LongValueProjection?

    @RawQuery(observedEntities = [RowEntity::class, CellEntity::class])
    suspend fun queryPivot(query: SupportSQLiteQuery): List<PivotProjection>
}
