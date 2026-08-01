# SMS template manager

## Requirement
SMS template manager: move SMS parse templates from the hardcoded DefaultSmsTemplates list into
Room (seeded from the current 10 defaults; enabled flag; user-created vs default flag), with the
detector observing the table. New Settings → SMS parsing screen: list templates (defaults +
user), add/edit/disable/delete, define-by-sample editor — paste a sample message, the app
pre-detects amount/brand/date spans which the user confirms/adjusts, and the template pattern is
derived from the tagged sample (no placeholder syntax exposed; pattern shown read-only for
reference). Safety guardrails: runtime match only counts if the captured amount parses as a
number (else try next template); matching is specificity-ranked (templates with more literal
anchor text win; user templates tie-break over defaults) instead of list order; save-time
preview runs the draft against stored SMS messages and warns how many other messages it matches
(including ones already parsed differently) with a minimum-anchor requirement. Templates are
included in the Drive backup envelope (schema version bump + migration). SMS Inbox: unparsed
messages get a 'create template from this message' affordance deep-linking into the editor
pre-filled with that message as the sample. Default templates can be disabled but not
edited/deleted.

## Spec

- **Goal:** users can teach the app their bank's SMS formats without a code change or an app
  release — by example, not by syntax — while a badly-made template can never poison parsing of
  other messages.
