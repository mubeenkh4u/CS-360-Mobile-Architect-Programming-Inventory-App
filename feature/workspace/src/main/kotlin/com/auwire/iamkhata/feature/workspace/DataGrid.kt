package com.auwire.iamkhata.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ColumnRole
import com.auwire.iamkhata.core.model.DataRow
import com.auwire.iamkhata.core.model.DatasetColumn
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.SortDirection
import com.auwire.iamkhata.core.model.displayState

internal val TableCellWidth = 150.dp
private val RowStateWidth = 112.dp
private val BalanceCellWidth = 170.dp

@Composable
internal fun DataGrid(
    columns: List<DatasetColumn>,
    rows: List<DataRow>,
    selectedRowId: Long?,
    sortColumnId: Long?,
    sortDirection: SortDirection?,
    onSelectRow: (Long?) -> Unit,
    onSort: (DatasetColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showBalance = columns.any { it.role == ColumnRole.DEBIT } &&
        columns.any { it.role == ColumnRole.CREDIT }
    val derivedWidth = if (showBalance) BalanceCellWidth else 0.dp
    val tableWidth = RowStateWidth +
        (TableCellWidth * columns.size.coerceAtLeast(1)) +
        derivedWidth

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        // Keep the header outside the vertically scrolling list. The entire
        // table remains inside one horizontal scroll container so header cells
        // and row cells always move together left/right.
        Column(
            modifier = Modifier
                .width(tableWidth)
                .fillMaxHeight(),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "State",
                    modifier = Modifier
                        .width(RowStateWidth)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                )
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
                    if (showBalance && column.role == ColumnRole.CREDIT) {
                        Text(
                            "Balance",
                            modifier = Modifier
                                .width(BalanceCellWidth)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(rows, key = DataRow::id) { row ->
                val selected = row.id == selectedRowId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable {
                            onSelectRow(if (selected) null else row.id)
                        },
                ) {
                    Text(
                        text = row.displayState(),
                        modifier = Modifier
                            .width(RowStateWidth)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    columns.forEach { column ->
                        Text(
                            row.values[column.id]?.rawValue.orEmpty(),
                            modifier = Modifier
                                .width(TableCellWidth)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showBalance && column.role == ColumnRole.CREDIT) {
                            Text(
                                row.balanceMinor?.let(FixedPoint::balanceDisplay) ?: "-",
                                modifier = Modifier
                                    .width(BalanceCellWidth)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun PaginationBar(
    offset: Int,
    pageSize: Int,
    loadedRows: Int,
    totalRows: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onPrevious, enabled = offset > 0) {
            Text("Previous")
        }

        val label = if (totalRows == 0L) {
            "No rows"
        } else {
            val start = offset + 1
            val end = (offset + loadedRows).toLong().coerceAtMost(totalRows)
            "$start-$end"
        }
        Text(label, Modifier.padding(top = 10.dp))

        OutlinedButton(
            onClick = onNext,
            enabled = offset + pageSize < totalRows,
        ) {
            Text("Next")
        }
    }
}
