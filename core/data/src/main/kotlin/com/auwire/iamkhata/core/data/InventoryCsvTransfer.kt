package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.TransferSummary
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

/**
 * Product-master and base-stock CSV interchange.
 *
 * Existing SKUs are skipped on import rather than overwritten. Tentative
 * reservations/incoming quantities are exported for visibility but never
 * imported as stock because their source of truth is a stock document.
 */
class InventoryCsvTransfer(
    private val repository: InventoryRepository,
) {
    suspend fun exportInventory(output: OutputStream): TransferSummary =
        withContext(Dispatchers.IO) {
            val stock = repository.observeStock().first()

            OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
                CSVPrinter(writer, CSVFormat.DEFAULT).use { printer ->
                    printer.printRecord(HEADERS)
                    stock.forEach { snapshot ->
                        printer.printRecord(
                            snapshot.product.sku,
                            snapshot.product.name,
                            snapshot.product.unit,
                            FixedPoint.quantityDecimal(snapshot.product.reorderLevelMicros),
                            snapshot.product.defaultSaleRateMinor?.let(FixedPoint::moneyDecimal).orEmpty(),
                            snapshot.product.defaultPurchaseRateMinor?.let(FixedPoint::moneyDecimal).orEmpty(),
                            FixedPoint.quantityDecimal(snapshot.onHandMicros),
                            FixedPoint.quantityDecimal(snapshot.reservedOutgoingMicros),
                            FixedPoint.quantityDecimal(snapshot.tentativeIncomingMicros),
                            FixedPoint.quantityDecimal(snapshot.availableToPromiseMicros),
                            FixedPoint.quantityDecimal(snapshot.projectedMicros),
                        )
                    }
                }
            }

            TransferSummary(exportedRows = stock.size)
        }

    suspend fun importInventory(input: InputStream): TransferSummary =
        withContext(Dispatchers.IO) {
            InputStreamReader(input, Charsets.UTF_8).buffered().use { reader ->
                val parser = CSVFormat.DEFAULT.parse(reader)
                val iterator = parser.iterator()
                if (!iterator.hasNext()) return@withContext TransferSummary()

                val header = iterator.next()
                    .mapIndexed { index, value ->
                        value.trim().lowercase(Locale.ROOT) to index
                    }
                    .toMap()

                requireRequiredHeaders(header)

                val existing = repository.observeStock().first()
                    .map { it.product.sku.uppercase(Locale.ROOT) }
                    .toMutableSet()

                var imported = 0
                var skipped = 0

                while (iterator.hasNext()) {
                    val record = iterator.next()
                    val sku = value(record, header, "sku")
                        .trim()
                        .uppercase(Locale.ROOT)
                    val name = value(record, header, "name").trim()
                    val unit = value(record, header, "unit").trim()

                    if (
                        sku.isBlank() ||
                        name.isBlank() ||
                        unit.isBlank() ||
                        sku in existing
                    ) {
                        skipped += 1
                        continue
                    }

                    val applied = runCatching {
                        val reorder = value(record, header, "reorder level")
                            .takeIf(String::isNotBlank)
                            ?.let(FixedPoint::parseQuantity)
                            ?: 0L
                        val saleRate = value(record, header, "default sale rate")
                            .takeIf(String::isNotBlank)
                            ?.let(FixedPoint::parseMoney)
                        val purchaseRate = value(record, header, "default purchase rate")
                            .takeIf(String::isNotBlank)
                            ?.let(FixedPoint::parseMoney)
                        val baseStock = value(record, header, "base stock")
                            .takeIf(String::isNotBlank)
                            ?.let(FixedPoint::parseQuantity)
                            ?: 0L

                        require(baseStock >= 0) {
                            "Imported base stock cannot be negative."
                        }

                        val productId = repository.createProduct(
                            sku = sku,
                            name = name,
                            unit = unit,
                            reorderLevelMicros = reorder,
                            defaultSaleRateMinor = saleRate,
                            defaultPurchaseRateMinor = purchaseRate,
                        )
                        if (baseStock > 0) {
                            repository.adjustBaseStock(
                                productId = productId,
                                quantityDeltaMicros = baseStock,
                                note = "CSV import opening stock",
                            )
                        }
                    }.isSuccess

                    if (applied) {
                        existing += sku
                        imported += 1
                    } else {
                        skipped += 1
                    }
                }

                TransferSummary(
                    importedRows = imported,
                    skippedRows = skipped,
                )
            }
        }

    private fun requireRequiredHeaders(header: Map<String, Int>) {
        REQUIRED_HEADERS.forEach { name ->
            require(header.containsKey(name)) {
                "Inventory CSV is missing required column: $name."
            }
        }
    }

    private fun value(
        record: CSVRecord,
        header: Map<String, Int>,
        name: String,
    ): String {
        val index = header[name] ?: return ""
        return if (index < record.size()) record[index] else ""
    }

    companion object {
        private val HEADERS = listOf(
            "SKU",
            "Name",
            "Unit",
            "Reorder Level",
            "Default Sale Rate",
            "Default Purchase Rate",
            "Base Stock",
            "Reserved Sales",
            "Tentative Incoming",
            "Available To Promise",
            "Projected",
        )
        private val REQUIRED_HEADERS = setOf("sku", "name", "unit")
    }
}
