package com.hisabak.feature.sms.presentation.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.presentation.templates.SmsTemplateEditIntent.ConsumeEffect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SmsTemplateEditRoute(
    templateId: SmsTemplateId?,
    sampleSmsId: SmsMessageId?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsTemplateEditViewModel = koinViewModel(
        key = templateId?.value ?: sampleSmsId?.value ?: "new",
        parameters = { parametersOf(templateId, sampleSmsId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(ConsumeEffect) },
    ) { effect ->
        when (effect) {
            SmsTemplateEditEffect.Saved -> onDone()
        }
    }

    SmsTemplateEditScreen(
        state = state,
        onSampleChange = { viewModel.onIntent(SmsTemplateEditIntent.SampleChanged(it)) },
        onRoleSelect = { viewModel.onIntent(SmsTemplateEditIntent.RoleSelected(it)) },
        onTokenTap = { viewModel.onIntent(SmsTemplateEditIntent.TokenTapped(it)) },
        onSave = { viewModel.onIntent(SmsTemplateEditIntent.Save) },
        modifier = modifier,
    )
}
