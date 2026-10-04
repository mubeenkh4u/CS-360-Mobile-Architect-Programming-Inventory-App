package com.auwire.iamkhata.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedPointBalanceTest {
    @Test
    fun balanceDisplayUsesDrCrSemantics() {
        assertEquals("Rs. 30400.00 DR", FixedPoint.balanceDisplay(3_040_000L))
        assertEquals("Rs. 42500.00 CR", FixedPoint.balanceDisplay(-4_250_000L))
        assertEquals("Rs. 0.00", FixedPoint.balanceDisplay(0L))
    }
}
