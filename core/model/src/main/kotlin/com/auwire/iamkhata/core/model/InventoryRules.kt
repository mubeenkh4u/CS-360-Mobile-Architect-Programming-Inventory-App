package com.auwire.iamkhata.core.model

/** Pure stock policy functions kept independent of Android/database code. */
object InventoryRules {
    fun canCommitSale(
        onHandMicros: Long,
        reservedByOtherDocumentsMicros: Long,
        requestedMicros: Long,
        allowNegativeCommittedStock: Boolean,
    ): Boolean {
        require(requestedMicros > 0) { "Requested quantity must be positive." }
        if (allowNegativeCommittedStock) return true

        val availableAfterOtherReservations =
            onHandMicros - reservedByOtherDocumentsMicros
        return availableAfterOtherReservations >= requestedMicros
    }
}
