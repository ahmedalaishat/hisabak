package com.hisabak.feature.brand.presentation.edit

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.hisabak.shared.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.hisabak.feature.brand.domain.ai.CategorySuggestion
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.ui.components.ButtonVariant
import com.hisabak.ui.components.ChipLaneGrid
import com.hisabak.ui.components.ColoredFilterChip
import com.hisabak.ui.components.HisabakButton
import com.hisabak.ui.components.LeadingIconChip
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.pluralStringResource
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.components.localizedFormatArg
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.hisabak.feature.brand.domain.BrandId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandEditScreen(
    state: BrandEditUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (CategoryId?) -> Unit,
    onCreateCategory: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onMergeInto: (BrandId) -> Unit,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

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
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s6)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
            NameField(
                value = state.nameInput,
                error = state.nameError,
                onValueChange = onNameChange,
            )

            CategorySection(
                options = state.categoryOptions,
                selected = state.selectedCategoryId,
                onSelect = onCategoryChange,
                onCreateNew = onCreateCategory,
            )

            if (state.selectedCategoryId == null) {
                SuggestionRow(
                    isSuggesting = state.isSuggesting,
                    suggestion = state.suggestion,
                    onAccept = onAcceptSuggestion,
                )
            }

            if (state.generalError != null) {
                Text(
                    text = state.generalError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.s2))

            HisabakButton(
                text = stringResource(if (state.isSaving) Res.string.action_saving else Res.string.action_save),
                onClick = onSave,
                variant = ButtonVariant.Primary,
                enabled = state.canSave,
                fullWidth = true,
            )

            // Deleting moved off the list row: a brand with transactions can't simply vanish, and
            // this is the screen with room to say so and offer somewhere to move them.
            if (!state.isNew) {
                HisabakButton(
                    text = stringResource(Res.string.action_delete),
                    onClick = { confirmingDelete = true },
                    variant = ButtonVariant.Danger,
                    enabled = !state.isDeleting && !state.isSaving,
                    fullWidth = true,
                )
            }
        }

    if (confirmingDelete) {
        BrandDeleteDialog(
            name = state.nameInput,
            transactionCount = state.transactionCount,
            otherBrands = state.otherBrands,
            onDismiss = { confirmingDelete = false },
            onConfirmDelete = {
                confirmingDelete = false
                onDelete()
            },
            onConfirmMerge = { target ->
                confirmingDelete = false
                onMergeInto(target)
            },
        )
    }
}

@Composable
private fun NameField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(Res.string.brand_name_label)) },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.s2),
            )
        }
    }
}

/** The AI suggestion line: a quiet progress hint while the model runs, then a tappable chip. */
@Composable
private fun SuggestionRow(
    isSuggesting: Boolean,
    suggestion: CategorySuggestion?,
    onAccept: () -> Unit,
) {
    when {
        isSuggesting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.brand_ai_suggesting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        suggestion is CategorySuggestion.Existing -> ColoredFilterChip(
            label = stringResource(Res.string.brand_ai_suggested, suggestion.category.name),
            colorKey = suggestion.category.color,
            selected = false,
            onClick = onAccept,
        )
        suggestion is CategorySuggestion.New -> ColoredFilterChip(
            label = stringResource(Res.string.brand_ai_suggested_new, suggestion.name),
            colorKey = suggestion.color,
            selected = false,
            onClick = onAccept,
        )
    }
}

@Composable
private fun CategorySection(
    options: List<BrandEditUiState.CategoryOption>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
    onCreateNew: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(Res.string.common_category),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // "None" and "New category" are chips too, so they count toward the lane split.
        ChipLaneGrid(chipCount = options.size + 2) {
            item {
                ColoredFilterChip(
                    label = stringResource(Res.string.common_none),
                    colorKey = null,
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
            }
            items(options) { option ->
                ColoredFilterChip(
                    label = option.name,
                    colorKey = option.color,
                    selected = selected == option.id,
                    onClick = { onSelect(option.id) },
                    iconKey = option.icon,
                )
            }
            item {
                LeadingIconChip(
                    label = stringResource(Res.string.category_new_title),
                    leadingIcon = HugeIcons.Add,
                    selected = false,
                    onClick = onCreateNew,
                )
            }
        }
    }
}


/**
 * Deleting a brand that has transactions would orphan them, so the only way out is to move them
 * onto another brand first. With no transactions it's a plain confirm.
 */
@Composable
private fun BrandDeleteDialog(
    name: String,
    transactionCount: Int,
    otherBrands: List<BrandEditUiState.BrandOption>,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmMerge: (BrandId) -> Unit,
) {
    if (transactionCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.common_delete_title, name)) },
            text = { Text(stringResource(Res.string.brand_delete_empty_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
        return
    }

    var target by remember { mutableStateOf<BrandEditUiState.BrandOption?>(null) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.common_delete_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(
                    pluralStringResource(
                        Res.plurals.brand_delete_move_body,
                        transactionCount,
                        localizedFormatArg(transactionCount),
                    ),
                )
                if (otherBrands.isEmpty()) {
                    Text(
                        stringResource(Res.string.brand_delete_no_target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { expanded = true }
                                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                        ) {
                            Text(
                                target?.name ?: stringResource(Res.string.brand_delete_choose),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (target == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                HugeIcons.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Sizing.iconSm),
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            otherBrands.forEach { brand ->
                                DropdownMenuItem(
                                    text = { Text(brand.name, maxLines = 1) },
                                    onClick = { target = brand; expanded = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onConfirmMerge(it.id) } },
                enabled = target != null,
            ) {
                Text(stringResource(Res.string.brand_delete_and_move), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
