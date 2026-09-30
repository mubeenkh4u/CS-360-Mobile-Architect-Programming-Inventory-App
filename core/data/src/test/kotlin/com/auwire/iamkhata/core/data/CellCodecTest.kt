package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.ColumnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellCodecTest {
    @Test
    fun currencyParsingPreservesRawValueAndCreatesNumericProjection() {
        val value = CellCodec.encode("Rs. 48,500/-", ColumnType.CURRENCY)

        assertEquals("Rs. 48,500/-", value.rawValue)
        assertEquals(48_500.0, value.numericValue!!, 0.001)
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
