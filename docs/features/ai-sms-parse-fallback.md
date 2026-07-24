# On-device AI SMS-parse fallback

## Requirement
First PR of the "AI capture & auto-categorization" roadmap item: when no regex template
matches a bank SMS, an on-device model parses it — as a confirm-first suggestion, using the
platform-native model pair.

## Spec
- **Goal:** unmatched bank SMS stop being dead-end Unparsed cards; the AI proposes
  brand/amount/date and the user confirms with one tap.
- **Models:** **Gemini Nano** via the ML Kit GenAI Prompt API
  (`com.google.mlkit:genai-prompt:1.0.0-beta4`, OS-managed via AICore) on Android;
  **Apple Foundation Models** (Apple Intelligence, iOS 26+, `@Generable` guided
  generation) on iOS. Nothing bundled in the app; inference never leaves the device.
- **Trust UX (confirm-first):** AI output is stored as a *suggestion* on the message
  (`suggested*` columns, schema v4) and shown with an "AI suggestion" badge + Confirm /
  Dismiss on the SMS inbox row. The row's status chip stays **Unparsed** until confirmed;
  AI never creates a transaction on its own.
- **Where it fires:** automatically after a failed capture (fire-and-forget on the app
  scope — the capture verdict, e.g. the Shortcuts dialog, stays regex-only and never
  waits on inference), and manually via a "Parse with AI" button on Unparsed rows.
- **Degradation:** no Gemini Nano / no Apple Intelligence / iOS < 26 → the port reports
  `Unavailable`, every AI affordance is hidden, behavior is identical to before. A
  DOWNLOADABLE Nano model triggers a background download and stays unavailable for the
  session.
- **Out of scope (follow-ups):** auto-categorization; template synthesis; provenance
  column; suggestion notifications; partial suggestions; batch parse; Nano
  download-progress UX; backing up suggestions.

## Design
- **Port:** `AiSmsParser` (`feature/sms/domain/ai/`) — `availability()` + `parse(body)`
  returning primitive-typed `AiParsedSms?` (crosses the Swift bridge cleanly; null = model
  failure, never throws). Prompt + output schema live platform-side (Foundation Models
  defines its schema in Swift `@Generable`); the shared **`sanitize`** step in
  `SuggestAiParseUseCase` owns acceptance: brand + positive amount required, currency
  defaults to the app currency, missing/far-future dates fall back to the SMS arrival
  time. `parseAiIsoDate` (shared) accepts instant / local date-time / bare date.
- **Use cases:** `SuggestAiParseUseCase` (guards → parse → sanitize → store suggestion),
  `ConfirmAiSuggestionUseCase` (promotes via `SmsTransactionProcessor.commit`, the
  extracted trusted pipeline tail — one write links the transaction, records the parse,
  keeps the suggestion as the linked row's "AI parsed" provenance badge; then budget
  re-check), `DismissAiSuggestionUseCase`. Note: provenance isn't backed up (suggested*
  columns stay out of the backup format), so it's lost on restore — accepted.
  `IngestSmsUseCase` launches the auto suggest on `ValidationFailed`.
- **Data:** `SmsMessage.suggested: ParsedSmsData?`; `sms_messages` gains 4 nullable
  `suggested*` columns; `SCHEMA_VERSION` 3→4 with additive `AutoMigration(3,4)`. Backup
  format unchanged — suggestions are ephemeral and restore as null.
- **Android adapter:** `GeminiNanoSmsParser` (androidApp) — suspend-native Prompt API
  (`Generation.getClient()`, `checkStatus()`, `generateContent`), strict-JSON prompt
  (bilingual-aware), fence-stripping defensive decode via `JsonElement` (no codegen).
- **iOS adapter:** `AiSmsBridge` (iosMain seam, has-flag primitives, nil = failure) ←
  `FoundationModelsSmsParser.swift` (everything behind `#available(iOS 26, *)` +
  `canImport`; `LanguageModelSession.respond(to:generating:)` with a `@Generable
  ParsedBankSms`) → `IosAiSmsParser`. Injected via
  `startIosApp(gcmCipher, aiSmsBridge)` — the `CryptoKitGcmCipher` pattern.
- **Analytics (PII-free):** `ai_parse_attempted/succeeded/failed` (source auto|manual,
  amount bucket, reason enum), `ai_suggestion_confirmed/dismissed`.
- **Test strategy:** commonTest suites for suggest (guards, sanitize rules, analytics),
  confirm (single-write link + clear), dismiss, ingest auto-fire scheduling, ViewModel
  (availability, spinner set, effects) with `FakeAiSmsParser` in testutil. The 3→4
  migration is a spec-less additive AutoMigration verified by Room at compile time.

## Brand-aware parsing (follow-up PR)

The parser knows the user's existing brands, two layers deep:
- **Prompt-side:** `SuggestAiParseUseCase` passes the top-50 brand names by transaction
  count (`BrandRepository.namesByUsage`) into `AiSmsParser.parse(body, knownBrands)`;
  both adapters instruct the model to answer with an existing brand's exact name when the
  merchant matches it — typos, casing, and abbreviations included. On-device inference is
  what makes feeding user data into the prompt acceptable.
- **Deterministic backstop:** `canonicalize` in the sanitize step snaps the model's
  merchant string to an existing brand — case-insensitive exact, then substring either way
  (mirroring `findByNameLike`'s link-time containment rule, so the suggestion shows
  exactly what Confirm will link), then Levenshtein ≤ 2 for names ≥ 4 chars. Usage order
  breaks ties toward the most-used brand.

This also pre-wires auto-categorization: a suggestion landing on an existing brand
inherits that brand's category through the normal commit path, no extra model call.

## Risks
Arabic/bilingual SMS quality (confirm-first mitigates; measure via analytics before
building categorization on top); initial device reach is small (Nano flagships; iPhone
15 Pro+ on iOS 26); the Prompt API is beta — pin `1.0.0-beta4` and re-verify on upgrade;
debit/credit sign ambiguity inherits the template convention (user confirms).
