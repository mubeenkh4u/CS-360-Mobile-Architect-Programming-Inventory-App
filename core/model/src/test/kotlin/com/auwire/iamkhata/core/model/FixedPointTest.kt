package com.auwire.iamkhata.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedPointTest {
    @Test
    fun parsesFractionalQuantityWithoutFloatingPoint() {
        assertEquals(12_500_000L, FixedPoint.parseQuantity("12.5"))
    }

    @Test
    fun parsesPakistaniCurrencyFormatting() {
        assertEquals(4_850_000L, FixedPoint.parseMoney("Rs. 48,500.00"))
    }

    @Test
    fun lineTotalUsesFixedPointMath() {
        val total = FixedPoint.lineTotalMinor(
            quantityMicros = FixedPoint.parseQuantity("28"),
            unitRateMinor = FixedPoint.parseMoney("1564.29"),
        )

        assertEquals(4_380_012L, total)
    }
}
