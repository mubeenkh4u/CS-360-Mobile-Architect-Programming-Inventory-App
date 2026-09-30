package com.auwire.iamkhata.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {
    @Test
    fun otherReservationsAreProtectedWhenCommittingSale() {
        assertFalse(
            InventoryRules.canCommitSale(
                onHandMicros = 10 * QUANTITY_SCALE,
                reservedByOtherDocumentsMicros = 4 * QUANTITY_SCALE,
                requestedMicros = 7 * QUANTITY_SCALE,
                allowNegativeCommittedStock = false,
            ),
        )
    }

    @Test
    fun tentativeShortageCanBeOverriddenOnlyByExplicitPolicy() {
        assertTrue(
            InventoryRules.canCommitSale(
                onHandMicros = QUANTITY_SCALE,
                reservedByOtherDocumentsMicros = 0,
                requestedMicros = 5 * QUANTITY_SCALE,
                allowNegativeCommittedStock = true,
            ),
        )
    }
}
