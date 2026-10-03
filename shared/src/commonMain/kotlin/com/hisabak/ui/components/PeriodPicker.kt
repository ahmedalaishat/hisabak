package com.hisabak.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.date_last_30
import com.hisabak.shared.resources.date_last_7
import com.hisabak.shared.resources.date_last_90
import com.hisabak.shared.resources.period_all_hint
import com.hisabak.shared.resources.period_all_time
import com.hisabak.shared.resources.period_choose
import com.hisabak.shared.resources.period_compared_with
import com.hisabak.shared.resources.period_last_12_months
import com.hisabak.shared.resources.period_last_month
import com.hisabak.shared.resources.period_mode_all
import com.hisabak.shared.resources.period_mode_custom
import com.hisabak.shared.resources.period_mode_month
import com.hisabak.shared.resources.period_mode_year
import com.hisabak.shared.resources.period_next
import com.hisabak.shared.resources.period_next_year
import com.hisabak.shared.resources.period_previous
import com.hisabak.shared.resources.period_previous_year
import com.hisabak.shared.resources.period_quick_picks
import com.hisabak.shared.resources.period_show
import com.hisabak.shared.resources.period_this_month
import com.hisabak.shared.resources.period_this_year
import com.hisabak.shared.resources.period_title
import com.hisabak.ui.format.LocalDateFormatter
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The period's name as the bar shows it — "September 2026", "2026", "6 Jul – 3 Oct", "All time". */
@Composable
fun periodLabel(period: SummaryPeriod): String {
    val formatter = LocalDateFormatter.current
    val arabic = rememberIsArabic()
    // The platform formatters print Western digits even in Arabic, so the digits are localized here.
    return when (period) {
        is SummaryPeriod.Month -> localizeDigits(formatter.monthYearLong(period.month.firstDay), arabic)
        is SummaryPeriod.Year -> localizeDigits(period.year.toString(), arabic)
        // A range inside one year reads as day + month; across years, the dates need their years.
        is SummaryPeriod.Custom -> localizeDigits(
            if (period.start.year == period.endInclusive.year) {
                "${formatter.dayMonth(period.start)} – ${formatter.dayMonth(period.endInclusive)}"
            } else {
                "${formatter.fullDate(period.start)} – ${formatter.fullDate(period.endInclusive)}"
            },
            arabic,
        )
        SummaryPeriod.All -> stringResource(Res.string.period_all_time)
    }
}

/**
 * ‹ September 2026 › — the period control on every date-scoped screen. The arrows step to the
 * equal-length window before or after; the title opens [PeriodSheet] for any month, year, custom
 * range, or all time. The line under the title says what the trend percentages compare against,
 * which a bare "+12%" never did. All time has no neighbours, so it trades the arrows for its span.
 */
