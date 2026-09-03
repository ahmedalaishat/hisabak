package com.hisabak.feature.dashboard.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.ui.components.compactAmount
import com.hisabak.ui.components.rememberIsArabic
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.Axis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.data.lineSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.multiplatform.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent

@Composable
fun AreaLineChart(
    values: List<Double>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    heightDp: Dp = 120.dp,
    xLabels: List<String> = emptyList(),
    overlayValues: List<Double?> = emptyList(),
    overlayColor: Color = lineColor,
) {
    // The overlay (e.g. a limit line) is plotted only at the points that have a value, using
    // explicit x positions, so it begins partway in instead of inventing zeros for empty months.
    val overlayXs = overlayValues.indices.filter { overlayValues[it] != null }
    val overlayYs = overlayXs.map { overlayValues[it]!! }
    val hasOverlay = overlayYs.isNotEmpty()

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, overlayXs, overlayYs) {
        if (values.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineSeries {
                series(values)
                if (hasOverlay) series(overlayXs, overlayYs)
            }
        }
    }
    if (values.isEmpty()) return
    // Area fill fades vertically — [fillColor] just under the line down to transparent at the
    // baseline — for a soft gradient rather than a flat tinted block.
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(Brush.verticalGradient(listOf(fillColor, Color.Transparent))),
        ),
    )
    val limitLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(overlayColor)),
    )
    val lineProvider = if (hasOverlay) {
        LineCartesianLayer.LineProvider.series(line, limitLine)
    } else {
        LineCartesianLayer.LineProvider.series(line)
    }

    val hasLabels = xLabels.isNotEmpty()
    // Thin the axis labels to ~5 so daily series don't overlap; the marker gives the exact value.
    val labelStep = if (xLabels.size <= 1) 1 else maxOf(1, (xLabels.size - 1) / 4)
    val bottomAxis = if (hasLabels) {
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                ),
            ),
            line = null,
            tick = null,
            guideline = null,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelStep }),
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                xLabels.getOrNull(value.toInt()).orEmpty().ifEmpty { " " }
            },
        )
    } else null

    val arabic = rememberIsArabic()
    val marker = if (hasLabels) {
        rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                ),
            ),
            guideline = rememberLineComponent(
                fill = Fill(MaterialTheme.colorScheme.outlineVariant),
                thickness = 1.dp,
            ),
            valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
                val target = targets.firstOrNull()
                val i = target?.x?.toInt() ?: 0
                val date = xLabels.getOrNull(i).orEmpty()
                val amount = (target as? LineCartesianLayerMarkerTarget)
                    ?.points?.firstOrNull()?.entry?.y
                if (amount != null) "$date   ${compactAmount(amount, arabic)}" else date
            },
        )
    } else null

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = lineProvider,
            ),
            bottomAxis = bottomAxis,
            marker = marker,
        ),
        modelProducer = producer,
        modifier = modifier.height(heightDp),
    )
}

/**
 * A marker for the column charts. The bottom axis is thinned to ~5 labels so bars stay readable, so
 * without this a bar has a date at best and never its amount — which is the number the user came
 * for. Multi-series charts show every column at that x, in series order.
 */
/**
 * The value scale on the leading edge. [count] is the number of gridlines: three on a full-height
 * chart, two on the 64dp sparkline, where a third label would sit almost on top of its neighbours.
 * Labels are bare compact figures — an axis label is plain text, so it cannot carry the dirham
 * glyph, which is the convention for charts anyway. The marker still gives a bar's exact amount.
 */
@Composable
private fun rememberAmountAxis(arabic: Boolean, count: Int): VerticalAxis<Axis.Position.Vertical.Start> =
    VerticalAxis.rememberStart(
        label = rememberAxisLabelComponent(
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            ),
        ),
        line = null,
        tick = null,
        guideline = rememberLineComponent(
            fill = Fill(MaterialTheme.colorScheme.outlineVariant),
            thickness = 1.dp,
        ),
        itemPlacer = VerticalAxis.ItemPlacer.count({ count }, shiftTopLines = false),
        valueFormatter = CartesianValueFormatter { _, value, _ -> compactAmount(value, arabic) },
    )

