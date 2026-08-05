package com.hisabak.feature.sms.presentation.templates

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.sms.domain.SmsTemplateId

data class SmsTemplatesUiState(
    val templates: List<TemplateRow> = emptyList(),
    val pendingDelete: SmsTemplateId? = null,
    val isLoading: Boolean = true,
) : ViewState {
    /** Rows are shown in matching order (specificity-ranked), so the list explains precedence. */
    data class TemplateRow(
        val id: SmsTemplateId,
        val pattern: String,
        val isDefault: Boolean,
        val enabled: Boolean,
    )
}

sealed interface SmsTemplatesIntent : ViewIntent {
    data class ToggleEnabled(val id: SmsTemplateId, val enabled: Boolean) : SmsTemplatesIntent
    data class DeleteRequested(val id: SmsTemplateId) : SmsTemplatesIntent
    data object DeleteDismissed : SmsTemplatesIntent
    data object DeleteConfirmed : SmsTemplatesIntent
    data object ConsumeEffect : SmsTemplatesIntent
}

sealed interface SmsTemplatesEffect : ViewEffect {
    data class Error(val message: String) : SmsTemplatesEffect
}
