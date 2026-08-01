package com.hisabak.feature.sms.presentation.inbox

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsTemplateDetector
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.feature.sms.domain.ai.ConfirmAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.DismissAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.SuggestAiParseUseCase
import com.hisabak.feature.sms.domain.capture.CaptureResult
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.feature.sms.domain.SmsMessageId
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SmsInboxViewModel(
    private val observeMessages: ObserveSmsMessagesUseCase,
    private val capture: CaptureTransactionUseCase,
    private val deleteSms: DeleteSmsUseCase,
    private val detector: SmsTemplateDetector,
    private val parser: SmsParser,
    private val aiParser: AiSmsParser,
    private val suggestAiParse: SuggestAiParseUseCase,
    private val confirmAiSuggestion: ConfirmAiSuggestionUseCase,
    private val dismissAiSuggestion: DismissAiSuggestionUseCase,
) : BaseViewModel<SmsInboxIntent, SmsInboxUiState, SmsInboxEffect>() {

    override fun initialState() = SmsInboxUiState()

    init {
        observeBasedOnSearch()
        viewModelScope.launch {
            val ready = aiParser.availability() == AiParserAvailability.Ready
            setState { copy(aiAvailable = ready) }
        }
    }

    override fun onIntent(intent: SmsInboxIntent) {
        when (intent) {
            is SmsInboxIntent.SearchChanged ->
                setState { copy(search = intent.query) }
            is SmsInboxIntent.DraftChanged ->
                setState { copy(draftBody = intent.body, draftPreview = previewOf(intent.body)) }
            SmsInboxIntent.IngestDraft -> ingestDraft()
            is SmsInboxIntent.Delete ->
                viewModelScope.launch { deleteSms(intent.id) }
            is SmsInboxIntent.SuggestParse -> suggestParse(intent.id)
            is SmsInboxIntent.ConfirmSuggestion -> confirmSuggestion(intent.id)
            is SmsInboxIntent.DismissSuggestion ->
                viewModelScope.launch { dismissAiSuggestion(intent.id) }
            is SmsInboxIntent.PermissionChanged ->
                setState { copy(autoImportGranted = intent.granted) }
            SmsInboxIntent.ConsumeEffect -> clearEffect()
        }
    }

    /** Parse the draft for a live preview (brand + amount) without persisting; null if it doesn't
     *  match a known bank-SMS template or is incomplete. */
    private fun previewOf(body: String): ParsedSmsData? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val template = detector.detect(trimmed) ?: return null
        return parser.parse(trimmed, template).takeIf { it.isComplete }
    }

    private fun ingestDraft() {
        val body = state.value.draftBody.trim()
        if (body.isEmpty() || state.value.isProcessing) return
        setState { copy(isProcessing = true) }
        viewModelScope.launch {
            when (val result = capture(body, CaptureSource.MANUAL_PASTE)) {
                is DomainResult.Success -> {
                    setState { copy(draftBody = "", draftPreview = null, isProcessing = false) }
                    when (val outcome = result.value) {
                        is CaptureResult.Imported ->
                            sendEffect(SmsInboxEffect.TransactionCreated(amount = outcome.transaction.amount))
                        // No template matched, but the text is safely in the inbox. With AI
                        // ready, drive a confirm-first suggestion on that row (spinner while it
                        // thinks); without it, say why nothing parsed — the row still offers
                        // Create template. Ask the parser directly, not state.aiAvailable —
                        // the init coroutine populating that flag races an eager first paste,
                        // and losing the race must not cost the suggestion.
                        is CaptureResult.StoredUnparsed ->
                            if (aiParser.availability() == AiParserAvailability.Ready) {
                                suggestPastedEntry(outcome.messageId)
                            } else {
                                sendEffect(SmsInboxEffect.ParseFailed("No SMS template matched"))
                            }
                    }
                }
                is DomainResult.Failure -> {
                    sendEffect(SmsInboxEffect.ParseFailed(reasonFor(result.error)))
                    setState { copy(isProcessing = false) }
                }
            }
        }
    }

    private suspend fun suggestPastedEntry(id: SmsMessageId) {
        setState { copy(suggestingIds = suggestingIds + id) }
        val result = suggestAiParse(id, source = "paste", freeText = true)
        setState { copy(suggestingIds = suggestingIds - id) }
        if (result is DomainResult.Failure) sendEffect(SmsInboxEffect.AiParseFailed)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeBasedOnSearch() {
        state
            .map { it.search }
            .distinctUntilChanged()
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .flatMapLatest { query -> observeMessages(query.ifBlank { null }) }
            .map { list -> list.map(::toRow) }
            .onEach { rows -> setState { copy(rows = rows, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private fun suggestParse(id: SmsMessageId) {
        if (id in state.value.suggestingIds) return
        setState { copy(suggestingIds = suggestingIds + id) }
        viewModelScope.launch {
            val result = suggestAiParse(id, source = "manual")
            setState { copy(suggestingIds = suggestingIds - id) }
            // The stored suggestion arrives reactively through observeMessages; only failure
            // needs an explicit signal.
            if (result is DomainResult.Failure) sendEffect(SmsInboxEffect.AiParseFailed)
        }
    }

    private fun confirmSuggestion(id: SmsMessageId) {
        viewModelScope.launch {
            when (val result = confirmAiSuggestion(id)) {
                is DomainResult.Success ->
                    sendEffect(SmsInboxEffect.TransactionCreated(amount = result.value.amount))
                is DomainResult.Failure ->
                    sendEffect(SmsInboxEffect.ParseFailed(reasonFor(result.error)))
            }
        }
    }

    private fun toRow(msg: SmsMessage): SmsInboxRow = SmsInboxRow(
        id = msg.id,
        body = msg.body,
        receivedAt = msg.receivedAt,
        parsedBrand = msg.parsed?.brandName,
        parsedAmount = msg.parsed?.amount,
        isLinked = msg.isLinked,
        suggestedBrand = msg.suggested?.brandName,
        suggestedAmount = msg.suggested?.amount,
        suggestedOccurredAt = msg.suggested?.occurredAt,
    )

    private fun reasonFor(error: DomainError): String = when (error) {
        is DomainError.ValidationFailed -> error.message
        is DomainError.NotFound -> "Required record missing: ${error.entity}"
        is DomainError.Conflict -> error.message
        is DomainError.Unexpected -> error.cause.message ?: "Unexpected error"
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
