package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.database.CellEntity
import com.auwire.iamkhata.core.database.ColumnEntity
import com.auwire.iamkhata.core.database.DatasetEntity
import com.auwire.iamkhata.core.database.IamDatabase
import com.auwire.iamkhata.core.database.RowEntity
import com.auwire.iamkhata.core.model.CellValue
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.DatasetKind
import com.auwire.iamkhata.core.model.LedgerDefaults
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import java.util.Locale

/**
 * Internal bridge for atomic domain postings into the dynamic Khata dataset.
 *
 * Inventory posts by semantic [ColumnRole]. Generated rows are FINAL and
 * system-locked so generic edits cannot desynchronize stock and accounting.
 */
internal class LedgerRowWriter(
    private val database: IamDatabase,
    private val auditWriter: AuditWriter,
) {
    private val datasetDao = database.datasetDao()

    suspend fun ensureLedger(): Long {
        val now = System.currentTimeMillis()
        val existing = datasetDao.getDatasetByName(LedgerDefaults.DATASET_NAME)
        val datasetId = existing?.id ?: datasetDao.insertDataset(
            DatasetEntity(
                name = LedgerDefaults.DATASET_NAME,
                kind = DatasetKind.LEDGER.name,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val current = datasetDao.getColumns(datasetId)
        val roles = current.map { it.role }.toSet()
        var nextPosition = (current.maxOfOrNull { it.position } ?: -1) + 1

        val missing = LedgerDefaults.columns
            .filterNot { definition -> definition.role.name in roles }
            .map { definition ->
                ColumnEntity(
                    datasetId = datasetId,
                    key = uniqueKey(definition.name, nextPosition),
                    displayName = definition.name,
                    type = definition.type.name,
                    role = definition.role.name,
                    position = nextPosition++,
                    required = definition.required,
                    isProtected = definition.isProtected,
                )
            }

        if (missing.isNotEmpty()) {
            datasetDao.insertColumns(missing)
            auditWriter.append(
                action = if (existing == null) "CREATE_DATASET" else "EXTEND_LEDGER_SCHEMA",
                targetType = "DATASET",
                targetId = datasetId.toString(),
                payloadSummary = "addedRoles=${missing.joinToString(",") { it.role }}",
                timestamp = now,
            )
        }

        return datasetId
    }

    suspend fun appendByRole(
        datasetId: Long,
        values: Map<ColumnRole, String>,
        auditAction: String,
        auditTargetId: String,
        timestamp: Long,
    ): Long {
        val dataset = requireNotNull(datasetDao.getDataset(datasetId)) { "Ledger not found." }
        val columns = datasetDao.getColumns(datasetId)
        val byRole = columns.associateBy { ColumnRole.valueOf(it.role) }

        values.keys.forEach { role ->
            require(byRole.containsKey(role)) { "Ledger column for $role is missing." }
        }
        columns.filter { it.required }.forEach { column ->
            val role = ColumnRole.valueOf(column.role)
            require(!values[role].isNullOrBlank()) {
                "${column.displayName} is required for ledger posting."
            }
        }

        val rowId = datasetDao.insertRow(
            RowEntity(
                datasetId = datasetId,
                createdAt = timestamp,
                updatedAt = timestamp,
                status = RowStatus.FINAL.name,
                origin = RowOrigin.STOCK_DOCUMENT.name,
                originRef = auditTargetId,
                isLocked = true,
            ),
        )

        val cells = values.mapNotNull { (role, raw) ->
            if (raw.isBlank()) return@mapNotNull null
            val column = requireNotNull(byRole[role])
            CellCodec.encode(raw, ColumnType.valueOf(column.type))
                .toEntity(rowId, column.id)
        }
        if (cells.isNotEmpty()) datasetDao.insertCells(cells)
        datasetDao.updateDataset(dataset.copy(updatedAt = timestamp))

        auditWriter.append(
            action = auditAction,
            targetType = "LEDGER_ROW",
            targetId = rowId.toString(),
            payloadSummary = "source=$auditTargetId;cells=${cells.size}",
            timestamp = timestamp,
        )
        return rowId
    }

    private fun uniqueKey(name: String, position: Int): String =
        name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "column" } + "_${position + 1}"

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
