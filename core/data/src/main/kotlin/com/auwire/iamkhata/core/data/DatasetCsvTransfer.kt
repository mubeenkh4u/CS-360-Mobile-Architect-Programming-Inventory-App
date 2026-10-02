package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.TransferSummary
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

/**
 * CSV interchange for one flexible dataset.
 *
 * Imports are intentionally staged as DRAFT rows. CSV is an interchange format,
 * not a trusted database backup, so imported data remains reviewable before it
 * becomes final business data.
 */
class DatasetCsvTransfer(
    private val repository: DatasetRepository,
) {
    suspend fun exportDataset(
        datasetId: Long,
        output: OutputStream,
    ): TransferSummary = withContext(Dispatchers.IO) {
        val columns = repository.observeColumns(datasetId).first()
        var exported = 0
        var offset = 0

        OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
            CSVPrinter(writer, CSVFormat.DEFAULT).use { printer ->
                printer.printRecord(
                    buildList {
                        add(STATUS_HEADER)
                        addAll(columns.map { it.displayName })
                    },
                )

                do {
                    val page = repository.loadPage(
                        datasetId = datasetId,
                        limit = EXPORT_PAGE_SIZE,
                        offset = offset,
                    )
                    page.rows.forEach { row ->
                        printer.printRecord(
                            buildList {
                                add(row.status.name)
                                columns.forEach { column ->
                                    add(row.values[column.id]?.rawValue.orEmpty())
                                }
                            },
                        )
                        exported += 1
                    }
                    offset += page.rows.size
                } while (page.rows.isNotEmpty() && offset < page.totalRows)
            }
        }

        TransferSummary(exportedRows = exported)
    }

    suspend fun importDataset(
        datasetId: Long,
        input: InputStream,
    ): TransferSummary = withContext(Dispatchers.IO) {
        InputStreamReader(input, Charsets.UTF_8).buffered().use { reader ->
            val parser = CSVFormat.DEFAULT.parse(reader)
            val iterator = parser.iterator()
            if (!iterator.hasNext()) return@withContext TransferSummary()

            val header = iterator.next().map { it.trim() }
            val statusIndex = header.indexOfFirst {
                it.equals(STATUS_HEADER, ignoreCase = true)
            }

            var columns = repository.observeColumns(datasetId).first()
            var addedColumns = 0
            val indexToColumnId = mutableMapOf<Int, Long>()

            header.forEachIndexed { index, name ->
                if (index == statusIndex || name.isBlank()) return@forEachIndexed

                val existing = columns.firstOrNull {
                    it.displayName.equals(name, ignoreCase = true)
                }
                val columnId = existing?.id ?: repository.addColumn(
                    datasetId = datasetId,
                    displayName = name,
                    type = ColumnType.TEXT,
                    role = ColumnRole.NONE,
                ).also {
                    addedColumns += 1
                    columns = repository.observeColumns(datasetId).first()
                }
                indexToColumnId[index] = columnId
            }

            var imported = 0
            var skipped = 0

            while (iterator.hasNext()) {
                val record = iterator.next()
                val values = buildMap<Long, String> {
                    indexToColumnId.forEach { (index, columnId) ->
                        put(
                            columnId,
                            if (index < record.size()) record[index] else "",
                        )
                    }
                }

                if (values.values.all(String::isBlank)) {
                    skipped += 1
                    continue
                }

                repository.appendRow(
                    datasetId = datasetId,
                    values = values,
                    status = RowStatus.DRAFT,
                    origin = RowOrigin.IMPORT,
                )
                imported += 1
            }

            TransferSummary(
                importedRows = imported,
                skippedRows = skipped,
                addedColumns = addedColumns,
            )
        }
    }

    companion object {
        private const val STATUS_HEADER = "_Auwire Status"
        private const val EXPORT_PAGE_SIZE = 500
    }
}