@Composable
fun PeriodBar(
    period: SummaryPeriod,
    today: LocalDate,
    earliest: LocalDate?,
    onSelect: (SummaryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val formatter = LocalDateFormatter.current
    val arabic = rememberIsArabic()
    val subtitle = when (period) {
        SummaryPeriod.All -> earliest?.let {
            localizeDigits("${formatter.monthYearLong(it)} – ${formatter.monthYearLong(today)}", arabic)
        }
        else -> period.previous?.let { stringResource(Res.string.period_compared_with, periodLabel(it)) }
    }
    val chooseLabel = stringResource(Res.string.period_choose)

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (period != SummaryPeriod.All) {
            StepButton(
                forward = false,
                enabled = period.canStepBack(earliest),
                label = stringResource(Res.string.period_previous),
                onClick = { period.previous?.let(onSelect) },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = chooseLabel) { sheetOpen = true }
                .padding(vertical = Spacing.s1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    periodLabel(period),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Icon(
                    HugeIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (period != SummaryPeriod.All) {
            StepButton(
                forward = true,
                enabled = period.canStepForward(today),
                label = stringResource(Res.string.period_next),
                onClick = { period.next?.let(onSelect) },
            )
        }
    }

    if (sheetOpen) {
        PeriodSheet(
            period = period,
            today = today,
            earliest = earliest,
            onSelect = {
                sheetOpen = false
                onSelect(it)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun StepButton(forward: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.4f)),
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
    ) {
        // One chevron, mirrored for "back": it already flips with the layout direction, so ‹ and ›
        // keep pointing towards the past and the future in Arabic too.
        Icon(
            HugeIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconSm).graphicsLayer { if (!forward) scaleX = -1f },
        )
    }
}

private enum class PeriodMode(val labelRes: StringResource) {
    MONTH(Res.string.period_mode_month),
    YEAR(Res.string.period_mode_year),
    CUSTOM(Res.string.period_mode_custom),
    ALL(Res.string.period_mode_all),
}

private fun SummaryPeriod.mode(): PeriodMode = when (this) {
    is SummaryPeriod.Month -> PeriodMode.MONTH
    is SummaryPeriod.Year -> PeriodMode.YEAR
    is SummaryPeriod.Custom -> PeriodMode.CUSTOM
    SummaryPeriod.All -> PeriodMode.ALL
}

/** Month / Year / Custom / All. A tap on a month, a year, a quick pick, or All applies at once; only a custom range, which takes two taps to say, has a confirm button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(
    period: SummaryPeriod,
    today: LocalDate,
    earliest: LocalDate?,
    onSelect: (SummaryPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(period.mode()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin)
                .padding(bottom = Spacing.s6),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(
                stringResource(Res.string.period_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PeriodMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            if (option == PeriodMode.ALL) onSelect(SummaryPeriod.All) else mode = option
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, PeriodMode.entries.size),
                        icon = {},
                    ) {
                        Text(stringResource(option.labelRes), maxLines = 1)
                    }
                }
            }
            when (mode) {
                PeriodMode.MONTH -> MonthPane(period, today, earliest, onSelect)
                PeriodMode.YEAR -> YearPane(period, today, earliest, onSelect)
                PeriodMode.CUSTOM -> CustomPane(period, today, onSelect)
                PeriodMode.ALL -> Text(
                    stringResource(Res.string.period_all_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthPane(
    period: SummaryPeriod,
    today: LocalDate,
    earliest: LocalDate?,
    onSelect: (SummaryPeriod) -> Unit,
) {
    val formatter = LocalDateFormatter.current
    val firstYear = minOf(earliest?.year ?: today.year, today.year)
    var year by rememberSaveable {
        mutableIntStateOf((period as? SummaryPeriod.Month)?.month?.year ?: today.year)
    }
    YearStepper(
        year = year,
        canGoBack = year > firstYear,
        canGoForward = year < today.year,
        onChange = { year = it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Month.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                row.forEach { m ->
                    val month = YearMonth(year, m)
                    PickCell(
                        label = formatter.month(month.firstDay),
                        selected = period == SummaryPeriod.Month(month),
                        enabled = month.firstDay <= today,
                        marked = month == today.yearMonth,
                        onClick = { onSelect(SummaryPeriod.Month(month)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    QuickPicks(period, today, onSelect)
}

@Composable
private fun YearPane(
    period: SummaryPeriod,
    today: LocalDate,
    earliest: LocalDate?,
    onSelect: (SummaryPeriod) -> Unit,
) {
    val arabic = rememberIsArabic()
    val years = (minOf(earliest?.year ?: today.year, today.year)..today.year).reversed().toList()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        years.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                row.forEach { y ->
                    PickCell(
                        label = localizeDigits(y.toString(), arabic),
                        selected = period == SummaryPeriod.Year(y),
                        enabled = true,
                        marked = y == today.year,
                        onClick = { onSelect(SummaryPeriod.Year(y)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPane(
    period: SummaryPeriod,
    today: LocalDate,
    onSelect: (SummaryPeriod) -> Unit,
) {
    val initial = period as? SummaryPeriod.Custom
    val todayMillis = today.toPickerMillis()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.endInclusive?.toPickerMillis(),
        yearRange = 2000..today.year,
        selectableDates = remember(todayMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            }
        },
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        listOf(
            Res.string.date_last_7 to SummaryPeriod.lastDays(today, 7),
            Res.string.date_last_30 to SummaryPeriod.lastDays(today, 30),
            Res.string.date_last_90 to SummaryPeriod.lastDays(today, 90),
        ).forEach { (label, preset) ->
            PickChip(stringResource(label), selected = period == preset, onClick = { onSelect(preset) })
        }
    }
    val colors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        selectedDayContainerColor = MaterialTheme.colorScheme.onSurface,
        selectedDayContentColor = MaterialTheme.colorScheme.surface,
        dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
        todayDateBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        todayContentColor = MaterialTheme.colorScheme.onSurface,
    )
    DateRangePicker(
        state = state,
        title = null,
        headline = null,
        showModeToggle = false,
        colors = colors,
        modifier = Modifier.fillMaxWidth().height(340.dp),
    )
    val start = state.selectedStartDateMillis?.toPickerDate()
    val end = state.selectedEndDateMillis?.toPickerDate()
    val chosen = if (start != null && end != null) SummaryPeriod.Custom(start, end) else null
    // The sheet's one primary action, so the one green surface on it.
    Button(
        onClick = { chosen?.let(onSelect) },
        enabled = chosen != null,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(
            stringResource(Res.string.period_show, chosen?.let { periodLabel(it) } ?: "…"),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun QuickPicks(period: SummaryPeriod, today: LocalDate, onSelect: (SummaryPeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(
            stringResource(Res.string.period_quick_picks),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // One scrolling row: the presets are a shortcut, not worth a second line of the sheet.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            listOf(
                Res.string.period_this_month to SummaryPeriod.thisMonth(today),
                Res.string.period_last_month to SummaryPeriod.lastMonth(today),
                Res.string.period_this_year to SummaryPeriod.thisYear(today),
                Res.string.period_last_12_months to SummaryPeriod.last12Months(today),
            ).forEach { (label, pick) ->
                PickChip(stringResource(label), selected = period == pick, onClick = { onSelect(pick) })
            }
        }
    }
}

@Composable
private fun YearStepper(year: Int, canGoBack: Boolean, canGoForward: Boolean, onChange: (Int) -> Unit) {
    val arabic = rememberIsArabic()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(year - 1) }, enabled = canGoBack) {
            Icon(
                HugeIcons.ChevronRight,
                contentDescription = stringResource(Res.string.period_previous_year),
                modifier = Modifier.size(Sizing.iconSm).graphicsLayer { scaleX = -1f },
            )
        }
        Text(
            localizeDigits(year.toString(), arabic),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(year + 1) }, enabled = canGoForward) {
            Icon(
                HugeIcons.ChevronRight,
                contentDescription = stringResource(Res.string.period_next_year),
                modifier = Modifier.size(Sizing.iconSm),
            )
        }
    }
}

/**
 * A month or year cell. Selection is neutral ink, not green — green is reserved for money-positive
 * values and the screen's one primary action. A dot marks the current month or year.
 */
@Composable
private fun PickCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(scheme.onSurface, shape)
                else Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.4f), shape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> scheme.surface
                enabled -> scheme.onSurface
                else -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            maxLines = 1,
        )
        if (marked && !selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(4.dp)
                    .background(scheme.onSurfaceVariant, CircleShape),
            )
        }
    }
}

/**
 * A one-tap preset. Filled rather than outlined: on the sheet's tinted surface a hairline outline
 * alone read as plain text, not as something to tap.
 */
@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(scheme.onSurface, CircleShape)
                } else {
                    Modifier
                        .background(scheme.surfaceContainerHighest, CircleShape)
                        .border(1.dp, scheme.outline.copy(alpha = 0.5f), CircleShape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.s4),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.surface else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** The date picker speaks UTC-midnight millis; a [LocalDate] is a calendar day with no zone. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
