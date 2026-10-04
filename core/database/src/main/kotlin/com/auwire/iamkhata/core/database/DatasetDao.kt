package com.auwire.iamkhata.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Canonical CRUD access for datasets, runtime schemas, rows and cells. */
@Dao
interface DatasetDao {
    @Query("SELECT * FROM datasets ORDER BY updatedAt DESC, id DESC")
    fun observeDatasets(): Flow<List<DatasetEntity>>

    @Query("SELECT * FROM datasets WHERE id = :datasetId LIMIT 1")
    suspend fun getDataset(datasetId: Long): DatasetEntity?

    @Query("SELECT * FROM datasets WHERE name = :name LIMIT 1")
    suspend fun getDatasetByName(name: String): DatasetEntity?

    @Insert
    suspend fun insertDataset(dataset: DatasetEntity): Long

    @Update
    suspend fun updateDataset(dataset: DatasetEntity)

    @Query("SELECT * FROM dataset_columns WHERE datasetId = :datasetId ORDER BY position, id")
    fun observeColumns(datasetId: Long): Flow<List<ColumnEntity>>

    @Query("SELECT * FROM dataset_columns WHERE datasetId = :datasetId ORDER BY position, id")
    suspend fun getColumns(datasetId: Long): List<ColumnEntity>

    @Query("SELECT * FROM dataset_columns WHERE id = :columnId LIMIT 1")
    suspend fun getColumn(columnId: Long): ColumnEntity?

    @Query("SELECT MAX(position) FROM dataset_columns WHERE datasetId = :datasetId")
    suspend fun maxColumnPosition(datasetId: Long): Int?

    @Insert
    suspend fun insertColumn(column: ColumnEntity): Long

    @Insert
    suspend fun insertColumns(columns: List<ColumnEntity>): List<Long>

    @Insert
    suspend fun insertRow(row: RowEntity): Long

    @Query("SELECT * FROM data_rows WHERE id = :rowId LIMIT 1")
    suspend fun getRow(rowId: Long): RowEntity?

    @Query("SELECT * FROM data_rows WHERE id IN (:rowIds)")
    suspend fun getRows(rowIds: List<Long>): List<RowEntity>

    @Query("SELECT * FROM cells WHERE rowId IN (:rowIds)")
    suspend fun getCellsForRows(rowIds: List<Long>): List<CellEntity>

    @Query("SELECT * FROM cells WHERE rowId = :rowId")
    suspend fun getCellsForRow(rowId: Long): List<CellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CellEntity>)

    @Query("DELETE FROM cells WHERE rowId = :rowId")
    suspend fun deleteCellsForRow(rowId: Long): Int

    /**
     * Optimistic row update. Returning 0 means the row was changed elsewhere or
     * is system-locked.
     */
    @Query(
        """
        UPDATE data_rows
        SET status = :status,
            revision = revision + 1,
            updatedAt = :updatedAt
        WHERE id = :rowId
          AND revision = :expectedRevision
          AND isLocked = 0
        """
    )
    suspend fun updateEditableRow(
        rowId: Long,
        expectedRevision: Long,
        status: String,
        updatedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM data_rows WHERE datasetId = :datasetId")
    suspend fun countRows(datasetId: Long): Long

    @Query("SELECT COUNT(*) FROM cells WHERE columnId = :columnId")
    suspend fun countCells(columnId: Long): Int

    @Query(
        """
        UPDATE cells
        SET rawValue = TRIM(rawValue),
            normalizedValue = LOWER(TRIM(rawValue))
        WHERE columnId = :columnId
          AND rowId IN (
              SELECT id
              FROM data_rows
              WHERE isLocked = 0
                AND status = 'DRAFT'
          )
        """
    )
    suspend fun trimColumn(columnId: Long): Int

    @Query(
        """
        UPDATE cells
        SET rawValue = LOWER(TRIM(rawValue)),
            normalizedValue = LOWER(TRIM(rawValue))
        WHERE columnId = :columnId
          AND rowId IN (
              SELECT id
              FROM data_rows
              WHERE isLocked = 0
                AND status = 'DRAFT'
          )
        """
    )
    suspend fun lowercaseColumn(columnId: Long): Int

    @Query(
        """
        UPDATE cells
        SET rawValue = UPPER(TRIM(rawValue)),
            normalizedValue = LOWER(TRIM(rawValue))
        WHERE columnId = :columnId
          AND rowId IN (
              SELECT id
              FROM data_rows
              WHERE isLocked = 0
                AND status = 'DRAFT'
          )
        """
    )
    suspend fun uppercaseColumn(columnId: Long): Int

    @Query(
        """
        UPDATE data_rows
        SET revision = revision + 1,
            updatedAt = :updatedAt
        WHERE isLocked = 0
          AND status = 'DRAFT'
          AND id IN (SELECT rowId FROM cells WHERE columnId = :columnId)
        """
    )
    suspend fun touchRowsForColumn(columnId: Long, updatedAt: Long): Int
}
