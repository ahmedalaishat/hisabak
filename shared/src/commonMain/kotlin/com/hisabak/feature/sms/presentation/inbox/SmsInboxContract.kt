package com.hisabak.feature.sms.presentation.inbox

import com.hisabak.core.common.Money
import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.transaction.domain.TransactionId
import kotlin.time.Instant

data class SmsInboxRow(
    val id: SmsMessageId,
    val body: String,
    val receivedAt: Instant,
    val parsedBrand: String?,
    val parsedAmount: Money?,
    val isLinked: Boolean,
    /** The created transaction, when linked — lets AI-parsed rows offer "Review transaction". */
    val transactionId: TransactionId? = null,
    val suggestedBrand: String? = null,
    val suggestedAmount: Money? = null,
    val suggestedOccurredAt: Instant? = null,
    /** Saved by the app rather than approved by the user — the row says which. */
    val autoConfirmed: Boolean = false,
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
    // On-device AI parse fallback: false (the default) hides every AI affordance.
    val aiAvailable: Boolean = false,
    val suggestingIds: Set<SmsMessageId> = emptySet(),
    /** The one offer worth making right now, or null. At most one is ever shown. */
    val prompt: InboxPrompt? = null,
) : ViewState

/**
 * A capability the user has not turned on, offered where it would help rather than only buried in
 * Settings. Dismissing hides it until the next launch; "don't ask again" hides it for good and
 * leaves Settings as the way in.
 */
enum class InboxPrompt { OnlineParsing, AutoConfirm }

sealed interface SmsInboxIntent : ViewIntent {
    data class SearchChanged(val query: String) : SmsInboxIntent
    data class DraftChanged(val body: String) : SmsInboxIntent
    data object IngestDraft : SmsInboxIntent
    /** Re-import a parsed-but-unlinked row (its transaction was deleted). */
    data class ImportParsed(val id: SmsMessageId) : SmsInboxIntent
    data class Delete(val id: SmsMessageId) : SmsInboxIntent
    data class SuggestParse(val id: SmsMessageId) : SmsInboxIntent
    data class ConfirmSuggestion(val id: SmsMessageId) : SmsInboxIntent
    data class DismissSuggestion(val id: SmsMessageId) : SmsInboxIntent
    /** Remove a template that confirming a suggestion just installed. */
    data class UndoLearnedTemplate(val id: SmsTemplateId) : SmsInboxIntent
    data class AcceptPrompt(val prompt: InboxPrompt) : SmsInboxIntent
    /** Not now — the offer returns on the next launch. */
    data class DismissPrompt(val prompt: InboxPrompt) : SmsInboxIntent
    /** Don't ask again — never offered here again; Settings still has the switch. */
    data class SuppressPrompt(val prompt: InboxPrompt) : SmsInboxIntent
    data class PermissionChanged(val granted: Boolean) : SmsInboxIntent
    data object ConsumeEffect : SmsInboxIntent
}

sealed interface SmsInboxEffect : ViewEffect {
    data class ParseFailed(val reason: String) : SmsInboxEffect
    /** [learnedTemplateId] is set when the confirm also installed a template, which the snackbar offers to undo. */
    data class TransactionCreated(val amount: Money, val learnedTemplateId: SmsTemplateId? = null) : SmsInboxEffect
    data object AiParseFailed : SmsInboxEffect
}
