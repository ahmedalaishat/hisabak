package com.hisabak.feature.brand.presentation.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.brand.domain.BrandId
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BrandListRoute(
    onAdd: () -> Unit,
    onEdit: (BrandId) -> Unit,
    showHeader: Boolean = true,
    viewModel: BrandListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(BrandListIntent.ConsumeEffect) },
        onEffect = { effect ->
            when (effect) {
                is BrandListEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        },
    )

    Box(Modifier.fillMaxSize()) {
        BrandListScreen(
            state = state,
            onSearchChange = { viewModel.onIntent(BrandListIntent.SearchChanged(it)) },
            onCategoryFilterChange = { viewModel.onIntent(BrandListIntent.CategoryFilterChanged(it)) },
            onDelete = { viewModel.onIntent(BrandListIntent.Delete(it)) },
            onMerge = { source, target -> viewModel.onIntent(BrandListIntent.MergeAndDelete(source, target)) },
            onAdd = onAdd,
            onEdit = onEdit,
            showHeader = showHeader,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
