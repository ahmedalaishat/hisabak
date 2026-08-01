# Unified capture in the SMS inbox

## Requirement
Unify capture in the SMS inbox and remove the add-sheet smart entry. The inbox paste box
accepts both bank SMS and free-text notes: template matches keep today's instant import;
anything unmatched is stored as an inbox entry and handed to the on-device AI for a
confirm-first suggestion instead of showing a dead-end 'couldn't parse' error — the row shows
an 'AI reading' spinner until the suggestion (or a failure snackbar) arrives. Manual input
routes through the free-text AI prompt (today's date context, relative-date resolution,
shorthand like 15k, plausibility window up to a year back and never the future — better for
pasted backlogs too), while broadcast/Shortcut SMS keep the strict SMS prompt. Ingest reports
'stored but unparsed' as a distinct outcome (not a failure) so the ViewModel can drive the AI
suggestion and spinner precisely. The transaction sheet's 'describe it' smart field, its
intents/state, the create-brand chip, ParseSmartInputUseCase, and the smart_parse_* analytics
are removed — the AI suggestion path logs ai_parse_* with a 'paste' source instead. On devices
without on-device AI, unmatched input keeps today's clear parse-failed error.

## Spec

- **Goal:** one capture surface. The SMS tab takes anything — auto-captured SMS, pasted bank
  messages, typed notes — and every AI-assisted path is confirm-first in the inbox. The add
  sheet goes back to being a plain manual form (per user feedback: the silent sheet auto-fill
  felt wrong; the suggestion-card flow is the one that earns trust).
- **In scope:**
  - `IngestSmsUseCase`/`CaptureTransactionUseCase` return `DomainResult<CaptureResult>`
    (`Imported(transaction)` | `StoredUnparsed(messageId)`). Only `MANUAL_PASTE` yields
    `StoredUnparsed`; background sources (broadcast/share/process-text/Shortcut) keep today's
    contract — failure result + detached auto-AI — so the platform adapters don't change.
  - Inbox paste flow: template match → import as today. No match + AI ready → draft clears,
    the stored row shows the existing suggestion spinner, and the ViewModel drives
    `SuggestAiParseUseCase(id, source = "paste", freeText = true)`; the suggestion card
    appears on completion, `AiParseFailed` snackbar on model failure. No match + AI
    unavailable → today's parse-failed snackbar, but the draft clears (the message *is*
    stored — keeping the text invited double-pastes).
  - `SuggestAiParseUseCase` gains a `freeText` mode: `parseFreeText` (today's-date context)
    instead of the SMS prompt, and a plausibility window of a year back / a day forward
    (pasted backlogs are legitimately old; the 7-day window stays for live SMS).
  - Removal of sheet smart entry: the "describe it" field, smart state/intents, the
    create-brand chip, `ParseSmartInputUseCase` (+ test), `smart_parse_*` analytics events,
    and the `transaction_smart_*` strings. `AiSmsParser.parseFreeText` and both platform
    prompts stay — the inbox free-text mode is their consumer now.
- **Out of scope:** the paste card's live preview (stays template-only — per-keystroke AI is
  not viable); storing a source/origin on messages; changing background-capture UX.
- **Acceptance criteria:**
  1. Pasting a template-matching bank SMS imports instantly (unchanged).
  2. Pasting/typing unmatched text with AI ready: no error snackbar; the entry appears with
     the reading spinner; a suggestion card follows (confirm-first, never auto-created).
  3. The suggestion for typed notes resolves relative dates and accepts dates up to a year
     back; future dates and >1y fall back to the paste time.
  4. Broadcast/Shortcut captures behave byte-for-byte as before (failure + detached AI, same
     adapter-visible results).
  5. The add-transaction sheet has no describe-it field on any device; its tests reflect the
     manual-only contract.
  6. `ai_parse_attempted/succeeded/failed` carry source "paste" for this path; no smart_parse_*
     events exist.
  7. AI-unavailable devices get the parse-failed snackbar and a cleared draft; the message is
     in the inbox for template-teaching.
- **Edge cases:**
  - Model returns nothing for a note → `AiParseFailed` snackbar; the row stays unparsed with
    the Create-template action available (and the anchor guard will reject nonsense samples).
  - Double-tap Import while processing is already guarded (`isProcessing`).
  - A pasted message that matches no template but *is* a bank SMS still benefits: the
    free-text prompt extracts the same fields, and Create template remains the durable fix.
- **Assumptions:**
  - The paste card's placeholder copy is updated to invite notes ("Paste a bank message or
    type e.g. \"lunch 45 yesterday\"") — en+ar.
  - `StoredUnparsed` logs the existing `sms_parse_failed` analytics reason (continuity), and
    the notification/budget side effects only run for `Imported`.

## Design

- **Domain/model changes:** `CaptureResult` sealed interface (feature/sms/domain/capture);
  `IngestSmsUseCase(body, source, receivedAt)` branches the unmatched outcome on source;
  `SuggestAiParseUseCase(messageId, source, freeText)` picks the port method and window;
  `todayContext()` moves there from the deleted `ParseSmartInputUseCase`.
- **Files to add/change:** the two capture use cases + `CaptureResult`; `SuggestAiParseUseCase`;
  `SmsInboxViewModel` (ingest outcome handling, VM-driven paste suggestions);
  `SmsInboxScreen` (placeholder copy); deletions across
  `feature/transaction/presentation/edit/*` smart pieces, `ParseSmartInputUseCase`(+test),
  `AnalyticsEvent`, strings, `TransactionModule`; test updates in
  Ingest/Capture/SuggestAiParse/SmsInboxViewModel/TransactionEditViewModel suites.
- **Test strategy:** each acceptance criterion has a direct test; the free-text window and
  prompt-selection are asserted via `FakeAiSmsParser.parsedFreeTexts`/`lastTodayIso`.
- **Trade-offs / decisions:** `StoredUnparsed` is deliberately paste-only — extending it to
  background sources would change three adapters and the Shortcut success toast for no UX
  gain, since those paths have no screen to drive a spinner on. The live preview stays
  deterministic (templates) so typing feels instant; AI cost is paid once, on Import.