- **In scope:**
  - `sms_templates` Room table seeded once from the 10 Hisabi defaults; enabled + default
    flags; user templates also keep the sample message they were defined from (so re-editing
    reconstructs the tagged spans by matching the pattern against the stored sample).
  - The capture pipeline and the inbox draft preview read templates through a detector that
    observes the table — add/disable/delete take effect immediately, no restart.
  - **Specificity-ranked matching**: enabled templates are tried most-literal-anchor-first
    (placeholders excluded, whitespace not counted), user templates before defaults on ties.
    List order no longer matters.
  - **Runtime amount guard**: a template match whose `{amount}` capture doesn't parse as a
    positive number is skipped and the next template is tried.
  - Settings → SMS parsing: template list (pattern preview, default/user badge, enabled
    switch; delete for user templates only), Add button, and per-row edit for user templates.
  - Define-by-sample editor: sample text (pasted, or preloaded from an inbox message),
    auto-suggested spans, tap-to-tag tokens with a role selector (Amount / Brand / Date /
    Time / Skip), live read-only derived pattern, live extraction preview on the sample, and
    a save-time match preview ("also matches N stored messages, M of them parsed
    differently").
  - Save requires: an Amount tag whose text parses as a number, and ≥ 10 literal
    non-whitespace anchor characters outside the tagged spans.
  - Backup: templates ride in the Drive envelope (new record list; restore replaces the
    table; older backups without templates restore fine and the defaults re-seed).
  - SMS Inbox: unparsed rows get a "Create template" action that opens the editor with that
    message as the sample.
- **Out of scope:** sender-scoped templates (needs storing the SMS sender — follow-up),
  AI-drafted templates from confirmed suggestions (follow-up), editing/deleting default
  templates, re-parsing existing inbox messages when a template is added (the paste box +
  future capture cover the immediate need), template names/labels.
- **Acceptance criteria:**
  1. On first run (and after restoring a pre-template backup) the 10 defaults are present and
     enabled; parsing behaves as before.
  2. A user template created from the Tabby sample "You spent AED 35.00 at HARDEES-WTC MALL.
     Your available Tabby Card limit is now AED 8,342.27." parses that message's amount/brand,
     and a later "You spent AED 6.50 at YANGO. …" message parses via the same template.
  3. A degenerate draft (e.g. tagging "100" and "noon" in "100 at noon") is rejected at save
     for insufficient literal anchor.
  4. A more specific template beats a less specific one regardless of creation order; a user
     template beats a default of equal anchor length.
  5. A template whose amount capture lands on non-numeric text falls through to the next
     template instead of producing a junk parse.
  6. Disabling a template excludes it from matching immediately; deleting is only possible for
     user templates.
  7. Backup → restore round-trips user templates and enabled flags.
  8. The save-time preview reports how many stored messages the draft matches and how many of
     those already parse differently (template or confirmed AI).
- **Edge cases:**
  - Span tags are trimmed of surrounding whitespace/punctuation so "MALL." tags as "MALL".
  - Untagged number-like tokens (balances) are auto-suggested as Skip so they don't become
    literals that break future matches; the user can untag.
  - The pattern derivation collapses each tagged span to one placeholder even when the user
    tapped multiple adjacent tokens.
  - An empty template table (fresh install, wiped restore) re-seeds defaults on first read.
  - Editing a user template re-runs its pattern against the stored sample to rebuild spans;
    if the sample no longer matches (shouldn't happen), the editor starts from the sample
    with fresh suggestions.
- **Assumptions:**
  - Roles are Amount, Brand, Date, Time, Skip — matching the placeholder vocabulary the
    parser already understands (`{card}`/`{account}` in defaults just become extra captured
    fields; user templates don't need them, Skip covers them).
  - The minimum literal anchor is 10 non-whitespace characters — enough to kill "{amount} at
    {brand}" while every real bank format passes easily.
  - "Parsed differently" in the preview means the stored message is linked or has a parse/AI
    suggestion whose amount differs from what the draft extracts.
  - The editor is also reachable for *viewing* a default template (read-only pattern, no
    save), keeping one screen for both.

## Design

- **Domain/model changes:**
  - `SmsParserTemplate(id, pattern, sampleBody?, isDefault, enabled, createdAt)` +
    `SmsTemplateRepository` (observe/upsert/delete/seedIfEmpty) in `feature/sms/domain/`.
  - `rankTemplates()` (pure specificity ordering) + amount validation inside
    `RegexSmsTemplateDetector`; new `ObservingSmsTemplateDetector` wraps a compiled snapshot
    refreshed from the repository flow on `APPLICATION_SCOPE` (detect stays synchronous for
    the per-keystroke inbox preview).
  - `TemplateSample.kt`: `TagRole`, `TokenSpan`, `deriveTemplatePattern(sample, spans)`,
    `suggestSpans(sample)`, `reconstructSpans(pattern, sample)` — all pure.
  - Use cases: `ObserveSmsTemplates`, `SaveSmsTemplate` (validation), `DeleteSmsTemplate`
    (defaults protected), `SetSmsTemplateEnabled`, `PreviewSmsTemplate` (match/conflict
    counts over `SmsRepository.observeAll().first()`).
  - Analytics: `sms_template_created` / `sms_template_deleted` / `sms_template_toggled`
    (no message text, ever).
- **Files to add/change:** entity/DAO/mapper in `feature/sms/data/local/`,
  `RoomSmsTemplateRepository`, `HisabakDatabase` v5 + AutoMigration(4→5) + committed schema,
  backup records/repository/module, `SmsTemplatesScreen/ViewModel` +
  `SmsTemplateEditScreen/ViewModel` in `feature/sms/presentation/templates/`, NavKeys +
  HisabakRoot (full-screen children, `PlatformSlots.settings` gains `onOpenSmsTemplates`;
  inbox slot gains a create-template callback), SettingsScreen row, SmsInboxScreen action,
  strings en+ar, testutil fakes.
- **Test strategy:** pure-function tests (derive/suggest/reconstruct/rank), detector tests
  (ranking, amount fall-through, disabled exclusion, live re-ranking via fake repo flow),
  save/delete/preview use-case tests, both ViewModels, codec round-trip with templates, and
  the acceptance samples above as literal test data.
- **Trade-offs / decisions:** detect stays a synchronous fun interface (an atomic compiled
  snapshot beats making the whole capture path re-entrant); ranking is computed at compile
  time of the snapshot, not per message; the stored sample makes user templates re-editable
  without reverse-parsing patterns; defaults keep fixed ids (`default-0…9`) so seeding is
  idempotent and restore-then-seed can't duplicate them.
- **Follow-ups from device testing:** saving a template made from an inbox message re-imports
  that message on the spot (detection reads the template table directly — the observing
  detector's snapshot refreshes asynchronously and racing it loses); creating an exact
  duplicate is surfaced in the editor rather than silently deduped — a disabled twin flips the
  action to "Enable template (& save transaction)", an enabled twin disables save; ranking
  ties break deterministically (createdAt, then id) so enable-toggles don't reshuffle the
  list.
- **Hardened in review:**
  - The repository re-seeds on *live* empty emissions, not just first collection — restoring a
    pre-template backup wipes the table under the detector's long-lived collection, and
    parsing must come back with the defaults instead of going dark until restart.
  - A pattern ending in a placeholder compiles its final group greedily (`(.+)`) in both the
    detector and the editor preview — a lazy trailing group matches empty at runtime while
    `matchEntire` shows it captured, so the preview would lie.
  - `BackupData.totalRecords` excludes templates: the seeded defaults always exist, and
    counting them would defeat the "nothing to back up" gate on an empty install.
  - Editing a user template preserves its `enabled` flag and creation time (a disabled
    template must not sneak back on; `createdAt` is a ranking tie-break input).
  - Preview conflict math goes through `Money.parseMajor`, not `(double × 100).toLong()`
    (which truncates 0.29 to 28 minor units and would flag identical amounts).
  - Known cold-start nuance: the detector's initial snapshot is the shipped defaults
    (unranked, ignoring disables) until the first DB emission lands — a deliberate
    better-than-nothing fallback for a capture racing app start.
