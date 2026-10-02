package com.auwire.iamkhata.core.model

/** Lifecycle for manually managed tabular rows. */
enum class RowStatus {
    DRAFT,
    FINAL,
}

/** Identifies which domain owns a row's source-of-truth mutation rules. */
enum class RowOrigin {
    MANUAL,
    IMPORT,
    STOCK_DOCUMENT,
}
