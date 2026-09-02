package com.hisabak.feature.sms.presentation.templates

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainResult
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.sms.domain.rankTemplates
import com.hisabak.feature.sms.domain.template.DeleteSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.ObserveSmsTemplatesUseCase
import com.hisabak.feature.sms.domain.template.SetSmsTemplateEnabledUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SmsTemplatesViewModel(
    observeTemplates: ObserveSmsTemplatesUseCase,
    private val setEnabled: SetSmsTemplateEnabledUseCase,
    private val deleteTemplate: DeleteSmsTemplateUseCase,
) : BaseViewModel<SmsTemplatesIntent, SmsTemplatesUiState, SmsTemplatesEffect>() {

    override fun initialState() = SmsTemplatesUiState()

    init {
        observeTemplates()
            .onEach { templates ->
                val rows = rankTemplates(templates).map {
                    SmsTemplatesUiState.TemplateRow(
                        id = it.id,
                        pattern = it.pattern,
                        isDefault = it.isDefault,
                        enabled = it.enabled,
                        derivedByAi = it.derivedByAi,
                    )
                }
                setState { copy(templates = rows, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SmsTemplatesIntent) {
        when (intent) {
            is SmsTemplatesIntent.ToggleEnabled -> viewModelScope.launch {
                val result = setEnabled(intent.id, intent.enabled)
                if (result is DomainResult.Failure) {
                    sendEffect(SmsTemplatesEffect.Error(result.error.message))
                }
            }
            is SmsTemplatesIntent.DeleteRequested ->
                setState { copy(pendingDelete = intent.id) }
            SmsTemplatesIntent.DeleteDismissed ->
                setState { copy(pendingDelete = null) }
            SmsTemplatesIntent.DeleteConfirmed -> {
                val id = state.value.pendingDelete ?: return
                setState { copy(pendingDelete = null) }
                viewModelScope.launch {
                    val result = deleteTemplate(id)
                    if (result is DomainResult.Failure) {
                        sendEffect(SmsTemplatesEffect.Error(result.error.message))
                    }
                }
            }
            SmsTemplatesIntent.ConsumeEffect -> clearEffect()
        }
    }
}
