package com.hisabak.feature.sms.presentation.inbox

import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsTemplateDetector
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
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
) : BaseViewModel<SmsInboxIntent, SmsInboxUiState, SmsInboxEffect>() {

    override fun initialState() = SmsInboxUiState()

    init {
        observeBasedOnSearch()
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
                    sendEffect(SmsInboxEffect.TransactionCreated(amount = result.value.amount))
                    setState { copy(draftBody = "", draftPreview = null, isProcessing = false) }
                }
                is DomainResult.Failure -> {
                    sendEffect(SmsInboxEffect.ParseFailed(reasonFor(result.error)))
                    setState { copy(isProcessing = false) }
                }
            }
        }
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

    private fun toRow(msg: SmsMessage): SmsInboxRow = SmsInboxRow(
        id = msg.id,
        body = msg.body,
        receivedAt = msg.receivedAt,
        parsedBrand = msg.parsed?.brandName,
        parsedAmount = msg.parsed?.amount,
        isLinked = msg.isLinked,
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
