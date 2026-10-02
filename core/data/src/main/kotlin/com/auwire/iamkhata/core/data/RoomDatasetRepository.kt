package com.auwire.iamkhata.core.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.auwire.iamkhata.core.database.CellEntity
import com.auwire.iamkhata.core.database.ColumnEntity
import com.auwire.iamkhata.core.database.DatasetEntity
import com.auwire.iamkhata.core.database.IamDatabase
import com.auwire.iamkhata.core.database.RowEntity
import com.auwire.iamkhata.core.database.TransformEventEntity
import com.auwire.iamkhata.core.model.Aggregation
import com.auwire.iamkhata.core.model.CellValue
import com.auwire.iamkhata.core.model.CleaningOperation
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.Dataset
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.DatasetKind
import com.auwire.iamkhata.core.model.FilterSpec
import com.auwire.iamkhata.core.model.PivotCell
import com.auwire.iamkhata.core.model.PivotResult
import com.auwire.iamkhata.core.model.PivotSpec
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.SortDirection
import com.auwire.iamkhata.core.model.SortSpec
import com.auwire.iamkhata.core.model.TablePage
import com.auwire.iamkhata.core.model.TransformResult
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of the tabular data contract.
 *
 * Dynamic SQL is limited to trusted query fragments selected from enums/column
 * metadata. User values and runtime IDs are always bound parameters.
 */
