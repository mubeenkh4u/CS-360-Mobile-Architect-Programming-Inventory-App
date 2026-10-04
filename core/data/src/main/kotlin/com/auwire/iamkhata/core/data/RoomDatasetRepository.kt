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
import com.auwire.iamkhata.core.model.RowLifecycleRules
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.SortDirection
import com.auwire.iamkhata.core.model.SortSpec
import com.auwire.iamkhata.core.model.TablePage
import com.auwire.iamkhata.core.model.TransformResult
import java.time.LocalDate
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
            if (DatasetKind.valueOf(dataset.kind) == DatasetKind.LEDGER) {
                require(
                    cleanName.lowercase(Locale.ROOT) !in setOf(
                        "state",
                        "balance",
                        "_awi&k state",
                        "_auwire status",
                    ),
                ) {
                    "State and Balance are system-managed ledger fields."
                }
            }
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
        require(status == RowStatus.DRAFT || status == RowStatus.FINAL) {
            "New rows can only be created as draft or final."
        }
        require(origin == RowOrigin.MANUAL || origin == RowOrigin.IMPORT) {
            "System row origins are reserved for domain services."
        }

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
        RowLifecycleRules.requireEditableTransition(
            current = RowStatus.valueOf(row.status),
            target = status,
        )

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

    override suspend fun voidRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        reason: String,
    ): Long = database.withTransaction {
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "A void reason is required." }

        val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Dataset not found." }
        val row = requireNotNull(datasetDao.getRow(rowId)) { "Row not found." }
        require(row.datasetId == datasetId) { "Row does not belong to this dataset." }
        require(!row.isLocked) { "System-managed rows cannot be voided from Khata." }
        require(RowStatus.valueOf(row.status) == RowStatus.FINAL) {
            "Only a final user-managed row can be voided."
        }

        val before = datasetDao.getCellsForRow(rowId)
        val now = System.currentTimeMillis()
        val updated = datasetDao.updateEditableRow(
            rowId = rowId,
            expectedRevision = expectedRevision,
            status = RowStatus.VOID.name,
            updatedAt = now,
        )
        require(updated == 1) {
            "The row changed before it could be voided. Reload it and try again."
        }

        datasetDao.updateDataset(dataset.copy(updatedAt = now))
        auditWriter.append(
            action = "VOID_ROW",
            targetType = "ROW",
            targetId = rowId.toString(),
            payloadSummary = "dataset=${datasetId};fromRevision=${expectedRevision};toRevision=${expectedRevision + 1};reason=${cleanReason};values=${before.auditMaterial()}",
            timestamp = now,
        )
        expectedRevision + 1
    }

    override suspend fun reverseRow(
        datasetId: Long,
        rowId: Long,
        expectedRevision: Long,
        reason: String,
    ): Long = database.withTransaction {
        val cleanReason = reason.trim()
        require(cleanReason.isNotEmpty()) { "A reversal reason is required." }

        val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Dataset not found." }
        require(DatasetKind.valueOf(dataset.kind) == DatasetKind.LEDGER) {
            "Reversal is only available for ledger rows."
        }

        val row = requireNotNull(datasetDao.getRow(rowId)) { "Row not found." }
        require(row.datasetId == datasetId) { "Row does not belong to this dataset." }
        require(!row.isLocked) { "System-managed rows must be corrected through their source workflow." }
        require(RowStatus.valueOf(row.status) == RowStatus.FINAL) {
            "Only a final user-managed row can be reversed."
        }

        val columns = datasetDao.getColumns(datasetId)
        val byRole = columns.associateBy { ColumnRole.valueOf(it.role) }
        val before = datasetDao.getCellsForRow(rowId)
        val originalValues = before.associate { it.columnId to it.rawValue }.toMutableMap()

        val debitColumn = requireNotNull(byRole[ColumnRole.DEBIT]) { "Ledger Debit column is missing." }
        val creditColumn = requireNotNull(byRole[ColumnRole.CREDIT]) { "Ledger Credit column is missing." }
        val originalDebit = originalValues[debitColumn.id].orEmpty()
        val originalCredit = originalValues[creditColumn.id].orEmpty()
        require(originalDebit.isNotBlank() || originalCredit.isNotBlank()) {
            "This row has no debit or credit amount to reverse."
        }

        originalValues.remove(debitColumn.id)
        originalValues.remove(creditColumn.id)
        if (originalDebit.isNotBlank()) originalValues[creditColumn.id] = originalDebit
        if (originalCredit.isNotBlank()) originalValues[debitColumn.id] = originalCredit

        byRole[ColumnRole.DATE]?.let { originalValues[it.id] = LocalDate.now().toString() }
        byRole[ColumnRole.PARTICULARS]?.let { particulars ->
            val source = before.firstOrNull { it.columnId == particulars.id }?.rawValue.orEmpty().trim()
            originalValues[particulars.id] =
                if (source.isBlank()) "Reversal of row #${rowId}" else "Reversal - ${source}"
        }
        byRole[ColumnRole.REFERENCE]?.let { reference ->
            val source = before.firstOrNull { it.columnId == reference.id }?.rawValue.orEmpty().trim()
            originalValues[reference.id] =
                (source.takeIf(String::isNotBlank) ?: "ROW-${rowId}") + "-REV-${rowId}"
        }
        byRole[ColumnRole.STOCK_STATUS]?.let { originalValues.remove(it.id) }

        validateValues(columns, originalValues, RowStatus.FINAL)

        val now = System.currentTimeMillis()
        val transitioned = datasetDao.updateEditableRow(
            rowId = rowId,
            expectedRevision = expectedRevision,
            status = RowStatus.REVERSED.name,
            updatedAt = now,
        )
        require(transitioned == 1) {
            "The row changed before it could be reversed. Reload it and try again."
        }

        val reversalRowId = datasetDao.insertRow(
            RowEntity(
                datasetId = datasetId,
                createdAt = now,
                updatedAt = now,
                status = RowStatus.FINAL.name,
                origin = RowOrigin.REVERSAL.name,
                originRef = rowId.toString(),
                isLocked = true,
            ),
        )
        val reversalCells = encodeCells(reversalRowId, columns, originalValues)
        if (reversalCells.isNotEmpty()) datasetDao.insertCells(reversalCells)

        datasetDao.updateDataset(dataset.copy(updatedAt = now))
        auditWriter.append(
            action = "REVERSE_ROW",
            targetType = "ROW",
            targetId = rowId.toString(),
            payloadSummary = "dataset=${datasetId};reversalRow=${reversalRowId};reason=${cleanReason};values=${before.auditMaterial()}",
            timestamp = now,
        )
        auditWriter.append(
            action = "APPEND_REVERSAL_ROW",
            targetType = "ROW",
            targetId = reversalRowId.toString(),
            payloadSummary = "dataset=${datasetId};sourceRow=${rowId};values=${reversalCells.auditMaterial()}",
            timestamp = now,
        )
        reversalRowId
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

        val columns = datasetDao.getColumns(datasetId)
        val sortColumn = sort?.let { spec -> columns.firstOrNull { it.id == spec.columnId } }
        require(sort == null || sortColumn != null) {
            "Sort column does not belong to this dataset."
        }
        val dateColumn = columns.firstOrNull { it.role == ColumnRole.DATE.name }

        val filterValue = filter?.query?.trim()?.takeIf(String::isNotEmpty)
        val rowQuery = buildRowQuery(
            datasetId = datasetId,
            limit = limit,
            offset = offset,
            sort = sort,
            sortColumn = sortColumn,
            defaultDateColumn = dateColumn,
            filter = filterValue,
        )
        val rowIds = analyticsDao.queryRowIds(rowQuery).map { it.value }
        val totalRows = queryRowCount(datasetId, filterValue)

        if (rowIds.isEmpty()) {
            return TablePage(emptyList(), totalRows, offset, limit)
        }

        val rowsById = datasetDao.getRows(rowIds).associateBy { it.id }
        val cellsByRow = datasetDao.getCellsForRows(rowIds).groupBy { it.rowId }
        val balances = queryLedgerBalances(datasetId, columns, rowIds)
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
                    balanceMinor = balances[rowId],
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

        val aggregateSql = aggregateExpression(
            aggregation = spec.aggregation,
            valueColumn = requireNotNull(columns[spec.valueColumnId]),
        )
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
                  AND r.status IN ('FINAL', 'REVERSED')
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
                  AND r.status IN ('FINAL', 'REVERSED')
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

    /**
     * Computes official running balances before display filtering/paging.
     *
     * A correlated exact-minor-unit sum is used instead of SQLite window
     * functions so the calculation remains compatible with the app's API 26
     * floor. The composite cell indexes keep Party/date/money lookups bounded to
     * the requested page rather than loading the full ledger into application
     * memory.
     *
     * Balance is partitioned by normalized Party and ordered by accounting date,
     * creation time, then row id. UI sorting/search never changes the balance.
     * DRAFT and VOID rows are excluded; REVERSED source rows remain official so
     * their immutable reversal entry can offset them on the reversal date.
     */
    private suspend fun queryLedgerBalances(
        datasetId: Long,
        columns: List<ColumnEntity>,
        requestedRowIds: List<Long>,
    ): Map<Long, Long?> {
        if (requestedRowIds.isEmpty()) return emptyMap()

        val byRole = columns.associateBy { ColumnRole.valueOf(it.role) }
        val party = byRole[ColumnRole.PARTY] ?: return emptyMap()
        val date = byRole[ColumnRole.DATE] ?: return emptyMap()
        val debit = byRole[ColumnRole.DEBIT] ?: return emptyMap()
        val credit = byRole[ColumnRole.CREDIT] ?: return emptyMap()

        val placeholders = requestedRowIds.joinToString(",") { "?" }
        val query = SimpleSQLiteQuery(
            """
                SELECT
                    r.id AS rowId,
                    CASE
                        WHEN r.status NOT IN ('FINAL', 'REVERSED')
                          OR NULLIF(TRIM(p.normalizedValue), '') IS NULL
                        THEN NULL
                        ELSE COALESCE(
                            (
                                SELECT SUM(
                                    COALESCE(db2.moneyMinorValue, 0)
                                    - COALESCE(cr2.moneyMinorValue, 0)
                                )
                                FROM data_rows r2
                                JOIN cells p2
                                  ON p2.rowId = r2.id
                                 AND p2.columnId = ?
                                LEFT JOIN cells d2
                                  ON d2.rowId = r2.id
                                 AND d2.columnId = ?
                                LEFT JOIN cells db2
                                  ON db2.rowId = r2.id
                                 AND db2.columnId = ?
                                LEFT JOIN cells cr2
                                  ON cr2.rowId = r2.id
                                 AND cr2.columnId = ?
                                WHERE r2.datasetId = ?
                                  AND r2.status IN ('FINAL', 'REVERSED')
                                  AND p2.normalizedValue = p.normalizedValue
                                  AND (
                                      COALESCE(d2.instantValue, 9223372036854775807)
                                          < COALESCE(d.instantValue, 9223372036854775807)
                                      OR (
                                          COALESCE(d2.instantValue, 9223372036854775807)
                                              = COALESCE(d.instantValue, 9223372036854775807)
                                          AND (
                                              r2.createdAt < r.createdAt
                                              OR (
                                                  r2.createdAt = r.createdAt
                                                  AND r2.id <= r.id
                                              )
                                          )
                                      )
                                  )
                            ),
                            0
                        )
                    END AS balanceMinor
                FROM data_rows r
                LEFT JOIN cells p
                  ON p.rowId = r.id
                 AND p.columnId = ?
                LEFT JOIN cells d
                  ON d.rowId = r.id
                 AND d.columnId = ?
                WHERE r.datasetId = ?
                  AND r.id IN ($placeholders)
            """.trimIndent(),
            // SQL placeholder order is outer Party/Date after the correlated
            // subquery placeholders, so reorder bound args to match the text.
            arrayOf(
                party.id,
                date.id,
                debit.id,
                credit.id,
                datasetId,
                party.id,
                date.id,
                datasetId,
                *requestedRowIds.toTypedArray(),
            ),
        )

        return analyticsDao.queryBalances(query).associate { it.rowId to it.balanceMinor }
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
        defaultDateColumn: ColumnEntity?,
        filter: String?,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val join = when {
            sort != null && sortColumn != null -> {
                args += sort.columnId
                "LEFT JOIN cells s ON s.rowId = r.id AND s.columnId = ?"
            }

            defaultDateColumn != null -> {
                args += defaultDateColumn.id
                "LEFT JOIN cells dfltDate ON dfltDate.rowId = r.id AND dfltDate.columnId = ?"
            }

            else -> ""
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
        } else if (defaultDateColumn != null) {
            "ORDER BY dfltDate.instantValue IS NULL, dfltDate.instantValue ASC, r.createdAt ASC, r.id ASC"
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

        if (status == RowStatus.FINAL) {
            val byRole = columns.associateBy { ColumnRole.valueOf(it.role) }
            val debitColumn = byRole[ColumnRole.DEBIT]
            val creditColumn = byRole[ColumnRole.CREDIT]
            if (debitColumn != null && creditColumn != null) {
                val debit = values[debitColumn.id]
                    ?.takeIf(String::isNotBlank)
                    ?.let { CellCodec.encode(it, ColumnType.CURRENCY).moneyMinorValue }
                    ?: 0L
                val credit = values[creditColumn.id]
                    ?.takeIf(String::isNotBlank)
                    ?.let { CellCodec.encode(it, ColumnType.CURRENCY).moneyMinorValue }
                    ?: 0L

                require(debit >= 0L && credit >= 0L) {
                    "Debit and Credit must be positive amounts; use the opposite side instead of a negative value."
                }
                require(!(debit > 0L && credit > 0L)) {
                    "A ledger row cannot contain both a Debit and a Credit amount."
                }

                if (debit > 0L || credit > 0L) {
                    val partyColumn = byRole[ColumnRole.PARTY]
                    require(
                        partyColumn != null &&
                            !values[partyColumn.id].isNullOrBlank()
                    ) {
                        "Party is required for a finalized financial row so its running balance has an account."
                    }
                }
            }
        }
    }

    private fun requireEncodedValueIsValid(column: ColumnEntity, value: CellValue) {
        val valid = when (ColumnType.valueOf(column.type)) {
            ColumnType.INTEGER,
            ColumnType.DECIMAL,
            ColumnType.PERCENTAGE -> value.numericValue != null

            ColumnType.CURRENCY -> value.moneyMinorValue != null

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

    private fun aggregateExpression(
        aggregation: Aggregation,
        valueColumn: ColumnEntity,
    ): String {
        val valueExpression =
            if (ColumnType.valueOf(valueColumn.type) == ColumnType.CURRENCY) {
                "(CAST(v.moneyMinorValue AS REAL) / 100.0)"
            } else {
                "v.numericValue"
            }

        return when (aggregation) {
            Aggregation.SUM -> "COALESCE(SUM(@@valueExpression), 0.0)"
            Aggregation.COUNT -> "CAST(COUNT(v.rowId) AS REAL)"
            Aggregation.AVERAGE -> "COALESCE(AVG(@@valueExpression), 0.0)"
            Aggregation.MIN -> "COALESCE(MIN(@@valueExpression), 0.0)"
            Aggregation.MAX -> "COALESCE(MAX(@@valueExpression), 0.0)"
        }
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
        moneyMinorValue = moneyMinorValue,
        instantValue = instantValue,
        booleanValue = booleanValue,
    )

    private fun CellValue.toEntity(rowId: Long, columnId: Long) = CellEntity(
        rowId = rowId,
        columnId = columnId,
        rawValue = rawValue,
        normalizedValue = normalizedValue,
        numericValue = numericValue,
        moneyMinorValue = moneyMinorValue,
        instantValue = instantValue,
        booleanValue = booleanValue,
    )

}
