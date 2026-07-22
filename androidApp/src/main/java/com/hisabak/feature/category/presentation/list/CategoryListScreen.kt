package com.hisabak.feature.category.presentation.list

import com.hisabak.ui.icons.HugeIcons

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import com.hisabak.ui.components.SkeletonCard
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.hisabak.R
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.ui.components.AmountText
import com.hisabak.ui.components.AmountTone
import com.hisabak.ui.components.Badge
import com.hisabak.ui.components.BadgeTone
import com.hisabak.ui.components.CircleIconTile
import com.hisabak.ui.components.CreateActionButton
import com.hisabak.ui.components.EmptyStatePanel
import com.hisabak.ui.components.FilterChipRow
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.SearchField
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

@Composable
fun CategoryListScreen(
    state: CategoryListUiState,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (CategoryType?) -> Unit,
    onDelete: (CategoryId) -> Unit,
    onAdd: () -> Unit,
    onEdit: (CategoryId) -> Unit,
    showHeader: Boolean = true,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                    SkeletonCard(Modifier.weight(1f), height = 96.dp)
                    SkeletonCard(Modifier.weight(1f), height = 96.dp)
                }
            }
        }
        return
    }

    var pendingDelete by remember { mutableStateOf<CategoryRow?>(null) }

    val typeOptions: List<Pair<String, CategoryType?>> = listOf(
        stringResource(R.string.common_all) to null,
        stringResource(R.string.category_type_expenses) to CategoryType.EXPENSES,
        stringResource(R.string.category_type_income) to CategoryType.INCOME,
        stringResource(R.string.category_type_savings) to CategoryType.SAVINGS,
        stringResource(R.string.category_type_investment) to CategoryType.INVESTMENT,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.pageMargin,
            end = Spacing.pageMargin,
            top = Spacing.s5,
            bottom = Spacing.s10 + Spacing.s7, // clear the Manage FAB
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (showHeader) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.common_categories),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CreateActionButton(text = stringResource(R.string.category_new_title), onClick = onAdd)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchField(
                value = state.search,
                onValueChange = onSearchChange,
                placeholder = stringResource(R.string.category_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterChipRow(
                options = typeOptions,
                selected = state.typeFilter,
                onSelect = onTypeFilterChange,
                contentPadding = PaddingValues(vertical = Spacing.s1),
            )
        }

        // Most-used card hidden for now (see the shared MostUsedCard / CategoryRow.transactionCount).

        if (state.rows.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStatePanel(
                    title = when {
                        state.search.isNotBlank() -> stringResource(R.string.common_no_matches)
                        state.typeFilter != null -> stringResource(R.string.category_empty_in_type)
                        else -> stringResource(R.string.category_empty_title)
                    },
                    subtitle = if (state.search.isBlank())
                        stringResource(R.string.category_empty_subtitle)
                    else
                        stringResource(R.string.common_no_matches_subtitle, state.search),
                )
            }
        } else {
            items(state.rows, key = { it.id.value }) { row ->
                CategoryTile(
                    row = row,
                    onEdit = { onEdit(row.id) },
                    onDelete = { pendingDelete = row },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                AddNewTile(onClick = onAdd)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(Spacing.s3))
        }
    }

    pendingDelete?.let { row ->
        val count = row.transactionCount
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.common_delete_title, row.name)) },
            text = {
                Text(
                    if (count > 0)
                        pluralStringResource(R.plurals.category_delete_body_count, count, count)
                    else
                        stringResource(R.string.category_delete_body_empty),
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(row.id); pendingDelete = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryTile(
    row: CategoryRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = tintPairForColor(row.color)
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            IconTile(
                icon = iconForKey(row.icon),
                background = bg,
                foreground = fg,
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    HugeIcons.DeleteOutline,
                    contentDescription = stringResource(R.string.common_delete_named, row.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(Spacing.s2))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(
                label = row.type.displayName(),
                tone = row.type.badgeTone(),
            )
            if (row.totalMinor > 0L) {
                AmountText(
                    value = row.totalMinor / 100.0,
                    tone = AmountTone.Neutral,
                    showSign = false,
                    size = 13.sp,
                    weight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AddNewTile(onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val dashColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            )
            drawRoundRect(
                color = dashColor,
                style = stroke,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            )
        }
        Column(
            modifier = Modifier.padding(Spacing.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            CircleIconTile(
                icon = HugeIcons.Add,
                size = 40.dp,
                iconSize = Sizing.iconSm,
                background = HisabakTheme.colors.incomeSoft,
                foreground = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.category_add_new),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryType.displayName(): String = stringResource(
    when (this) {
        CategoryType.INCOME -> R.string.category_type_income
        CategoryType.EXPENSES -> R.string.category_type_expense
        CategoryType.SAVINGS -> R.string.category_type_savings
        CategoryType.INVESTMENT -> R.string.category_type_investment
    },
)

private fun CategoryType.badgeTone(): BadgeTone = when (this) {
    CategoryType.INCOME -> BadgeTone.Income
    CategoryType.EXPENSES -> BadgeTone.Expense
    CategoryType.SAVINGS -> BadgeTone.Savings
    CategoryType.INVESTMENT -> BadgeTone.Investment
}
