package com.hisabak.feature.brand.presentation.edit

import androidx.compose.runtime.Composable
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
    viewModel: BrandEditViewModel = koinViewModel(
        key = brandId?.value ?: "new",
        parameters = { parametersOf(brandId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(BrandEditIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            is BrandEditEffect.Saved -> onDone(effect.id)
            is BrandEditEffect.OpenCategoryEditor -> onCreateCategory(effect.prefill)
        }
    }

    BrandEditScreen(
        state = state,
        onNameChange = { viewModel.onIntent(BrandEditIntent.NameChanged(it)) },
        onCategoryChange = { viewModel.onIntent(BrandEditIntent.CategoryChanged(it)) },
        onCreateCategory = { onCreateCategory(null) },
        onAcceptSuggestion = { viewModel.onIntent(BrandEditIntent.SuggestionAccepted) },
        onSave = { viewModel.onIntent(BrandEditIntent.Save) },
        onCancel = onCancel,
    )
}