class RoomDatasetRepository(
    private val database: IamDatabase,
    signer: IntegritySigner,
) : DatasetRepository {
    private val datasetDao = database.datasetDao()
    private val analyticsDao = database.analyticsDao()
    private val auditDao = database.auditDao()
    private val auditWriter = AuditWriter(auditDao, signer)
    private val ledgerWriter = LedgerRowWriter(database, auditWriter)

    override fun observeDatasets(): Flow<List<Dataset>> =
        datasetDao.observeDatasets().map { entities -> entities.map { it.toModel() } }

    override fun observeColumns(datasetId: Long): Flow<List<DatasetColumn>> =
        datasetDao.observeColumns(datasetId).map { entities -> entities.map { it.toModel() } }

    override suspend fun ensureDefaultLedger(): Long = database.withTransaction {
        ledgerWriter.ensureLedger()
    }

    override suspend fun createDataset(name: String, kind: DatasetKind): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Dataset name is required." }

        return database.withTransaction {
            val now = System.currentTimeMillis()
            val id = datasetDao.insertDataset(
                DatasetEntity(
                    name = cleanName,
                    kind = kind.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            auditWriter.append(
                "CREATE_DATASET",
                "DATASET",
                id.toString(),
                "kind=${kind.name}",
                now,
            )
            id
        }
    }

    override suspend fun addColumn(
        datasetId: Long,
        displayName: String,
        type: ColumnType,
        role: ColumnRole,
        required: Boolean,
        isProtected: Boolean,
    ): Long {
        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty()) { "Column name is required." }
        require(type != ColumnType.FORMULA) {
            "Formula columns are reserved until the formula engine is enabled."
        }

        return database.withTransaction {
            val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Dataset not found." }
            val position = (datasetDao.maxColumnPosition(datasetId) ?: -1) + 1
            val key = "${slug(cleanName)}_${position + 1}"
            val id = datasetDao.insertColumn(
                ColumnEntity(
                    datasetId = datasetId,
                    key = key,
                    displayName = cleanName,
                    type = type.name,
                    role = role.name,
                    position = position,
                    required = required,
                    isProtected = isProtected,
                ),
            )

            val now = System.currentTimeMillis()
            datasetDao.updateDataset(dataset.copy(updatedAt = now))
            auditWriter.append(
                "ADD_COLUMN",
                "COLUMN",
                id.toString(),
                "dataset=$datasetId;type=${type.name};role=${role.name}",
                now,
            )
            id
        }
    }

    override suspend fun appendRow(
        datasetId: Long,
        values: Map<Long, String>,
        status: RowStatus,
        origin: RowOrigin,
    ): Long = database.withTransaction {
        val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Dataset not found." }
        val columns = datasetDao.getColumns(datasetId)
        validateValues(columns, values, status)

        val now = System.currentTimeMillis()
        val rowId = datasetDao.insertRow(
            RowEntity(
                datasetId = datasetId,
                createdAt = now,
                updatedAt = now,
                status = status.name,
                origin = origin.name,
            ),
        )

        val cells = encodeCells(rowId, columns, values)
        if (cells.isNotEmpty()) datasetDao.insertCells(cells)

        datasetDao.updateDataset(dataset.copy(updatedAt = now))
        auditWriter.append(
            action = "APPEND_ROW",
            targetType = "ROW",
            targetId = rowId.toString(),
            payloadSummary = "dataset=$datasetId;status=${status.name};origin=${origin.name};values=${cells.auditMaterial()}",
            timestamp = now,
        )
        rowId
    }

    override suspend fun updateRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        values: Map<Long, String>,
        status: RowStatus,
    ): Long = database.withTransaction {
        val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Dataset not found." }
        val row = requireNotNull(datasetDao.getRow(rowId)) { "Row not found." }
        require(row.datasetId == datasetId) { "Row does not belong to this dataset." }
        require(!row.isLocked) {
            "This row is system-managed. Correct the originating business transaction instead."
        }

        val columns = datasetDao.getColumns(datasetId)
        validateValues(columns, values, status)
        val before = datasetDao.getCellsForRow(rowId)

        val now = System.currentTimeMillis()
        val updated = datasetDao.updateEditableRow(
            rowId = rowId,
            expectedRevision = expectedRevision,
            status = status.name,
            updatedAt = now,
        )
        require(updated == 1) {
            "The row changed before this edit was saved. Reload it and try again."
        }

        datasetDao.deleteCellsForRow(rowId)
        val cells = encodeCells(rowId, columns, values)
        if (cells.isNotEmpty()) datasetDao.insertCells(cells)
        datasetDao.updateDataset(dataset.copy(updatedAt = now))

        auditWriter.append(
            action = "UPDATE_ROW",
            targetType = "ROW",
            targetId = rowId.toString(),
            payloadSummary = "dataset=$datasetId;fromRevision=$expectedRevision;toRevision=${expectedRevision + 1};status=${status.name};before=${before.auditMaterial()};after=${cells.auditMaterial()}",
            timestamp = now,
        )
        expectedRevision + 1
    }

    override suspend fun loadPage(
        datasetId: Long,
        limit: Int,
        offset: Int,
        sort: SortSpec?,
        filter: FilterSpec?,
    ): TablePage {
        require(limit in 1..500) { "Page size must be between 1 and 500." }
        require(offset >= 0) { "Offset cannot be negative." }

        val sortColumn = sort?.let { datasetDao.getColumn(it.columnId) }
        require(sort == null || sortColumn?.datasetId == datasetId) {
            "Sort column does not belong to this dataset."
        }

        val filterValue = filter?.query?.trim()?.takeIf(String::isNotEmpty)
        val rowQuery = buildRowQuery(datasetId, limit, offset, sort, sortColumn, filterValue)
        val rowIds = analyticsDao.queryRowIds(rowQuery).map { it.value }
        val totalRows = queryRowCount(datasetId, filterValue)

        if (rowIds.isEmpty()) {
            return TablePage(emptyList(), totalRows, offset, limit)
        }

        val rowsById = datasetDao.getRows(rowIds).associateBy { it.id }
        val cellsByRow = datasetDao.getCellsForRows(rowIds).groupBy { it.rowId }
        val rows = rowIds.mapNotNull { rowId ->
            rowsById[rowId]?.let { row ->
                DataRow(
                    id = row.id,
                    datasetId = row.datasetId,
                    revision = row.revision,
                    status = RowStatus.valueOf(row.status),
                    origin = RowOrigin.valueOf(row.origin),
                    originRef = row.originRef,
                    isLocked = row.isLocked,
                    values = cellsByRow[rowId].orEmpty().associate {
                        it.columnId to it.toModel()
                    },
                )
            }
        }
        return TablePage(rows, totalRows, offset, limit)
    }

    override suspend fun cleanColumn(
        datasetId: Long,
        columnId: Long,
        operation: CleaningOperation,
    ): TransformResult = database.withTransaction {
        val column = requireNotNull(datasetDao.getColumn(columnId)) { "Column not found." }
        require(column.datasetId == datasetId) { "Column does not belong to this dataset." }
        require(!column.isProtected) { "Protected accounting columns cannot be cleaned in-place." }
        require(column.type == ColumnType.TEXT.name || column.type == ColumnType.CATEGORY.name) {
            "This cleaning operation is only valid for text/category columns."
        }

        val affected = when (operation) {
            CleaningOperation.TRIM_WHITESPACE -> datasetDao.trimColumn(columnId)
            CleaningOperation.LOWERCASE -> datasetDao.lowercaseColumn(columnId)
            CleaningOperation.UPPERCASE -> datasetDao.uppercaseColumn(columnId)
        }
        val now = System.currentTimeMillis()
        datasetDao.touchRowsForColumn(columnId, now)
        auditDao.insertTransformEvent(
            TransformEventEntity(
                datasetId = datasetId,
                columnId = columnId,
                operation = operation.name,
                affectedRows = affected,
                createdAt = now,
            ),
        )
        auditWriter.append(
            "CLEAN_COLUMN",
            "COLUMN",
            columnId.toString(),
            "dataset=$datasetId;operation=${operation.name};affected=$affected",
            now,
        )

        TransformResult(
            affectedRows = affected,
            description = "${operation.name.replace('_', ' ')} applied to $affected cells.",
        )
    }

    override suspend fun pivot(datasetId: Long, spec: PivotSpec): PivotResult {
        val columns = datasetDao.getColumns(datasetId).associateBy { it.id }
        require(columns.containsKey(spec.rowColumnId)) { "Pivot row column is invalid." }
        require(columns.containsKey(spec.valueColumnId)) { "Pivot value column is invalid." }
        require(spec.columnColumnId == null || columns.containsKey(spec.columnColumnId)) {
            "Pivot column field is invalid."
        }

        val aggregateSql = aggregateExpression(spec.aggregation)
        val args = mutableListOf<Any>()
        val sql = if (spec.columnColumnId == null) {
            args += datasetId
            args += spec.rowColumnId
            args += spec.valueColumnId
            """
                SELECT
                    COALESCE(NULLIF(TRIM(rg.rawValue), ''), '(blank)') AS rowKey,
                    'Value' AS columnKey,
                    $aggregateSql AS value
                FROM data_rows r
                JOIN cells rg ON rg.rowId = r.id
                JOIN cells v ON v.rowId = r.id
                WHERE r.datasetId = ?
                  AND r.status = 'FINAL'
                  AND rg.columnId = ?
                  AND v.columnId = ?
                GROUP BY rg.normalizedValue
                ORDER BY rowKey COLLATE NOCASE
            """.trimIndent()
        } else {
            args += datasetId
            args += spec.rowColumnId
            args.add(requireNotNull(spec.columnColumnId))
            args += spec.valueColumnId
            """
                SELECT
                    COALESCE(NULLIF(TRIM(rg.rawValue), ''), '(blank)') AS rowKey,
                    COALESCE(NULLIF(TRIM(cg.rawValue), ''), '(blank)') AS columnKey,
                    $aggregateSql AS value
                FROM data_rows r
                JOIN cells rg ON rg.rowId = r.id
                JOIN cells cg ON cg.rowId = r.id
                JOIN cells v ON v.rowId = r.id
                WHERE r.datasetId = ?
                  AND r.status = 'FINAL'
                  AND rg.columnId = ?
                  AND cg.columnId = ?
                  AND v.columnId = ?
                GROUP BY rg.normalizedValue, cg.normalizedValue
                ORDER BY rowKey COLLATE NOCASE, columnKey COLLATE NOCASE
            """.trimIndent()
        }

        val cells = analyticsDao.queryPivot(SimpleSQLiteQuery(sql, args.toTypedArray()))
            .map { PivotCell(it.rowKey, it.columnKey, it.value) }

        return PivotResult(
            rowKeys = cells.map(PivotCell::rowKey).distinct(),
            columnKeys = cells.map(PivotCell::columnKey).distinct(),
            cells = cells,
        )
    }

    private suspend fun queryRowCount(datasetId: Long, filter: String?): Long {
        val args = mutableListOf<Any>(datasetId)
        val whereFilter = if (filter == null) {
            ""
        } else {
            args += "%${escapeLike(filter.lowercase(Locale.ROOT))}%"
            """
                AND EXISTS (
                    SELECT 1
                    FROM cells f
                    WHERE f.rowId = r.id
                      AND f.normalizedValue LIKE ? ESCAPE '\'
                )
            """.trimIndent()
        }
        val query = SimpleSQLiteQuery(
            "SELECT COUNT(*) AS value FROM data_rows r WHERE r.datasetId = ? $whereFilter",
            args.toTypedArray(),
        )
        return analyticsDao.queryLong(query)?.value ?: 0L
    }

    private fun buildRowQuery(
        datasetId: Long,
        limit: Int,
        offset: Int,
        sort: SortSpec?,
        sortColumn: ColumnEntity?,
        filter: String?,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val join = if (sort != null && sortColumn != null) {
            args += sort.columnId
            "LEFT JOIN cells s ON s.rowId = r.id AND s.columnId = ?"
        } else {
            ""
        }

        args += datasetId
        val whereFilter = if (filter == null) {
            ""
        } else {
            args += "%${escapeLike(filter.lowercase(Locale.ROOT))}%"
            """
                AND EXISTS (
                    SELECT 1
                    FROM cells f
                    WHERE f.rowId = r.id
                      AND f.normalizedValue LIKE ? ESCAPE '\'
                )
            """.trimIndent()
        }

        val order = if (sort != null && sortColumn != null) {
            val field = when (ColumnType.valueOf(sortColumn.type)) {
                ColumnType.INTEGER,
                ColumnType.DECIMAL,
                ColumnType.CURRENCY,
                ColumnType.PERCENTAGE -> "s.numericValue"
                ColumnType.DATE,
                ColumnType.DATETIME -> "s.instantValue"
                ColumnType.BOOLEAN -> "s.booleanValue"
                else -> "s.normalizedValue"
            }
            val direction = if (sort.direction == SortDirection.ASC) "ASC" else "DESC"
            "ORDER BY $field IS NULL, $field $direction, r.id ASC"
        } else {
            "ORDER BY r.id ASC"
        }

        args += limit
        args += offset
        val sql = """
            SELECT r.id AS value
            FROM data_rows r
            $join
            WHERE r.datasetId = ?
            $whereFilter
            $order
            LIMIT ? OFFSET ?
        """.trimIndent()

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun validateValues(
        columns: List<ColumnEntity>,
        values: Map<Long, String>,
        status: RowStatus,
    ) {
        val byId = columns.associateBy { it.id }
        require(values.keys.all(byId::containsKey)) {
            "One or more values target a column outside this dataset."
        }

        if (status == RowStatus.FINAL) {
            columns.filter { it.required }.forEach { column ->
                require(!values[column.id].isNullOrBlank()) {
                    "${column.displayName} is required before a row can be finalized."
                }
            }
        }

        values.forEach { (columnId, raw) ->
            if (raw.isBlank()) return@forEach
            val column = requireNotNull(byId[columnId])
            val type = ColumnType.valueOf(column.type)
            require(type != ColumnType.FORMULA) {
                "Formula columns cannot accept direct input."
            }
            if (status == RowStatus.FINAL) {
                requireEncodedValueIsValid(column, CellCodec.encode(raw, type))
            }
        }
    }

    private fun requireEncodedValueIsValid(column: ColumnEntity, value: CellValue) {
        val valid = when (ColumnType.valueOf(column.type)) {
            ColumnType.INTEGER,
            ColumnType.DECIMAL,
            ColumnType.CURRENCY,
            ColumnType.PERCENTAGE -> value.numericValue != null

            ColumnType.DATE,
            ColumnType.DATETIME -> value.instantValue != null

            ColumnType.BOOLEAN -> value.booleanValue != null
            else -> true
        }
        require(valid) {
            "${column.displayName} does not contain a valid ${column.type.lowercase()} value."
        }
    }

    private fun encodeCells(
        rowId: Long,
        columns: List<ColumnEntity>,
        values: Map<Long, String>,
    ): List<CellEntity> {
        val byId = columns.associateBy { it.id }
        return values.mapNotNull { (columnId, raw) ->
            if (raw.isBlank()) return@mapNotNull null
            val column = requireNotNull(byId[columnId])
            CellCodec.encode(raw, ColumnType.valueOf(column.type))
                .toEntity(rowId, columnId)
        }
    }

    /**
     * Canonical cell material is passed only to AuditWriter, which persists its
     * SHA-256 hash rather than duplicating sensitive row contents.
     */
    private fun List<CellEntity>.auditMaterial(): String =
        sortedBy { it.columnId }
            .joinToString("|") { cell -> "${cell.columnId}=${cell.rawValue}" }

    private fun aggregateExpression(aggregation: Aggregation): String =
        when (aggregation) {
            Aggregation.SUM -> "COALESCE(SUM(v.numericValue), 0.0)"
            Aggregation.COUNT -> "CAST(COUNT(v.rowId) AS REAL)"
            Aggregation.AVERAGE -> "COALESCE(AVG(v.numericValue), 0.0)"
            Aggregation.MIN -> "COALESCE(MIN(v.numericValue), 0.0)"
            Aggregation.MAX -> "COALESCE(MAX(v.numericValue), 0.0)"
        }

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "column" }

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")


    private fun DatasetEntity.toModel() = Dataset(
        id = id,
        name = name,
        kind = DatasetKind.valueOf(kind),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ColumnEntity.toModel() = DatasetColumn(
        id = id,
        datasetId = datasetId,
        key = key,
        displayName = displayName,
        type = ColumnType.valueOf(type),
        role = ColumnRole.valueOf(role),
        position = position,
        required = required,
        isProtected = isProtected,
    )

    private fun CellEntity.toModel() = CellValue(
        rawValue = rawValue,
        normalizedValue = normalizedValue,
        numericValue = numericValue,
        instantValue = instantValue,
        booleanValue = booleanValue,
    )

    private fun CellValue.toEntity(rowId: Long, columnId: Long) = CellEntity(
        rowId = rowId,
        columnId = columnId,
        rawValue = rawValue,
        normalizedValue = normalizedValue,
        numericValue = numericValue,
        instantValue = instantValue,
        booleanValue = booleanValue,
    )

}
