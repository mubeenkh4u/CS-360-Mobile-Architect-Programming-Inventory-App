package com.auwire.iamkhata.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Shared fixed-point conversion helpers.
 *
 * Business values are converted at the boundary and remain integers in the
 * source-of-truth database. Formatting never changes stored values.
 */
object FixedPoint {
    fun parseQuantity(value: String): Long =
        parseScaled(value, QUANTITY_SCALE, 6, "quantity")

    fun parseMoney(value: String): Long =
        parseScaled(value, MONEY_SCALE, 2, "amount")

    fun quantityDecimal(micros: Long): String =
        BigDecimal.valueOf(micros)
            .divide(BigDecimal.valueOf(QUANTITY_SCALE))
            .stripTrailingZeros()
            .toPlainString()

    fun moneyDecimal(minor: Long): String =
        BigDecimal.valueOf(minor)
            .divide(BigDecimal.valueOf(MONEY_SCALE))
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString()

    fun moneyDisplay(minor: Long, prefix: String = "Rs. "): String =
        prefix + moneyDecimal(minor)

    /**
     * Computes quantity × rate using integer-backed decimal arithmetic and
     * returns the result in minor currency units.
     */
    fun lineTotalMinor(quantityMicros: Long, unitRateMinor: Long): Long {
        require(quantityMicros >= 0) { "Quantity cannot be negative." }
        require(unitRateMinor >= 0) { "Rate cannot be negative." }

        return BigDecimal.valueOf(quantityMicros)
            .multiply(BigDecimal.valueOf(unitRateMinor))
            .divide(BigDecimal.valueOf(QUANTITY_SCALE), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun parseScaled(
        source: String,
        scale: Long,
        decimalPlaces: Int,
        label: String,
    ): Long {
        val clean = source
            .trim()
            .replace(",", "")
            .replace(Regex("(?i)^rs\\.?\\s*"), "")
            .removeSuffix("/-")
            .trim()

        require(clean.isNotEmpty()) { "$label is required." }
        return runCatching {
            BigDecimal(clean)
                .setScale(decimalPlaces, RoundingMode.UNNECESSARY)
                .multiply(BigDecimal.valueOf(scale))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrElse {
            throw IllegalArgumentException(
                "Invalid $label or too many decimal places.",
                it,
            )
        }
    }
}
