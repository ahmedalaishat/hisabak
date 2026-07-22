package com.hisabak.feature.sms.presentation.inbox

import com.hisabak.core.common.Money
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessageId
import java.time.Instant

data class SmsInboxRow(
    val id: SmsMessageId,
    val body: String,
    val receivedAt: Instant,
    val parsedBrand: String?,
    val parsedAmount: Money?,
    val isLinked: Boolean,
)

data class SmsInboxUiState(
    val rows: List<SmsInboxRow> = emptyList(),
    val search: String = "",
    val draftBody: String = "",
    // Live parse of the current draft, so the user sees what will be imported before tapping.
    val draftPreview: ParsedSmsData? = null,
    val isProcessing: Boolean = false,
    val isLoading: Boolean = true,
    val autoImportGranted: Boolean = false,
) : ViewState

sealed interface SmsInboxIntent : ViewIntent {
    data class SearchChanged(val query: String) : SmsInboxIntent
    data class DraftChanged(val body: String) : SmsInboxIntent
    data object IngestDraft : SmsInboxIntent
    data class Delete(val id: SmsMessageId) : SmsInboxIntent
    data class PermissionChanged(val granted: Boolean) : SmsInboxIntent
    data object ConsumeEffect : SmsInboxIntent
}

sealed interface SmsInboxEffect : ViewEffect {
    data class ParseFailed(val reason: String) : SmsInboxEffect
    data class TransactionCreated(val amount: Money) : SmsInboxEffect
}
