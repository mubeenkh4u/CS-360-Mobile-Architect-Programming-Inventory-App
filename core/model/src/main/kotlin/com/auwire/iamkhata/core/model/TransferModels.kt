package com.auwire.iamkhata.core.model

/** Summary returned by CSV import/export operations. */
data class TransferSummary(
    val importedRows: Int = 0,
    val exportedRows: Int = 0,
    val skippedRows: Int = 0,
    val addedColumns: Int = 0,
) {
    fun describe(): String = when {
        exportedRows > 0 -> "Exported $exportedRows rows."
        importedRows > 0 || skippedRows > 0 ->
            "Imported $importedRows rows; skipped $skippedRows; added $addedColumns columns."
        else -> "No rows were transferred."
    }
}
