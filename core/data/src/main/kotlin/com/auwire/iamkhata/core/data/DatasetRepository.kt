package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.Aggregation
import com.auwire.iamkhata.core.model.CleaningOperation
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.Dataset
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.DatasetKind
import com.auwire.iamkhata.core.model.FilterSpec
import com.auwire.iamkhata.core.model.PivotResult
import com.auwire.iamkhata.core.model.PivotSpec
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.SortSpec
import com.auwire.iamkhata.core.model.TablePage
import com.auwire.iamkhata.core.model.TransformResult
import kotlinx.coroutines.flow.Flow

/**
 * Stable data contract consumed by features.
 *
 * UI code depends on this interface rather than Room so persistence and
 * analytics implementations can evolve independently.
 */
interface DatasetRepository {
    fun observeDatasets(): Flow<List<Dataset>>
    fun observeColumns(datasetId: Long): Flow<List<DatasetColumn>>

    suspend fun ensureDefaultLedger(): Long

    suspend fun createDataset(name: String, kind: DatasetKind): Long

    suspend fun addColumn(
        datasetId: Long,
        displayName: String,
        type: ColumnType,
        role: ColumnRole = ColumnRole.NONE,
        required: Boolean = false,
        isProtected: Boolean = false,
    ): Long

    suspend fun appendRow(
        datasetId: Long,
        values: Map<Long, String>,
        status: RowStatus = RowStatus.DRAFT,
        origin: RowOrigin = RowOrigin.MANUAL,
    ): Long

    suspend fun updateRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        values: Map<Long, String>,
        status: RowStatus,
    ): Long

    suspend fun voidRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        reason: String,
    ): Long

    suspend fun reverseRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        reason: String,
    ): Long

    suspend fun loadPage(
        datasetId: Long,
        limit: Int,
        offset: Int,
        sort: SortSpec? = null,
        filter: FilterSpec? = null,
    ): TablePage

    suspend fun cleanColumn(
        datasetId: Long,
        columnId: Long,
        operation: CleaningOperation,
    ): TransformResult

    suspend fun pivot(datasetId: Long, spec: PivotSpec): PivotResult
}
