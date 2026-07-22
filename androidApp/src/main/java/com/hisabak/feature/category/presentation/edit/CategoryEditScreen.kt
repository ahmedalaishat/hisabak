package com.hisabak.feature.category.presentation.edit

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.R
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.category.presentation.CategoryStyle
import com.hisabak.ui.components.BadgeTone
import com.hisabak.ui.components.DirhamGlyph
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.SegmentOption
import com.hisabak.ui.components.SegmentedControl
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    state: CategoryEditUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onColorChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onLimitChange: (String) -> Unit,
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
            .padding(Spacing.pageMargin)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
            // Name field
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.common_name)) },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Type picker
            FormSection(label = stringResource(R.string.category_type_label)) {
                SegmentedControl(
                    options = listOf(
                        SegmentOption(CategoryType.INCOME, stringResource(R.string.category_type_income), BadgeTone.Income),
                        SegmentOption(CategoryType.EXPENSES, stringResource(R.string.category_type_expenses), BadgeTone.Expense),
                        SegmentOption(CategoryType.SAVINGS, stringResource(R.string.category_type_savings), BadgeTone.Savings),
                        SegmentOption(CategoryType.INVESTMENT, stringResource(R.string.category_type_invest_short), BadgeTone.Investment),
                    ),
                    selected = state.type,
                    onSelect = onTypeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Monthly limit (expense categories only)
            if (state.showLimit) {
                OutlinedTextField(
                    value = state.limitInput,
                    onValueChange = onLimitChange,
                    label = { Text(stringResource(R.string.category_limit_label)) },
                    prefix = { DirhamGlyph(size = 16.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    isError = state.limitError != null,
                    supportingText = {
                        Text(state.limitError ?: stringResource(R.string.category_limit_hint))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Color picker
            FormSection(label = stringResource(R.string.category_color_label)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(CategoryStyle.palette) { key ->
                        ColorSwatch(
                            colorKey = key,
                            selected = key == state.color,
                            onSelect = { onColorChange(key) },
                        )
                    }
                }
            }

            // Icon picker
            FormSection(label = stringResource(R.string.category_icon_label)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(CategoryStyle.icons) { key ->
                        IconChip(
                            iconKey = key,
                            colorKey = state.color,
                            selected = key == state.icon,
                            onSelect = { onIconChange(key) },
                        )
                    }
                }
            }

            // Live preview
            FormSection(label = stringResource(R.string.category_preview_label)) {
                LivePreviewTile(
                    name = state.nameInput.ifBlank { stringResource(R.string.category_name_placeholder) },
                    iconKey = state.icon,
                    colorKey = state.color,
                )
            }

            // General error
            state.generalError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Actions
            HisabakButton(
                text = stringResource(if (state.isSaving) R.string.action_saving else R.string.action_save),
                onClick = onSave,
                enabled = state.canSave,
                variant = ButtonVariant.Primary,
                fullWidth = true,
            )

            Spacer(Modifier.height(Spacing.s3))
        }
}

@Composable
private fun FormSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ColorSwatch(
    colorKey: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val swatchColor = CategoryStyle.color(colorKey)
    val swatchShape = MaterialTheme.shapes.small
    Box(
        modifier = Modifier
            .size(Sizing.avatar)
            .clip(swatchShape)
            .background(swatchColor, swatchShape)
            .then(
                if (selected)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, swatchShape)
                else
                    Modifier
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                HugeIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun IconChip(
    iconKey: String,
    colorKey: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val (bg, fg) = tintPairForColor(colorKey)
    val shape = MaterialTheme.shapes.medium
    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconForKey(iconKey),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LivePreviewTile(
    name: String,
    iconKey: String,
    colorKey: String,
) {
    val (bg, fg) = tintPairForColor(colorKey)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            IconTile(
                icon = iconForKey(iconKey),
                size = Sizing.tileSize,
                iconSize = 22.dp,
                background = bg,
                foreground = fg,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
