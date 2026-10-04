package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.ColumnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellCodecTest {
    @Test
    fun currencyParsingPreservesRawValueAndCreatesExactMinorUnits() {
        val value = CellCodec.encode("Rs. 48,500.25/-", ColumnType.CURRENCY)

        assertEquals("Rs. 48,500.25/-", value.rawValue)
        assertEquals(48_500.25, value.numericValue!!, 0.001)
        assertEquals(4_850_025L, value.moneyMinorValue)
    }

    @Test
    fun currencyRejectsMoreThanTwoDecimalPlaces() {
        val value = CellCodec.encode("10.001", ColumnType.CURRENCY)

        assertNull(value.numericValue)
        assertNull(value.moneyMinorValue)
    }

    @Test
    fun invalidNumberDoesNotInventNumericValue() {
        val value = CellCodec.encode("not-a-number", ColumnType.DECIMAL)

        assertNull(value.numericValue)
    }

    @Test
    fun commonLedgerDateParsesToInstantProjection() {
        val value = CellCodec.encode("04/09/2026", ColumnType.DATE)

        assertEquals(true, value.instantValue != null)
    }
}
