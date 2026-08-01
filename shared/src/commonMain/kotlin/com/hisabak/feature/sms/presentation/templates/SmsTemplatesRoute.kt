package com.hisabak.feature.sms.presentation.templates

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.sms.domain.SmsTemplateId
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SmsTemplatesRoute(
    onAdd: () -> Unit,
    onOpen: (SmsTemplateId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsTemplatesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(SmsTemplatesIntent.ConsumeEffect) },
    ) { effect ->
        when (effect) {
            is SmsTemplatesEffect.Error -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    SmsTemplatesScreen(
        state = state,
        onAdd = onAdd,
        onOpen = onOpen,
        onToggle = { id, enabled -> viewModel.onIntent(SmsTemplatesIntent.ToggleEnabled(id, enabled)) },
        onDeleteRequest = { viewModel.onIntent(SmsTemplatesIntent.DeleteRequested(it)) },
        onDeleteConfirm = { viewModel.onIntent(SmsTemplatesIntent.DeleteConfirmed) },
        onDeleteDismiss = { viewModel.onIntent(SmsTemplatesIntent.DeleteDismissed) },
        modifier = modifier,
    )
}
