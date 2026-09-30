package com.auwire.iamkhata.core.model

/** Sort order applied to a runtime column. */
data class SortSpec(
    val columnId: Long,
    val direction: SortDirection,
)

enum class SortDirection {
    ASC,
    DESC,
}

/** Global text search across cells in a dataset. */
data class FilterSpec(
    val query: String,
)

/** Supported read-only aggregation functions. */
enum class Aggregation {
    SUM,
    COUNT,
    AVERAGE,
    MIN,
    MAX,
}

/** Visual pivot definition. columnColumnId may be null for a one-dimensional group. */
data class PivotSpec(
    val rowColumnId: Long,
    val columnColumnId: Long?,
    val valueColumnId: Long,
    val aggregation: Aggregation,
)

/** One materialized cell in a pivot result. */
data class PivotCell(
    val rowKey: String,
    val columnKey: String,
    val value: Double,
)

/** Read-only result created by the analytics engine. */
data class PivotResult(
    val rowKeys: List<String>,
    val columnKeys: List<String>,
    val cells: List<PivotCell>,
)
