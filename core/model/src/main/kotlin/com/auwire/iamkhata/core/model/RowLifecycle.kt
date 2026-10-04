package com.auwire.iamkhata.core.model

/**
 * Lifecycle for Khata rows.
 *
 * FINAL is one-way: a posted row never returns to DRAFT. Incorrect FINAL rows
 * are corrected as a new revision, explicitly VOIDed, or explicitly REVERSED.
 */
enum class RowStatus {
    DRAFT,
    FINAL,
    REVERSED,
    VOID,
}

/** Identifies which domain owns a row's source-of-truth mutation rules. */
enum class RowOrigin {
    MANUAL,
    IMPORT,
    STOCK_DOCUMENT,
    REVERSAL,
}

/** Centralized lifecycle transition rules shared by data and UI layers. */
object RowLifecycleRules {
    fun requireEditableTransition(current: RowStatus, target: RowStatus) {
        when (current) {
            RowStatus.DRAFT -> require(target == RowStatus.DRAFT || target == RowStatus.FINAL) {
                "Draft rows can only remain draft or be finalized."
            }

            RowStatus.FINAL -> require(target == RowStatus.FINAL) {
                "Final rows cannot return to draft. Correct, void, or reverse the row instead."
            }

            RowStatus.REVERSED -> error("Reversed rows are historical records and cannot be edited.")
            RowStatus.VOID -> error("Voided rows are historical records and cannot be edited.")
        }
    }

    fun isOfficial(status: RowStatus): Boolean =
        status == RowStatus.FINAL || status == RowStatus.REVERSED
}
