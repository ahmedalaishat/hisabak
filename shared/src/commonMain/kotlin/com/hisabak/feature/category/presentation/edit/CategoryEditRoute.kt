package com.hisabak.feature.category.presentation.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.category.domain.CategoryId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CategoryEditRoute(
    categoryId: CategoryId?,
    onDone: (CategoryId) -> Unit,
    onCancel: () -> Unit,
    prefill: CategoryEditPrefill? = null,
    viewModel: CategoryEditViewModel = koinViewModel(
        key = categoryId?.value ?: "new",
        parameters = { parametersOf(categoryId, prefill) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(CategoryEditIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            is CategoryEditEffect.Saved -> onDone(effect.id)
        }
    }

    CategoryEditScreen(
        state = state,
        onNameChange = { viewModel.onIntent(CategoryEditIntent.NameChanged(it)) },
        onTypeChange = { viewModel.onIntent(CategoryEditIntent.TypeChanged(it)) },
        onColorChange = { viewModel.onIntent(CategoryEditIntent.ColorChanged(it)) },
        onIconChange = { viewModel.onIntent(CategoryEditIntent.IconChanged(it)) },
        onLimitChange = { viewModel.onIntent(CategoryEditIntent.LimitChanged(it)) },
        onSave = { viewModel.onIntent(CategoryEditIntent.Save) },
        onCancel = onCancel,
    )
}
