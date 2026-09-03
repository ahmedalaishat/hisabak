package com.hisabak.feature.category.presentation.edit

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
import com.hisabak.feature.category.domain.CategoryId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CategoryEditRoute(
    categoryId: CategoryId?,
    onDone: (CategoryId) -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit = onCancel,
    prefill: CategoryEditPrefill? = null,
    /** A proposed monthly cap (from a narrative suggestion) shown in the limit field, unsaved until Save. */
    proposedLimitMinor: Long? = null,
    viewModel: CategoryEditViewModel = koinViewModel(
        key = categoryId?.value ?: "new",
        parameters = { parametersOf(categoryId, prefill, proposedLimitMinor) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(CategoryEditIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            is CategoryEditEffect.Saved -> onDone(effect.id)
            CategoryEditEffect.Deleted -> onDeleted()
            is CategoryEditEffect.DeleteFailed -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    Box(Modifier.fillMaxSize()) {
    CategoryEditScreen(
        state = state,
        onNameChange = { viewModel.onIntent(CategoryEditIntent.NameChanged(it)) },
        onTypeChange = { viewModel.onIntent(CategoryEditIntent.TypeChanged(it)) },
        onColorChange = { viewModel.onIntent(CategoryEditIntent.ColorChanged(it)) },
        onIconChange = { viewModel.onIntent(CategoryEditIntent.IconChanged(it)) },
        onLimitChange = { viewModel.onIntent(CategoryEditIntent.LimitChanged(it)) },
        onSave = { viewModel.onIntent(CategoryEditIntent.Save) },
        onCancel = onCancel,
        onDelete = { viewModel.onIntent(CategoryEditIntent.Delete) },
    )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
