package com.hisabak.feature.transaction.presentation.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.transaction.domain.TransactionId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionEditRoute(
    transactionId: TransactionId?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onEditBrand: (com.hisabak.feature.brand.domain.BrandId) -> Unit = {},
    viewModel: TransactionEditViewModel = koinViewModel(
        key = transactionId?.value ?: "new",
        parameters = { parametersOf(transactionId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(TransactionEditIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            TransactionEditEffect.Saved, TransactionEditEffect.Deleted -> onDone()
        }
    }

    TransactionEditScreen(
        state = state,
        onAmountChange = { viewModel.onIntent(TransactionEditIntent.AmountChanged(it)) },
        onBrandSelected = { viewModel.onIntent(TransactionEditIntent.BrandSelected(it)) },
        onNoteChange = { viewModel.onIntent(TransactionEditIntent.NoteChanged(it)) },
        onTypeSelected = { viewModel.onIntent(TransactionEditIntent.TypeSelected(it)) },
        onDirectionChange = { viewModel.onIntent(TransactionEditIntent.DirectionChanged(it)) },
        onEditBrand = onEditBrand,
        onDateClick = { viewModel.onIntent(TransactionEditIntent.DatePickerOpened) },
        onDateSelected = { viewModel.onIntent(TransactionEditIntent.DateChanged(it)) },
        onDateDismiss = { viewModel.onIntent(TransactionEditIntent.DatePickerDismissed) },
        onSave = { viewModel.onIntent(TransactionEditIntent.Save) },
        onCancel = onCancel,
        onDeleteRequest = { viewModel.onIntent(TransactionEditIntent.DeleteRequested) },
        onDeleteConfirm = { viewModel.onIntent(TransactionEditIntent.DeleteConfirmed) },
        onDeleteDismiss = { viewModel.onIntent(TransactionEditIntent.DeleteDismissed) },
    )
}
