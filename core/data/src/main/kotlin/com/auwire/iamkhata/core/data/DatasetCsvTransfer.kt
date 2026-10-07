package com.auwire.iamkhata.core.data

import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.ColumnType
import com.auwire.iamkhata.core.model.FilterSpec
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.RowOrigin
import com.auwire.iamkhata.core.model.RowStatus
import com.auwire.iamkhata.core.model.SortSpec
import com.auwire.iamkhata.core.model.TransferSummary
import com.auwire.iamkhata.core.model.displayState
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

class DatasetCsvTransfer(
    private val repository: DatasetRepository,
) {
    suspend fun exportDataset(
        datasetId: Long,
        output: OutputStream,
        sort: SortSpec? = null,
        filter: FilterSpec? = null,
    ): TransferSummary = withContext(Dispatchers.IO) {
        val columns = repository.observeColumns(datasetId).first()
        var exported = 0
        var offset = 0

        OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
            CSVPrinter(writer, CSVFormat.DEFAULT).use { printer ->
                printer.printRecord(
                    buildList {
                        add(STATE_HEADER)
                        columns.forEach { column ->
                            add(column.displayName)
                            if (column.role == ColumnRole.CREDIT) add(BALANCE_HEADER)
                        }
                    },
                )

                do {
                    val page = repository.loadPage(
                        datasetId = datasetId,
                        limit = EXPORT_PAGE_SIZE,
                        offset = offset,
                        sort = sort,
                        filter = filter,
                    )
                    page.rows.forEach { row ->
                        printer.printRecord(
                            buildList {
                                add(row.displayState())
                                columns.forEach { column ->
                                    add(row.values[column.id]?.rawValue.orEmpty())
                                    if (column.role == ColumnRole.CREDIT) {
                                        add(
                                            row.balanceMinor
                                                ?.let(FixedPoint::balanceDisplay)
                                                .orEmpty(),
                                        )
                                    }
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
            var columns = repository.observeColumns(datasetId).first()
            var addedColumns = 0
            val indexToColumnId = mutableMapOf<Int, Long>()

            header.forEachIndexed { index, name ->
                if (name.isBlank() || isSystemHeader(name)) return@forEachIndexed

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

    private fun isSystemHeader(name: String): Boolean =
        name.trim().lowercase() in SYSTEM_HEADERS

    companion object {
        private const val STATE_HEADER = "_AWi&k State"
        private const val BALANCE_HEADER = "Balance"
        private const val EXPORT_PAGE_SIZE = 500
        private val SYSTEM_HEADERS = setOf(
            "_awi&k state",
            "_auwire status",
            "state",
            "balance",
            "stock status",
        )
    }
}
