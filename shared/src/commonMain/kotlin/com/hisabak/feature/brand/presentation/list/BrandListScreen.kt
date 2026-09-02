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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.shared.resources.*
import com.hisabak.ui.components.localizedFormatArg
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.ui.components.AmountText
import com.hisabak.ui.components.AmountTone
import com.hisabak.ui.components.CircleIconTile
import com.hisabak.ui.components.ChipLaneGrid
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
import androidx.compose.foundation.layout.offset

@Composable
fun BrandListScreen(
    state: BrandListUiState,
    onSearchChange: (String) -> Unit,
    onCategoryFilterChange: (CategoryId?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BrandId) -> Unit,
    onViewTransactions: (BrandId) -> Unit,
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


    val allLabel = stringResource(Res.string.common_all)
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
                placeholder = stringResource(Res.string.brand_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.availableCategories.isNotEmpty()) {
            item {
                CategoryFilterRow(
                    allOptions = filterOptions,
                    colorByCategory = state.availableCategories.associate { it.id to it.color },
                    iconByCategory = state.availableCategories.associate { it.id to it.icon },
                    selected = state.categoryFilter,
                    onSelect = onCategoryFilterChange,
                )
            }
        }

        // Most-used card hidden for now (see the shared MostUsedCard / BrandRow.transactionCount).

        item {
            SectionHeader(title = stringResource(Res.string.brand_all_section))
        }

        if (state.rows.isEmpty()) {
            item {
                EmptyStatePanel(
                    title = when {
                        state.search.isNotBlank() -> stringResource(Res.string.common_no_matches)
                        state.categoryFilter != null -> stringResource(Res.string.brand_empty_in_category)
                        else -> stringResource(Res.string.brand_empty_title)
                    },
                    subtitle = if (state.search.isBlank())
                        stringResource(Res.string.brand_empty_subtitle)
                    else
                        stringResource(Res.string.common_no_matches_subtitle, state.search),
                    icon = HugeIcons.Storefront,
                )
            }
        } else {
            items(state.rows, key = { it.id.value }) { row ->
                BrandRowItem(
                    row = row,
                    onEdit = { onEdit(row.id) },
                    onViewTransactions = { onViewTransactions(row.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item { Spacer(Modifier.height(Spacing.s3)) }
    }

}

@Composable
private fun HeaderRow(onCreate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.common_brands),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CreateActionButton(text = stringResource(Res.string.brand_new_title), onClick = onCreate)
    }
}

@Composable
private fun CategoryFilterRow(
    allOptions: List<Pair<String, CategoryId?>>,
    colorByCategory: Map<CategoryId, String>,
    iconByCategory: Map<CategoryId, String>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
) {
    ChipLaneGrid(chipCount = allOptions.size) {
        staggeredItems(allOptions) { (label, value) ->
            ColoredFilterChip(
                label = label,
                colorKey = value?.let { colorByCategory[it] },
                iconKey = value?.let { iconByCategory[it] },
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
    onViewTransactions: () -> Unit,
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
                // Any activity shows a total — a fully-repaid savings brand reads 0, an
                // over-withdrawn one reads − (Neutral tone never shows a +).
                if (row.transactionCount > 0) {
                    AmountText(
                        value = row.totalMinor / 100.0,
                        tone = AmountTone.Neutral,
                        showSign = true,
                        size = 14.sp,
                    )
                }
                // This brand's transactions. Editing is the row tap; deleting moved into the
                // editor. Offset so the glyph lands on the card's content edge — an IconButton
                // centres its icon in a 48dp target, which would otherwise inset it past the
                // padding the leading tile sits on.
                IconButton(
                    onClick = onViewTransactions,
                    modifier = Modifier.offset(x = IconButtonInset),
                ) {
                    Icon(
                        imageVector = HugeIcons.ReceiptLong,
                        contentDescription = stringResource(Res.string.action_view_transactions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizing.iconSm),
                    )
                }
            }
        },
        onClick = onEdit,
    )
}


/**
 * Half the gap between an IconButton's 48dp touch target and its glyph — pulls the icon back onto
 * the card's content padding so it lines up with the leading tile on the other side.
 */
private val IconButtonInset = 12.dp
