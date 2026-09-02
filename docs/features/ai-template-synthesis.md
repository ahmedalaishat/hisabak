# Learn-once template synthesis

## Requirement
A confirmed AI parse should teach the regex engine, so the next message of the same bank format
parses offline instead of needing the model again.

## Spec
- **Goal:** turn a one-off AI parse into a durable parsing rule. The payoff is **reach** as much
  as cost — Gemini Nano is flagship-only and Apple Foundation Models needs iOS 26 on an
  iPhone 15 Pro+, but a template learned on one device works on every device, and a restored
  backup carries it across.
- **Where it fires:** the candidate pattern is derived at **suggest** time (it needs the model's
  raw merchant string, which `sanitize` replaces with a canonical brand) and stored on the
  message; it is installed as a template only when the user **confirms** the suggestion. Every
  capture source qualifies — share sheet, Shortcut, Android broadcast, and paste.
- **Trust UX:** confirming the parse is the consent. The inbox snackbar says a rule was learned
  and offers **Undo**; the template shows a "Learned" badge in Settings → SMS parsing and is
  editable and deletable exactly like a hand-made one.
- **Never at the user's expense:** synthesis failure cannot fail the confirm. A rejected rule
  returns success-with-null — the transaction is the outcome the user asked for.
- **Pasted text included, notes filtered by the gate not the source.** The inbox takes pasted bank
  SMS and typed notes through one field, and on iOS — no SMS API — pasting is how bank messages
  arrive, so excluding the free-text path would leave the platform unable to learn at all. A note
  like "lunch 45 yesterday" is stopped instead by the anchor minimum, which is the same rule that
  stops an over-generic hand-made template.
- **Out of scope (follow-ups):** the server-side path (a real model tagging the spans for devices
  with no on-device AI, plus the opt-in consent flow and privacy-policy change it needs);
  re-teaching from a corrected template; sharing learned templates between users.

## Design
- **Pure core:** `deriveAiSpans(body, rawBrand, amountMinor)` in
  `feature/sms/domain/template/AiTemplateSynthesis.kt`. It starts from `suggestSpans` for the
  structural work — dates, times, and crucially the *other* number tokens, which must become
  `{ignore}` or the pattern would be pinned to one message's balance — then overrides the amount
  and brand spans with what the model actually returned. Heuristic amount/brand guesses that lose
  are **demoted to Skip, not dropped**: `suggestSpans` only ever tags variable text, so leaving a
  rejected guess untagged would bake that value into the pattern as a literal.
  - Amount: the token whose value equals `amountMinor`, preferring one with a currency marker
    before it (`AED 50.00 … balance 50.00` would otherwise pin to the balance). String math, not
    `(x * 100).toLong()`, which truncates 0.29 to 28.
  - Brand: case-insensitive `indexOf` of the **raw** model string. Not found → no template; the
    model cleaned the name past recognition and a wrong rule is worse than none.
- **Use case:** `SynthesizeTemplateUseCase` gates on `SaveSmsTemplateUseCase.validate` (the same
  10-char anchor + numeric-amount rule hand-made templates pass), exact-pattern dedupe, and a
  `PreviewSmsTemplateUseCase` conflict check against stored messages. Every rejection logs its
  reason. `rankTemplates` then handles precedence for free — bank boilerplate gives a learned
  pattern a long literal anchor.
- **Data:** `SmsMessage.suggestedPattern` (`sms_messages`) carries the candidate;
  `SmsParserTemplate.derivedByAi` (`sms_templates`) marks the origin. `SCHEMA_VERSION` 5→6 with
  an additive `AutoMigration(5, 6)`. `derivedByAi` rides the backup envelope with a `false`
  default, so pre-v6 backups restore as hand-made.
- **Analytics (PII-free):** `sms_template_synthesized`, `sms_template_synthesis_skipped(reason)`
  — `no_amount_span` / `no_brand_span` / `weak_anchor` / `duplicate` / `conflict` — and
  `sms_template_synthesis_undone`. The skip mix is the measurement that decides whether a
  server-side tagger is worth building: lots of `no_brand_span` means the local locator is the
  bottleneck, not the idea.
- **Test strategy:** commonTest suites for the locator (separators, decimal-less amounts,
  currency disambiguation, casing, the absent-amount and cleaned-merchant rejections), the
  round-trip property (a pattern learned from one message parses a *second* message of the same
  shape), the use-case gate, the free-text guard, confirm-side installation, and the
  backup default. `AiTemplateSynthesisTest` is the one that matters — it is the property the
  whole feature rests on.

## Risks
The locator depends on the model echoing a merchant substring that really appears in the body;
Gemini Nano often normalizes it, which shows up as `no_brand_span` skips rather than bad rules.
A learned rule is only as good as the one message it came from — the conflict check catches
over-generic patterns against messages already captured, but not against formats not yet seen;
Undo and ordinary template editing are the recovery path.
