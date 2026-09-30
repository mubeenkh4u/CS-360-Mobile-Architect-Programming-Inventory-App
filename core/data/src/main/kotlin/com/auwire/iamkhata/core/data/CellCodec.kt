package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.CellValue
import com.auwire.iamkhata.core.model.ColumnType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Converts source text into typed projections without discarding the source.
 *
 * Parsing failure never invents a value: the raw and normalized strings remain
 * available and the incompatible typed projection stays null.
 */
object CellCodec {
    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("M/d/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
    )

    fun encode(raw: String, type: ColumnType): CellValue {
        val trimmed = raw.trim()
        val normalized = trimmed.lowercase(Locale.ROOT)

        return when (type) {
            ColumnType.INTEGER,
            ColumnType.DECIMAL,
            ColumnType.CURRENCY,
            ColumnType.PERCENTAGE -> CellValue(
                rawValue = raw,
                normalizedValue = normalized,
                numericValue = parseNumber(trimmed),
            )

            ColumnType.DATE -> CellValue(
                rawValue = raw,
                normalizedValue = normalized,
                instantValue = parseDate(trimmed),
            )

            ColumnType.DATETIME -> CellValue(
                rawValue = raw,
                normalizedValue = normalized,
                instantValue = parseDateTime(trimmed),
            )

            ColumnType.BOOLEAN -> CellValue(
                rawValue = raw,
                normalizedValue = normalized,
                booleanValue = parseBoolean(trimmed),
            )

            else -> CellValue(rawValue = raw, normalizedValue = normalized)
        }
    }

    private fun parseNumber(value: String): Double? {
        if (value.isBlank()) return null

        val sanitized = value
            .replace(Regex("(?i)rs\\.?"), "")
            .replace(",", "")
            .replace("/-", "")
            .replace("%", "")
            .replace(Regex("\\s+"), "")

        return sanitized.toDoubleOrNull()
    }

    private fun parseDate(value: String): Long? =
        dateFormats.firstNotNullOfOrNull { formatter ->
            runCatching {
                LocalDate.parse(value, formatter)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()
        }

    private fun parseDateTime(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            }.getOrNull()

    private fun parseBoolean(value: String): Boolean? =
        when (value.lowercase(Locale.ROOT)) {
            "true", "yes", "y", "1" -> true
            "false", "no", "n", "0" -> false
            else -> null
        }
}
