package com.hisabak.feature.brand.presentation.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.presentation.edit.CategoryEditPrefill
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BrandEditRoute(
    brandId: BrandId?,
    onDone: (BrandId) -> Unit,
    onCancel: () -> Unit,
    onCreateCategory: (CategoryEditPrefill?) -> Unit,
    onDeleted: () -> Unit = onCancel,
    viewModel: BrandEditViewModel = koinViewModel(
        key = brandId?.value ?: "new",
        parameters = { parametersOf(brandId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(BrandEditIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            is BrandEditEffect.Saved -> onDone(effect.id)
            is BrandEditEffect.OpenCategoryEditor -> onCreateCategory(effect.prefill)
            BrandEditEffect.Deleted -> onDeleted()
            is BrandEditEffect.Message -> snackbarHostState.showSnackbar(effect.text)
        }
    }

    Box(Modifier.fillMaxSize()) {
    BrandEditScreen(
        state = state,
        onNameChange = { viewModel.onIntent(BrandEditIntent.NameChanged(it)) },
        onCategoryChange = { viewModel.onIntent(BrandEditIntent.CategoryChanged(it)) },
        onCreateCategory = { onCreateCategory(null) },
        onAcceptSuggestion = { viewModel.onIntent(BrandEditIntent.SuggestionAccepted) },
        onSave = { viewModel.onIntent(BrandEditIntent.Save) },
        onCancel = onCancel,
        onDelete = { viewModel.onIntent(BrandEditIntent.Delete) },
        onMergeInto = { viewModel.onIntent(BrandEditIntent.MergeInto(it)) },
    )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
