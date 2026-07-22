package com.hisabak.feature.dashboard.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.ui.components.compactAmount
import com.hisabak.ui.components.rememberIsArabic
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider

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
        fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
        areaFill = LineCartesianLayer.AreaFill.single(
            fill(ShaderProvider.verticalGradient(fillColor.toArgb(), Color.Transparent.toArgb())),
        ),
    )
    val limitLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(overlayColor)),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 10.sp,
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
                color = MaterialTheme.colorScheme.onSurface,
                textSize = 11.sp,
            ),
            guideline = rememberLineComponent(
                fill = fill(MaterialTheme.colorScheme.outlineVariant),
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
        LineComponent(fill = Fill(barColor.toArgb()), thicknessDp = 4f)
    }

    val labelStep = if (xLabels.size <= 1) 1 else maxOf(1, (xLabels.size - 1) / 4)
    val bottomAxis = if (xLabels.isNotEmpty()) {
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 10.sp,
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
            bottomAxis = bottomAxis,
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
        LineComponent(fill = Fill(incomeColor.toArgb()), thicknessDp = 5f)
    }
    val expenseCol = remember(expenseColor) {
        LineComponent(fill = Fill(expenseColor.toArgb()), thicknessDp = 5f)
    }

    val labelStep = if (xLabels.size <= 1) 1 else maxOf(1, (xLabels.size - 1) / 4)
    val bottomAxis = if (xLabels.isNotEmpty()) {
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 10.sp,
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
            bottomAxis = bottomAxis,
        ),
        modelProducer = producer,
        modifier = modifier.height(heightDp),
    )
}
