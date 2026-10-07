package com.auwire.iamkhata.feature.inventory

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.FixedPoint
import com.auwire.iamkhata.core.model.StockSnapshot

private val SkuWidth = 120.dp
private val NameWidth = 180.dp
private val UnitWidth = 90.dp
private val QuantityWidth = 140.dp
private val RateWidth = 150.dp

@Composable
internal fun InventoryStockTable(
    stock: List<StockSnapshot>,
    modifier: Modifier = Modifier,
) {
    val tableWidth =
        SkuWidth + NameWidth + UnitWidth +
            (QuantityWidth * 6) + (RateWidth * 2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .width(tableWidth)
                .fillMaxHeight(),
        ) {
            Row(Modifier.fillMaxWidth()) {
                HeaderCell("SKU", SkuWidth)
                HeaderCell("Name", NameWidth)
                HeaderCell("Unit", UnitWidth)
                HeaderCell("On hand", QuantityWidth)
                HeaderCell("Reserved", QuantityWidth)
                HeaderCell("Incoming", QuantityWidth)
                HeaderCell("Available", QuantityWidth)
                HeaderCell("Projected", QuantityWidth)
                HeaderCell("Reorder", QuantityWidth)
                HeaderCell("Sale rate", RateWidth)
                HeaderCell("Purchase rate", RateWidth)
            }
            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(stock, key = { it.product.id }) { snapshot ->
                    Row(Modifier.fillMaxWidth()) {
                        BodyCell(snapshot.product.sku, SkuWidth)
                        BodyCell(snapshot.product.name, NameWidth)
                        BodyCell(snapshot.product.unit, UnitWidth)
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.onHandMicros),
                            QuantityWidth,
                        )
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.reservedOutgoingMicros),
                            QuantityWidth,
                        )
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.tentativeIncomingMicros),
                            QuantityWidth,
                        )
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.availableToPromiseMicros),
                            QuantityWidth,
                        )
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.projectedMicros),
                            QuantityWidth,
                            emphasized = snapshot.belowReorderLevel,
                        )
                        BodyCell(
                            FixedPoint.quantityDecimal(snapshot.product.reorderLevelMicros),
                            QuantityWidth,
                        )
                        BodyCell(
                            snapshot.product.defaultSaleRateMinor
                                ?.let(FixedPoint::moneyDisplay)
                                ?: "-",
                            RateWidth,
                        )
                        BodyCell(
                            snapshot.product.defaultPurchaseRateMinor
                                ?.let(FixedPoint::moneyDisplay)
                                ?: "-",
                            RateWidth,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        color = if (emphasized) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
