package com.hisabak

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.presentation.list.BrandListRoute
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.presentation.list.CategoryListRoute
import com.hisabak.ui.components.IconTile
import com.hisabak.ui.components.SurfaceCard
import com.hisabak.ui.components.localizeDigits
import com.hisabak.ui.components.rememberIsArabic
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

private enum class ManageTab { Brands, Categories }

@Composable
fun ManageRoute(
    modifier: Modifier = Modifier,
    onAddBrand: () -> Unit,
    onEditBrand: (BrandId) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (CategoryId) -> Unit,
    viewModel: ManageViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(ManageTab.Brands) }
    val counts by viewModel.counts.collectAsStateWithLifecycle()

    // List view: count cards act as the Brands/Categories switcher; FAB adds the active type.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.pageMargin, vertical = Spacing.cardGap),
                horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                ManageSwitchCard(
                    label = stringResource(R.string.common_brands),
                    count = counts.brands,
                    icon = HugeIcons.Storefront,
                    selected = tab == ManageTab.Brands,
                    onClick = { tab = ManageTab.Brands },
                    modifier = Modifier.weight(1f),
                )
                ManageSwitchCard(
                    label = stringResource(R.string.common_categories),
                    count = counts.categories,
                    icon = HugeIcons.Category,
                    selected = tab == ManageTab.Categories,
                    onClick = { tab = ManageTab.Categories },
                    modifier = Modifier.weight(1f),
                )
            }

            when (tab) {
                ManageTab.Brands -> BrandListRoute(
                    onAdd = onAddBrand,
                    onEdit = onEditBrand,
                    showHeader = false,
                )
                ManageTab.Categories -> CategoryListRoute(
                    onAdd = onAddCategory,
                    onEdit = onEditCategory,
                    showHeader = false,
                )
            }
        }

        FloatingActionButton(
            onClick = {
                when (tab) {
                    ManageTab.Brands -> onAddBrand()
                    ManageTab.Categories -> onAddCategory()
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.pageMargin),
        ) {
            Icon(
                HugeIcons.Add,
                contentDescription = stringResource(if (tab == ManageTab.Brands) R.string.brand_new_title else R.string.category_new_title),
            )
        }
    }
}

@Composable
private fun ManageSwitchCard(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    // incomeSoft is a translucent tint in dark theme; composite it over the card surface so the
    // selected card stays opaque and distinct from the page rather than blending into it.
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val bg = if (selected) HisabakTheme.colors.incomeSoft.compositeOver(surface) else surface
    val iconBg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val iconFg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    SurfaceCard(
        modifier = modifier,
        contentPadding = Spacing.s4,
        backgroundColor = bg,
        borderColor = border,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(
                icon = icon,
                size = Spacing.s8,
                iconSize = 16.dp,
                background = iconBg,
                foreground = iconFg,
            )
            Column {
                Text(
                    text = localizeDigits(count.toString(), rememberIsArabic()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
