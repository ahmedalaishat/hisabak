package com.hisabak.feature.brand.presentation.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.brand.domain.BrandId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BrandEditRoute(
    brandId: BrandId?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
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
            BrandEditEffect.Saved -> onDone()
        }
    }

    BrandEditScreen(
        state = state,
        onNameChange = { viewModel.onIntent(BrandEditIntent.NameChanged(it)) },
        onCategoryChange = { viewModel.onIntent(BrandEditIntent.CategoryChanged(it)) },
        onSave = { viewModel.onIntent(BrandEditIntent.Save) },
        onCancel = onCancel,
    )
}
