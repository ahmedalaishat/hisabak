package com.hisabak.feature.category.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hisabak.shared.resources.*
import com.hisabak.ui.components.SearchField
import com.hisabak.ui.components.dismissKeyboardOnGesture
import com.hisabak.ui.components.iconForKey
import com.hisabak.ui.components.tintPairForColor
import com.hisabak.feature.category.domain.CategoryIconEntry
import com.hisabak.feature.category.domain.CategoryIconGroup
import com.hisabak.feature.category.domain.iconSearchSeed
import com.hisabak.feature.category.domain.searchCategoryIcons
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The icon picker: a search field over the whole catalogue, with the unfiltered list grouped into
 * labelled sections so browsing stays possible when the user doesn't have a word in mind.
 * Searching flattens the sections — with a query on screen, ranked results beat taxonomy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryIconPickerSheet(
    selectedKey: String,
    colorKey: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /**
     * The category's name. Opening on it puts the likely icon on screen immediately — you named
     * the thing already, so making you search for "coffee" a second time is busywork. Ignored
     * when it matches nothing, so a name like "Misc" still opens on the full browsable list.
     */
    nameHint: String = "",
) {
    var query by rememberSaveable { mutableStateOf(iconSearchSeed(nameHint)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val results = remember(query) { searchCategoryIcons(query) }
    val searching = query.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            // Built here, inside the sheet's own composition: a sheet is a separate window with
            // its own focus owner, so a modifier constructed at the call site would clear focus
            // on the parent window instead and do nothing.
            modifier = Modifier
                .dismissKeyboardOnGesture()
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(
                text = stringResource(Res.string.category_icon_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.category_icon_search_hint),
            )

            if (results.isEmpty()) {
                Text(
                    text = stringResource(Res.string.category_icon_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sectionGap),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = IconCell + Spacing.s3),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                    contentPadding = PaddingValues(bottom = Spacing.sectionGap),
                ) {
                    if (searching) {
                        items(results, key = { it.key }) { entry ->
                            IconCell(entry, colorKey, entry.key == selectedKey, onPick)
                        }
                    } else {
                        for (group in CategoryIconGroup.entries) {
                            val inGroup = results.filter { it.group == group }
                            if (inGroup.isEmpty()) continue
                            item(span = { GridItemSpan(maxLineSpan) }, key = "group-${group.name}") {
                                Text(
                                    text = stringResource(group.label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.s3),
                                )
                            }
                            items(inGroup, key = { it.key }) { entry ->
                                IconCell(entry, colorKey, entry.key == selectedKey, onPick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCell(
    entry: CategoryIconEntry,
    colorKey: String,
    selected: Boolean,
    onPick: (String) -> Unit,
) {
    val (bg, fg) = tintPairForColor(colorKey)
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .size(IconCell)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable { onPick(entry.key) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconForKey(entry.key),
            // A screen reader should hear "coffee", not "fast-food" spelled as an identifier.
            contentDescription = entry.keywords.firstOrNull() ?: entry.key,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val IconCell = 48.dp

private val CategoryIconGroup.label: StringResource
    get() = when (this) {
        CategoryIconGroup.FOOD -> Res.string.category_icon_group_food
        CategoryIconGroup.TRANSPORT -> Res.string.category_icon_group_transport
        CategoryIconGroup.SHOPPING -> Res.string.category_icon_group_shopping
        CategoryIconGroup.HOME -> Res.string.category_icon_group_home
        CategoryIconGroup.HEALTH -> Res.string.category_icon_group_health
        CategoryIconGroup.TRAVEL -> Res.string.category_icon_group_travel
        CategoryIconGroup.WORK -> Res.string.category_icon_group_work
        CategoryIconGroup.MONEY -> Res.string.category_icon_group_money
        CategoryIconGroup.LEISURE -> Res.string.category_icon_group_leisure
        CategoryIconGroup.TECH -> Res.string.category_icon_group_tech
        CategoryIconGroup.OTHER -> Res.string.category_icon_group_other
    }