@Composable
private fun rememberColumnMarker(xLabels: List<String>, arabic: Boolean): CartesianMarker? {
    if (xLabels.isEmpty()) return null
    return rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
            ),
        ),
        guideline = rememberLineComponent(
            fill = Fill(MaterialTheme.colorScheme.outlineVariant),
            thickness = 1.dp,
        ),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull()
            val date = xLabels.getOrNull(target?.x?.toInt() ?: 0).orEmpty()
            val amounts = (target as? ColumnCartesianLayerMarkerTarget)
                ?.columns
                ?.joinToString("   ") { compactAmount(it.entry.y, arabic) }
                .orEmpty()
            if (amounts.isEmpty()) date else "$date   $amounts"
        },
    )
}

@Composable
fun BarSparkline(
    values: List<Double>,
    barColor: Color,
    modifier: Modifier = Modifier,
    heightDp: Dp = 64.dp,
    xLabels: List<String> = emptyList(),
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        if (values.isEmpty()) return@LaunchedEffect
        producer.runTransaction { columnSeries { series(values) } }
    }
    if (values.isEmpty()) return
    val column = remember(barColor) {
        LineComponent(fill = Fill(barColor), thickness = 4.dp)
    }

    val arabic = rememberIsArabic()
    val labelStep = if (xLabels.size <= 1) 1 else maxOf(1, (xLabels.size - 1) / 4)
    val bottomAxis = if (xLabels.isNotEmpty()) {
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                ),
            ),
            line = null,
            tick = null,
            guideline = null,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelStep }),
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                xLabels.getOrNull(value.toInt()).orEmpty().ifEmpty { " " }
            },
        )
    } else null

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
            ),
            startAxis = rememberAmountAxis(arabic, count = 2),
            bottomAxis = bottomAxis,
            marker = rememberColumnMarker(xLabels, arabic),
        ),
        modelProducer = producer,
        modifier = modifier.height(heightDp),
    )
}

/** Side-by-side income + expense bars per time bucket. */
@Composable
fun GroupedBarChart(
    incomeValues: List<Double>,
    expenseValues: List<Double>,
    incomeColor: Color,
    expenseColor: Color,
    modifier: Modifier = Modifier,
    heightDp: Dp = 140.dp,
    xLabels: List<String> = emptyList(),
) {
    if (incomeValues.isEmpty() || expenseValues.isEmpty()) return
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(incomeValues, expenseValues) {
        producer.runTransaction {
            columnSeries {
                series(incomeValues)
                series(expenseValues)
            }
        }
    }
    val incomeCol = remember(incomeColor) {
        LineComponent(fill = Fill(incomeColor), thickness = 5.dp)
    }
    val expenseCol = remember(expenseColor) {
        LineComponent(fill = Fill(expenseColor), thickness = 5.dp)
    }

    val arabic = rememberIsArabic()
    val labelStep = if (xLabels.size <= 1) 1 else maxOf(1, (xLabels.size - 1) / 4)
    val bottomAxis = if (xLabels.isNotEmpty()) {
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                ),
            ),
            line = null,
            tick = null,
            guideline = null,
            itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelStep }),
            valueFormatter = CartesianValueFormatter { _, value, _ ->
                xLabels.getOrNull(value.toInt()).orEmpty().ifEmpty { " " }
            },
        )
    } else null

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(incomeCol, expenseCol),
            ),
            startAxis = rememberAmountAxis(arabic, count = 3),
            bottomAxis = bottomAxis,
            // Both series at that bucket, income first — the legend above says which is which.
            marker = rememberColumnMarker(xLabels, arabic),
        ),
        modelProducer = producer,
        modifier = modifier.height(heightDp),
    )
}
