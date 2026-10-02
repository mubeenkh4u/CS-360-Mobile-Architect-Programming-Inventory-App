package com.auwire.iamkhata.core.model

/** A logical table managed by the workbench. */
data class Dataset(
    val id: Long,
    val name: String,
    val kind: DatasetKind,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Dataset categories can add behavior without changing the storage shape. */
enum class DatasetKind {
    LEDGER,
    TABLE,
}

/** Runtime column metadata. */
data class DatasetColumn(
    val id: Long,
    val datasetId: Long,
    val key: String,
    val displayName: String,
    val type: ColumnType,
    val role: ColumnRole,
    val position: Int,
    val required: Boolean,
    val isProtected: Boolean,
)

/**
 * Cell data exposed to UI/domain code.
 *
 * rawValue is never discarded by type parsing. The remaining projections are
 * optional and exist only when the source value can be safely interpreted.
 */
data class CellValue(
    val rawValue: String,
    val normalizedValue: String,
    val numericValue: Double? = null,
    val instantValue: Long? = null,
    val booleanValue: Boolean? = null,
)

/**
 * One logical row in a dataset.
 *
 * System-owned rows (for example committed stock postings) are locked so a
 * generic table editor cannot break cross-domain integrity.
 */
data class DataRow(
    val id: Long,
    val datasetId: Long,
    val revision: Long,
    val status: RowStatus,
    val origin: RowOrigin,
    val originRef: String?,
    val isLocked: Boolean,
    val values: Map<Long, CellValue>,
)

/** Paged table result suitable for large datasets. */
data class TablePage(
    val rows: List<DataRow>,
    val totalRows: Long,
    val offset: Int,
    val limit: Int,
)
