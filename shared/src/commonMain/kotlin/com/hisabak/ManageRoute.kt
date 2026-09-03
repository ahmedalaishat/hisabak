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
import com.hisabak.shared.resources.manage_tab_label
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.presentation.list.BrandListRoute
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.presentation.list.CategoryListRoute
import com.hisabak.ui.components.localizeDigits
import com.hisabak.ui.components.rememberIsArabic
import com.hisabak.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel
import com.hisabak.shared.resources.*

private enum class ManageTab { Brands, Categories }

@Composable
fun ManageRoute(
    modifier: Modifier = Modifier,
    onAddBrand: () -> Unit,
    onEditBrand: (BrandId) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (CategoryId) -> Unit,
    onViewBrandTransactions: (BrandId) -> Unit,
    onViewCategoryTransactions: (CategoryId) -> Unit,
    viewModel: ManageViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(ManageTab.Brands) }
    val counts by viewModel.counts.collectAsStateWithLifecycle()

    // List view: count cards act as the Brands/Categories switcher; FAB adds the active type.
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The same segmented control the dashboard, the ledger and insights use. The count
            // rides in the label, which is the only thing the old count cards carried that a
            // reader could not get faster from the word itself.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.pageMargin)
                    .padding(top = Spacing.pageMargin, bottom = Spacing.s3),
            ) {
                val arabic = rememberIsArabic()
                ManageTab.entries.forEachIndexed { index, entry ->
                    val count = if (entry == ManageTab.Brands) counts.brands else counts.categories
                    SegmentedButton(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        shape = SegmentedButtonDefaults.itemShape(index, ManageTab.entries.size),
                    ) {
                        Text(
                            stringResource(
                                Res.string.manage_tab_label,
                                stringResource(
                                    if (entry == ManageTab.Brands) Res.string.common_brands else Res.string.common_categories,
                                ),
                                localizeDigits(count.toString(), arabic),
                            ),
                        )
                    }
                }
            }

            when (tab) {
                ManageTab.Brands -> BrandListRoute(
                    onAdd = onAddBrand,
                    onEdit = onEditBrand,
                    onViewTransactions = onViewBrandTransactions,
                    showHeader = false,
                )
                ManageTab.Categories -> CategoryListRoute(
                    onAdd = onAddCategory,
                    onEdit = onEditCategory,
                    onViewTransactions = onViewCategoryTransactions,
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
                contentDescription = stringResource(if (tab == ManageTab.Brands) Res.string.brand_new_title else Res.string.category_new_title),
            )
        }
    }
}

