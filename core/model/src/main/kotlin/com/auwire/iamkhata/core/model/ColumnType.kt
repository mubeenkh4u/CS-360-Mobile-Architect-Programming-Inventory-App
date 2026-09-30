package com.auwire.iamkhata.core.model

/**
 * Logical value types supported by the tabular engine.
 *
 * Raw source text is always preserved; typed projections are derived for search,
 * sorting and aggregation.
 */
enum class ColumnType {
    TEXT,
    INTEGER,
    DECIMAL,
    CURRENCY,
    PERCENTAGE,
    DATE,
    DATETIME,
    BOOLEAN,
    CATEGORY,
    FORMULA,
}
