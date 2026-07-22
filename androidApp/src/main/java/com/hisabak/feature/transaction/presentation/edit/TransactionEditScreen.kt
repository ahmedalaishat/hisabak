package com.hisabak.feature.transaction.presentation.edit

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.R
import com.hisabak.core.common.sanitizeAmountInput
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.ui.components.BadgeTone
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.ColoredFilterChip
import com.hisabak.ui.components.DirhamGlyph
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.SegmentOption
import com.hisabak.ui.components.SegmentedControl
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    state: TransactionEditUiState,
    onAmountChange: (String) -> Unit,
    onBrandSelected: (BrandId) -> Unit,
    onNoteChange: (String) -> Unit,
    onTypeSelected: (CategoryType) -> Unit,
    onDateClick: () -> Unit,
    onDateSelected: (Instant) -> Unit,
    onDateDismiss: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state.isLoading) {
        Box(
            Modifier.fillMaxWidth().padding(Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.pageMargin)
            .padding(bottom = Spacing.s5)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        Text(
            text = stringResource(if (state.isNew) R.string.transaction_new_title else R.string.transaction_edit_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        AmountHeroField(
            amountInput = state.amountInput,
            error = state.amountError,
            type = state.selectedType,
            onAmountChange = onAmountChange,
        )

        SegmentedControl(
            options = listOf(
                SegmentOption(CategoryType.EXPENSES,   stringResource(R.string.category_type_expense),       BadgeTone.Expense),
                SegmentOption(CategoryType.INCOME,     stringResource(R.string.category_type_income),         BadgeTone.Income),
                SegmentOption(CategoryType.SAVINGS,    stringResource(R.string.category_type_savings),        BadgeTone.Savings),
                SegmentOption(CategoryType.INVESTMENT, stringResource(R.string.category_type_invest_short),   BadgeTone.Investment),
            ),
            selected = state.selectedType,
            onSelect = onTypeSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
            Text(
                text = stringResource(R.string.common_brand),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.brandOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.transaction_no_brands),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(state.brandOptions, key = { it.id.value }) { option ->
                        ColoredFilterChip(
                            label = option.name,
                            colorKey = option.categoryColor,
                            selected = option.id == state.selectedBrandId,
                            onClick = { onBrandSelected(option.id) },
                        )
                    }
                }
            }
            state.brandError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sectionTitleGap)) {
            Text(
                text = stringResource(R.string.common_date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HisabakButton(
                text = formatDate(state.occurredAt),
                onClick = onDateClick,
                variant = ButtonVariant.Secondary,
                leadingIcon = HugeIcons.CalendarToday,
                fullWidth = true,
            )
        }

        OutlinedTextField(
            value = state.noteInput,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.transaction_note_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        state.generalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        HisabakButton(
            text = stringResource(if (state.isSaving) R.string.action_saving else R.string.action_save),
            onClick = onSave,
            variant = ButtonVariant.Primary,
            enabled = state.canSave,
            fullWidth = true,
        )
        HisabakButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            variant = ButtonVariant.Ghost,
            fullWidth = true,
        )
    }

    if (state.showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.occurredAt.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDateDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(Instant.ofEpochMilli(it)) }
                        ?: onDateDismiss()
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = onDateDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Large editable amount — the dirham glyph + an inline borderless field, tinted by type. */
@Composable
private fun AmountHeroField(
    amountInput: String,
    error: String?,
    type: CategoryType,
    onAmountChange: (String) -> Unit,
) {
    val c = HisabakTheme.colors
    val color = when (type) {
        CategoryType.INCOME     -> c.income
        CategoryType.EXPENSES   -> c.expense
        CategoryType.SAVINGS    -> c.savings
        CategoryType.INVESTMENT -> c.investment
    }
    val heroStyle = HisabakType.amount.copy(
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val tapSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Center the glyph + field as one tight cluster. The field wraps to its
        // content width (IntrinsicSize.Min) so it doesn't eat the row and push
        // the glyph to the edge. Tapping anywhere in the (full-width, padded) row
        // focuses the field and opens the keyboard — a generous target for a hero
        // input. No ripple: it reads as a field, not a button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = tapSource, indication = null) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                .padding(vertical = Spacing.s2),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DirhamGlyph(size = 44.sp * 0.82f, tint = color)
            Spacer(Modifier.width(Spacing.s2))
            Box(contentAlignment = Alignment.CenterStart) {
                if (amountInput.isEmpty()) {
                    Text("0.00", style = heroStyle.copy(color = c.textTertiary))
                }
                BasicTextField(
                    value = amountInput,
                    onValueChange = { onAmountChange(sanitizeAmountInput(it)) },
                    textStyle = heroStyle,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = GroupedAmountTransformation,
                    cursorBrush = SolidColor(color),
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .focusRequester(focusRequester),
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(Spacing.s2))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Display-only thousands grouping for the raw amount string (digits + at most one `.`): the value
 * passed to `onValueChange` stays comma-free, so [sanitizeAmountInput] and the parser are unaffected.
 * Only the integer part is grouped; the fraction (and any trailing `.`) is left as typed.
 */
private val GroupedAmountTransformation = VisualTransformation { text ->
    val original = text.text
    val dot = original.indexOf('.')
    val intPart = if (dot >= 0) original.substring(0, dot) else original
    val rest = if (dot >= 0) original.substring(dot) else ""

    val grouped = StringBuilder()
    // origToTransformed[i] = transformed offset where original integer digit i begins (after any
    // comma inserted before it). Lets the cursor land naturally on the digit side of a separator.
    val origToTransformed = IntArray(intPart.length + 1)
    val n = intPart.length
    for (i in 0 until n) {
        if (i > 0 && (n - i) % 3 == 0) grouped.append(',')
        origToTransformed[i] = grouped.length
        grouped.append(intPart[i])
    }
    origToTransformed[n] = grouped.length

    val transformedText = grouped.toString() + rest
    val intCommas = grouped.length - n

    TransformedText(
        AnnotatedString(transformedText),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, original.length)
                return if (clamped <= n) origToTransformed[clamped] else clamped + intCommas
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transformedText.length)
                val commasBefore = transformedText.substring(0, clamped).count { it == ',' }
                return clamped - commasBefore
            }
        },
    )
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

private fun formatDate(instant: Instant): String =
    dateFormatter.format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
