package com.hisabak.feature.sms.presentation.templates

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainResult
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.template.PreviewSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SaveSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SetSmsTemplateEnabledUseCase
import com.hisabak.feature.sms.domain.template.TagRole
import com.hisabak.feature.sms.domain.template.TagSpan
import com.hisabak.feature.sms.domain.usecase.ReparseSmsMessageUseCase
import com.hisabak.feature.sms.domain.template.deriveTemplatePattern
import com.hisabak.feature.sms.domain.template.previewFields
import com.hisabak.feature.sms.domain.template.reconstructSpans
import com.hisabak.feature.sms.domain.template.suggestSpans
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsTemplateEditViewModel(
    private val templateId: SmsTemplateId?,
    private val sampleSmsId: SmsMessageId?,
    private val templateRepository: SmsTemplateRepository,
    private val smsRepository: SmsRepository,
    private val saveTemplate: SaveSmsTemplateUseCase,
    private val previewTemplate: PreviewSmsTemplateUseCase,
    private val reparseSms: ReparseSmsMessageUseCase,
    private val setTemplateEnabled: SetSmsTemplateEnabledUseCase,
) : BaseViewModel<SmsTemplateEditIntent, SmsTemplateEditUiState, SmsTemplateEditEffect>() {

    /** Live template list for synchronous duplicate detection while tagging. */
    private val allTemplates = MutableStateFlow<List<SmsParserTemplate>>(emptyList())

    override fun initialState() = SmsTemplateEditUiState(
        isLoading = templateId != null || sampleSmsId != null,
        importsSample = sampleSmsId != null,
    )

    private val patternForPreview = MutableStateFlow("")

    init {
        viewModelScope.launch {
            templateRepository.observeAll().collect { templates ->
                allTemplates.value = templates
                // Re-evaluate the current draft: a toggle on the templates screen (or the
                // save itself) changes what "duplicate" means.
                if (!state.value.isLoading) applyTokens(state.value.tokens)
            }
        }
        viewModelScope.launch { loadInitial() }
        viewModelScope.launch {
            patternForPreview.collectLatest { pattern ->
                if (pattern.isBlank() || state.value.isDefaultTemplate) return@collectLatest
                delay(PREVIEW_DEBOUNCE_MS)
                val preview = previewTemplate(pattern, state.value.sample)
                setState { copy(inboxPreview = preview) }
            }
        }
    }

    override fun onIntent(intent: SmsTemplateEditIntent) {
        when (intent) {
            is SmsTemplateEditIntent.SampleChanged -> {
                // Tags are character offsets into the old text — re-suggest from scratch.
                applySample(intent.value, suggestSpans(intent.value))
            }
            is SmsTemplateEditIntent.RoleSelected ->
                setState { copy(activeRole = intent.role) }
            is SmsTemplateEditIntent.TokenTapped -> {
                val tokens = state.value.tokens.toMutableList()
                val token = tokens.getOrNull(intent.index) ?: return
                val active = state.value.activeRole
                tokens[intent.index] = token.copy(role = if (token.role == active) null else active)
                applyTokens(tokens)
            }
            SmsTemplateEditIntent.Save -> save()
            SmsTemplateEditIntent.ConsumeEffect -> clearEffect()
        }
    }

    private suspend fun loadInitial() {
        if (templateId != null) {
            val template = templateRepository.observeAll().first().firstOrNull { it.id == templateId }
            when {
                template == null -> setState { copy(isLoading = false) }
                template.isDefault -> setState {
                    copy(isLoading = false, isDefaultTemplate = true, pattern = template.pattern)
                }
                else -> {
                    val sample = template.sampleBody.orEmpty()
                    val spans = template.sampleBody?.let { reconstructSpans(template.pattern, it) }
                        ?: suggestSpans(sample)
                    applySample(sample, spans, loading = false)
                }
            }
            return
        }
        val sample = sampleSmsId?.let { id ->
            (smsRepository.getById(id) as? DomainResult.Success)?.value?.body
        }.orEmpty()
        applySample(sample, suggestSpans(sample), loading = false)
    }

    private fun applySample(sample: String, spans: List<TagSpan>, loading: Boolean = false) {
        val tokens = tokenize(sample).map { token ->
            val role = spans.firstOrNull { it.start < token.end && token.start < it.end }?.role
            token.copy(role = role)
        }
        applyTokens(tokens, sample = sample, loading = loading)
    }

    private fun applyTokens(
        tokens: List<SmsTemplateEditUiState.Token>,
        sample: String = state.value.sample,
        loading: Boolean = false,
    ) {
        val spans = tokens.mapNotNull { t -> t.role?.let { TagSpan(it, t.start, t.end) } }
        val pattern = if (sample.isBlank()) "" else deriveTemplatePattern(sample, spans)
        val fields = if (pattern.isBlank()) null else previewFields(pattern, sample)?.fields
        val duplicate = if (pattern.isBlank()) {
            null
        } else {
            allTemplates.value
                .firstOrNull { it.pattern == pattern && it.id != templateId }
                ?.let { SmsTemplateEditUiState.DuplicateInfo(id = it.id, enabled = it.enabled) }
        }
        // An unchanged pattern won't re-emit through the StateFlow, so keep the current inbox
        // preview instead of blanking it forever.
        val patternChanged = pattern != state.value.pattern
        setState {
            copy(
                sample = sample,
                tokens = tokens,
                pattern = pattern,
                previewAmount = fields?.get("amount"),
                previewBrand = fields?.get("brand"),
                previewDate = listOfNotNull(fields?.get("date"), fields?.get("time"))
                    .joinToString(" ")
                    .ifBlank { null },
                validationError = if (sample.isBlank()) null else saveTemplate.validate(sample, spans),
                duplicate = duplicate,
                inboxPreview = if (patternChanged) null else inboxPreview,
                isLoading = loading,
            )
        }
        patternForPreview.value = pattern
    }

    private fun save() {
        val s = state.value
        if (!s.canSave) return
        val duplicate = s.duplicate
        if (duplicate != null && !duplicate.enabled) {
            // The format is already taught, just switched off — the action is to switch it
            // back on (and import the source message, when there is one).
            setState { copy(isSaving = true, generalError = null) }
            viewModelScope.launch {
                when (val result = setTemplateEnabled(duplicate.id, true)) {
                    is DomainResult.Success -> {
                        sampleSmsId?.let { reparseSms(it) }
                        setState { copy(isSaving = false) }
                        sendEffect(SmsTemplateEditEffect.Saved)
                    }
                    is DomainResult.Failure -> setState {
                        copy(isSaving = false, generalError = result.error.message)
                    }
                }
            }
            return
        }
        val spans = s.tokens.mapNotNull { t -> t.role?.let { TagSpan(it, t.start, t.end) } }
        setState { copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            when (val result = saveTemplate(templateId, s.sample, spans)) {
                is DomainResult.Success -> {
                    // The template was defined from a specific inbox message — parse it through
                    // the format it just taught instead of leaving it sitting unparsed.
                    sampleSmsId?.let { reparseSms(it) }
                    setState { copy(isSaving = false) }
                    sendEffect(SmsTemplateEditEffect.Saved)
                }
                is DomainResult.Failure -> setState {
                    copy(isSaving = false, generalError = result.error.message)
                }
            }
        }
    }

    private fun tokenize(sample: String): List<SmsTemplateEditUiState.Token> {
        val tokens = mutableListOf<SmsTemplateEditUiState.Token>()
        var i = 0
        while (i < sample.length) {
            if (sample[i].isWhitespace()) {
                i++
                continue
            }
            val start = i
            while (i < sample.length && !sample[i].isWhitespace()) i++
            tokens += SmsTemplateEditUiState.Token(sample.substring(start, i), start, i, role = null)
        }
        return tokens
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
    }
}
