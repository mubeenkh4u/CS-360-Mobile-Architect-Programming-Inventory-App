package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.SortDirection

internal val TableCellWidth = 150.dp

/** Horizontally scrollable grid backed by a vertically lazy row list. */
@Composable
internal fun DataGrid(
    columns: List<DatasetColumn>,
    rows: List<DataRow>,
    sortColumnId: Long?,
    sortDirection: SortDirection?,
    onSort: (DatasetColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tableWidth = TableCellWidth * columns.size.coerceAtLeast(1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        LazyColumn(Modifier.width(tableWidth).fillMaxHeight()) {
            item(key = "header") {
                Row {
                    columns.forEach { column ->
                        val suffix = when {
                            sortColumnId != column.id -> ""
                            sortDirection == SortDirection.ASC -> " ↑"
                            else -> " ↓"
                        }
                        TextButton(
                            onClick = { onSort(column) },
                            modifier = Modifier.width(TableCellWidth),
                        ) {
                            Text(
                                column.displayName + suffix,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            items(rows, key = DataRow::id) { row ->
                Row {
                    columns.forEach { column ->
                        Text(
                            row.values[column.id]?.rawValue.orEmpty(),
                            modifier = Modifier
                                .width(TableCellWidth)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Paging controls keep the UI bounded even when a dataset has many rows. */
@Composable
internal fun PaginationBar(
    offset: Int,
    pageSize: Int,
    loadedRows: Int,
    totalRows: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onPrevious, enabled = offset > 0) { Text("Previous") }

        val label = if (totalRows == 0L) {
            "No rows"
        } else {
            val start = offset + 1
            val end = (offset + loadedRows).toLong().coerceAtMost(totalRows)
            "$start–$end"
        }
        Text(label, Modifier.padding(top = 10.dp))

        OutlinedButton(onClick = onNext, enabled = offset + pageSize < totalRows) { Text("Next") }
    }
}
