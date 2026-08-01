package com.hisabak.feature.sms.presentation.templates

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.template.TagRole
import com.hisabak.feature.sms.domain.template.TemplatePreview
import com.hisabak.feature.sms.domain.template.TemplateValidationError

data class SmsTemplateEditUiState(
    val sample: String = "",
    /** Whitespace-delimited tokens of [sample]; tapping one assigns/clears the active role. */
    val tokens: List<Token> = emptyList(),
    val activeRole: TagRole = TagRole.AMOUNT,
    /** Live-derived pattern shown read-only for reference. */
    val pattern: String = "",
    /** What the pattern extracts from the sample right now. */
    val previewAmount: String? = null,
    val previewBrand: String? = null,
    val previewDate: String? = null,
    /** How widely the draft matches the stored inbox (debounced; null while computing). */
    val inboxPreview: TemplatePreview? = null,
    val validationError: TemplateValidationError? = null,
    /** Opened from an inbox message — saving also imports that message, and the button says so. */
    val importsSample: Boolean = false,
    /** The derived pattern exactly matches a stored template — surfaced instead of silently
     *  deduped: enabled → nothing to save; disabled → the action becomes "enable". */
    val duplicate: DuplicateInfo? = null,
    /** Viewing a shipped default: pattern only, nothing editable. */
    val isDefaultTemplate: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val generalError: String? = null,
) : ViewState {
    data class Token(val text: String, val start: Int, val end: Int, val role: TagRole?)

    data class DuplicateInfo(val id: SmsTemplateId, val enabled: Boolean)

    val canSave: Boolean
        get() = !isDefaultTemplate && !isSaving && !isLoading &&
            sample.isNotBlank() && validationError == null &&
            duplicate?.enabled != true
}

sealed interface SmsTemplateEditIntent : ViewIntent {
    data class SampleChanged(val value: String) : SmsTemplateEditIntent
    data class RoleSelected(val role: TagRole) : SmsTemplateEditIntent
    data class TokenTapped(val index: Int) : SmsTemplateEditIntent
    data object Save : SmsTemplateEditIntent
    data object ConsumeEffect : SmsTemplateEditIntent
}

sealed interface SmsTemplateEditEffect : ViewEffect {
    data object Saved : SmsTemplateEditEffect
}
