package com.hisabak.feature.brand.presentation.list

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.hisabak.ui.components.SkeletonRowList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.R
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.ui.components.AmountText
import com.hisabak.ui.components.AmountTone
import com.hisabak.ui.components.CircleIconTile
import com.hisabak.ui.components.ColoredFilterChip
import com.hisabak.ui.components.CreateActionButton
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.ListRow
import com.hisabak.ui.components.SearchField
import com.hisabak.ui.components.SectionHeader
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

@Composable
fun BrandListScreen(
    state: BrandListUiState,
    onSearchChange: (String) -> Unit,
    onCategoryFilterChange: (CategoryId?) -> Unit,
    onDelete: (BrandId) -> Unit,
    onMerge: (BrandId, BrandId) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BrandId) -> Unit,
    showHeader: Boolean = true,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s5),
        ) {
            SkeletonRowList(count = 7)
        }
        return
    }

    var pendingDelete by remember { mutableStateOf<BrandRow?>(null) }

    val allLabel = stringResource(R.string.common_all)
    val filterOptions: List<Pair<String, CategoryId?>> = buildList {
        add(allLabel to null)
        state.availableCategories.forEach { add(it.name to it.id) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.pageMargin,
            end = Spacing.pageMargin,
            top = Spacing.s5,
            bottom = Spacing.s10 + Spacing.s7, // clear the Manage FAB
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (showHeader) item { HeaderRow(onCreate = onAdd) }

        item {
            SearchField(
                value = state.search,
                onValueChange = onSearchChange,
                placeholder = stringResource(R.string.brand_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.availableCategories.isNotEmpty()) {
            item {
                CategoryFilterRow(
                    allOptions = filterOptions,
                    colorByCategory = state.availableCategories.associate { it.id to it.color },
                    selected = state.categoryFilter,
                    onSelect = onCategoryFilterChange,
                )
            }
        }

        // Most-used card hidden for now (see the shared MostUsedCard / BrandRow.transactionCount).

        item {
            SectionHeader(title = stringResource(R.string.brand_all_section))
        }

        if (state.rows.isEmpty()) {
            item {
                EmptyStatePanel(
                    title = when {
                        state.search.isNotBlank() -> stringResource(R.string.common_no_matches)
                        state.categoryFilter != null -> stringResource(R.string.brand_empty_in_category)
                        else -> stringResource(R.string.brand_empty_title)
                    },
                    subtitle = if (state.search.isBlank())
                        stringResource(R.string.brand_empty_subtitle)
                    else
                        stringResource(R.string.common_no_matches_subtitle, state.search),
                    icon = HugeIcons.Storefront,
                )
            }
        } else {
            items(state.rows, key = { it.id.value }) { row ->
                BrandRowItem(
                    row = row,
                    onEdit = { onEdit(row.id) },
                    onDelete = { pendingDelete = row },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item { Spacer(Modifier.height(Spacing.s3)) }
    }

    pendingDelete?.let { row ->
        BrandDeleteDialog(
            row = row,
            otherBrands = state.rows.filter { it.id != row.id },
            onDismiss = { pendingDelete = null },
            onConfirmDelete = { onDelete(row.id); pendingDelete = null },
            onConfirmMerge = { target -> onMerge(row.id, target); pendingDelete = null },
        )
    }
}

@Composable
private fun BrandDeleteDialog(
    row: BrandRow,
    otherBrands: List<BrandRow>,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmMerge: (BrandId) -> Unit,
) {
    if (row.transactionCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.common_delete_title, row.name)) },
            text = { Text(stringResource(R.string.brand_delete_empty_body)) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }

    var target by remember { mutableStateOf<BrandRow?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val count = row.transactionCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_delete_title, row.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(pluralStringResource(R.plurals.brand_delete_move_body, count, count))
                if (otherBrands.isEmpty()) {
                    Text(
                        stringResource(R.string.brand_delete_no_target),
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
                                target?.name ?: stringResource(R.string.brand_delete_choose),
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
            ) { Text(stringResource(R.string.brand_delete_and_move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HeaderRow(onCreate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.common_brands),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CreateActionButton(text = stringResource(R.string.brand_new_title), onClick = onCreate)
    }
}

@Composable
private fun CategoryFilterRow(
    allOptions: List<Pair<String, CategoryId?>>,
    colorByCategory: Map<CategoryId, String>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        contentPadding = PaddingValues(vertical = Spacing.s1),
    ) {
        items(allOptions) { (label, value) ->
            ColoredFilterChip(
                label = label,
                colorKey = value?.let { colorByCategory[it] },
                selected = selected == value,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun BrandRowItem(
    row: BrandRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = tintPairForColor(row.categoryColor)
    ListRow(
        modifier = modifier,
        title = row.name,
        subtitle = row.categoryName,
        leading = {
            CircleIconTile(
                icon = iconForKey(row.categoryIcon),
                background = bg,
                foreground = fg,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.totalMinor > 0L) {
                    AmountText(
                        value = row.totalMinor / 100.0,
                        tone = AmountTone.Neutral,
                        showSign = false,
                        size = 14.sp,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.DeleteOutline,
                        contentDescription = stringResource(R.string.common_delete_named, row.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onEdit,
    )
}
