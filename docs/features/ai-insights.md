# AI insights: review, narrative, and a bounded "Ask"

## Requirement
Let the user discuss their finances with the AI — insights, suggestions, optimization — without
it becoming an expensive, off-brand general chatbot, and without shipping their whole financial
picture off the phone by default.

## Spec
- **Goal:** the "so what?" layer over data the app already collects. The dashboard shows *what*
  happened; this says *what changed, why it matters, and what to do*.
- **Three layers, built in this order.** The order is the design: each layer is a complete
  feature on its own, and the later ones are enhancements the user opts into rather than the price
  of admission.
  1. **Review (deterministic, no AI).** Insights that are arithmetic: largest expense category,
     largest change vs the prior period, categories over/under their limit, an unusually large
     transaction against the brand's history, savings-rate trend, uncategorized spend. Computed
     on-device from the same aggregates the dashboard builds. Free, private, offline, **reaches
     every device** — including the majority with no model and anyone who never opts in.
  2. **Narrative (AI, opt-in).** The model explains and proposes: "Dining is up 40%, driven by
     three weekend delivery orders; a 600 cap would bring it under your limit." Generated **once
     per period**, cached, rendered as **cards** — not a chat window.
  3. **Ask (AI, opt-in, later).** "Ask about this" from an insight card, seeded with suggested
     questions as chips; free text allowed but capped. The entry point is an insight, never a
     blank box: a blank box is where nearly all the cost and abuse live.
- **What leaves the phone — aggregates, never rows.** The `InsightsSummary`: totals by category
  with prior-period deltas, top-N brands by spend, limit utilization, savings rate, uncategorized
  total, the period. A few KB, deterministic, enough for every insight above. **Notes never
  leave** — the most sensitive field and the obvious prompt-injection vector. Transaction rows
  never leave; if a question needs one ("what was that 1,200 on the 9th?") the answer comes from
  the local deterministic layer, not the model.
- **Consent per request, not a switch** (decided 2026-09-03, after building it as a switch first).
  Parsing has a stored opt-in because it must run unattended when a message arrives; the narrative
  has no such need — the user is on the screen. So there is no `insightsEnabled` setting: the send
  *is* the tap on **Explain with AI**, and **"See what's shared"** sits beside it showing the exact
  summary that tap would send. Nothing is ever sent by the app on its own, and there is nothing to
  revoke. `docs/privacy.html` gets its own section.
- **Read-only, always.** The model never writes. An actionable suggestion ("set a 600 dining
  limit") renders as a **confirm-first chip** that opens the existing editor prefilled — the AI
  parse and category-suggestion pattern. No exception, even when it would be convenient.
- **Structured output, validated like `sanitize`.** The model returns JSON insights — type,
  category/brand reference, delta, a one-line explanation, an optional suggestion — not prose.
  The app renders them, so they stay on-brand and can deep-link (tap → filtered transactions);
  a reference to a category the user doesn't have is dropped, not shown.
- **Limits are layered; no single cap is enough.**
  | Layer | Rule | Why |
  |---|---|---|
  | per-install quota (**server**) | anonymous `installId` UUID as a header; N asks/day | **fairness, not enforcement** — the ID is client-asserted, so anyone with the bearer token can mint a new one per call; it exists so honest phones get a fair, visible allowance |
  | visible cap (**client**) | "3 of 10 questions left today" | a counter reads as fair; a silent 429 reads as broken |
  | existing tiers | `Limiter` per-minute / per-IP-daily / global-daily + the daily budget | already built, and **keyed on things the caller can't choose** — rotating UUIDs doesn't escape per-IP; the global daily budget is the true ceiling on the bill |
  | new-IDs-per-IP | an IP presenting more than a handful of *never-seen* install IDs in a day is throttled | defeats UUID rotation from one host specifically; honest phones present exactly one |
  | token bounds | summary ≤ ~3K tokens, question ≤ 500 chars, history window ~6 turns, `max_tokens` on output | cost per call is a constant, not a variable |
  | prompt caching | system prompt + summary as the cached prefix across a session | multi-turn cost collapses to question + answer |
  | scope | system prompt: this user's finances from this summary only; brief refusal otherwise | kills general-chatbot use cheaply |
  | period cache | narrative keyed by `(period, hash(summary))`; regenerate only on material change | layer 2 is ~one call per user per month |
  Cost is then bounded by construction: `users × N/day × constant`, with the global budget as the
  hard stop. Size N against current model pricing at implementation time, not from memory. The
  bearer token remains the real gate — this is the same exposure `/v1/parse` already carries, and
  `API_TOKEN_PREVIOUS` rotation is the response to a leak. **App attestation** (Play Integrity /
  App Attest) is the answer to "is this call from my app" and the escalation path if that ever
  proves insufficient; it is deliberately not v1.
- **Out of scope (follow-ups):** streaming (Haiku is fast and answers are short; SSE over
  `HttpURLConnection`/`NSURLSession` is complexity for later); an on-device path (the local models
  were already judged materially weaker for parsing, and this is harder); a general chat tab; any
  write action from a model reply; multi-period or cross-year analysis beyond "this vs prior".
- **Decided (2026-09-03):**
  1. **Free text ships in v1**, alongside the suggested-question chips — capped at 500 chars,
     behind the per-install allowance. This makes the quota work a v1 requirement rather than a
     follow-up, and makes the `chip`/`free` analytics split the measurement of whether the box
     earns its cost.
  2. **Anonymous install ID: yes, and it is not the enforcement layer.** A random UUID, tied to
     nothing, disclosed in the privacy policy. Fairness for honest clients comes from it; abuse
     resistance comes from the IP tiers, the new-IDs-per-IP heuristic, and the global budget,
     none of which the caller controls.

## Design
- **Pure core, `feature/insights/domain/`:**
  - `InsightsSummary` — built by `BuildInsightsSummaryUseCase` over the same repositories
    `GetDashboardMetricsUseCase` reads; `DashboardSnapshot` already carries income/expense with
    trend percentages, `expenseByCategory`/`expenseByBrand` shares, `limitByCategory`, and the
    uncategorized totals, so the builder is mostly selection and rounding, not new math.
    `CategoryLimit.effectiveFor` supplies the budget side.
  - `deriveInsights(summary, prior)` — the deterministic rules, pure and fully unit-testable. Each
    rule yields an `Insight(type, ref, delta, severity)`; ordering is by severity then magnitude.
  - `AiInsights` port (like `AiSmsParser`): `narrate(summary): List<Insight>?` and
    `ask(summary, history, question): Answer?`. `sanitizeInsights` owns acceptance — unknown
    refs dropped, deltas re-derived from the summary rather than trusted, suggestion amounts
    bounded like `MAX_AMOUNT_MINOR`.
  - `GenerateReviewUseCase` (layers 1+2: deterministic always; narrative only when enabled,
    cached, and the hash changed) and `AskInsightUseCase` (layer 3: quota check first, then the
    call; every failure returns the deterministic layer, never an error screen).
- **Server:** `/v1/insights` and `/v1/insights/ask` beside `/v1/parse` in `server/app/main.py`;
  an `InsightsProvider` protocol beside `ParseProvider`, so a self-hosted model stays a swap.
  Same bearer auth and `Limiter`; the new per-install quota keys on the `X-Install-Id` header and
  falls back to IP when absent. Structured output via a JSON schema; the system prompt lives
  server-side with the parse prompt. No content logged — the existing policy.
- **Transport:** `RemoteInsightsClient` in commonMain; `HttpRemoteInsightsClient` (androidApp,
  `HttpURLConnection`) and `IosRemoteInsightsClient` (iosMain, `NSURLSession`), mirroring
  `RemoteParseClient` — no HTTP dependency, every failure is `null`, offline degrades to layer 1.
- **Data:** an `insights` Room table — `periodKey`, `summaryHash`, `payload` (JSON), `createdAt`
  — for the period cache; additive `AutoMigration`, `SCHEMA_VERSION` +1. Not in the backup
  envelope: it is a cache, re-derived on any device. Prefs: `insightsEnabled`, `installId`,
  `insightsAskedToday` + its date (the visible cap; the server is the enforcement).
- **UI:** a **"Monthly review"** card on the dashboard → an insights screen of `SurfaceCard`s
  (severity via the semantic finance colours, glyph via `iconForKey`, tap deep-links to the
  filtered transaction list) → **Ask** as a bottom sheet with suggested-question chips
  (`ChipLaneGrid`) and, if enabled, a capped text field. The review cards fit the existing
  components; the Ask sheet does not yet have a design — `// TODO: design` rather than inventing
  a chat UI. AI affordances hidden entirely when `insightsEnabled` is off or no service is
  configured, matching every other AI surface.
- **Analytics (PII-free):** `insights_review_shown(deterministic_count)`,
  `insights_narrative_generated`, `insights_ask(source=chip|free)`, `insights_quota_hit(scope)`,
  `insights_suggestion_accepted(type)`. Never the question, never the answer. The
  `chip`/`free` split is the measurement behind open decision 1.
- **Test strategy:** commonTest for the summary builder (a known ledger produces known
  aggregates; notes and rows never appear in the summary — assert their absence), every
  deterministic rule with its edge cases (no prior period, zero-spend categories, a limit with no
  spend), `sanitizeInsights` (unknown category dropped, delta re-derived, out-of-range suggestion
  clamped), the period cache (same hash → no call; changed hash → one call), and the quota (client
  counter resets at the day boundary; server 429 → deterministic fallback, no error surfaced).
  `server/evals`: a small set of summaries with expected *properties* — flags the largest delta,
  never names a category absent from the summary, refuses an off-topic question — the same
  property style as the parse evals.
- **Sequence:** PR 1 layer 1 (summary builder + rules + review card; no server, no opt-in, ships
  to everyone). PR 2 server endpoint + `AiInsights` + narrative cards + opt-in + privacy section +
  "See what's shared". PR 3 Ask — chips **and** the capped free-text field — with the install-ID
  allowance, the new-IDs-per-IP heuristic, and the visible counter landing together, since the
  free-text box is what makes them necessary.

## Landed
- **PR 1 (2026-09-03):** layer 1 — `InsightsSummary`, `deriveInsights`, the Review card and the
  insights screen. No server, no opt-in.
- **PR 2 (2026-09-03):** layer 2 — `/v1/insights` + `InsightsProvider`, `AiInsights` /
  `RemoteAiInsights`, `sanitizeNarrative`, the `insight_narratives` cache (schema 9→10), the
  ask card on the screen (Explain with AI + See what's shared), the privacy section, confirm-first
  "Set a limit" chips, property evals. **Revised the same evening:** the `insightsEnabled` switch
  (Settings → Insights) was removed at his request — consent is the tap, every time. Every visit
  starts at the ask (showing a cached answer on open read as an automatic send); a tap for unchanged
  figures is answered from the cache without a send. Decisions made while building:
  - **"Material change" is defined by the deterministic layer**, not by a hash of the summary: the
    cache key is the sorted finding ids + income/expense/uncategorized rounded to two significant
    digits + language. Hashing the summary would have regenerated per transaction; this regenerates
    when the review would read differently. An empty reply is cached too.
  - **Language rides the request** (`en`|`ar`) and is part of the key; the text is rendered as-is,
    so an Arabic user must get Arabic. The eval asserts the script.
  - **Unknown category → drop the item**, not demote it: its text is about something the user
    can't see. Period-wide items are not collapsed (savings rate and uncategorized are distinct).
  - **Suggestion bounds:** within 3× of max(spent, prior, limit), positive, ≠ current limit, rounded
    to whole units. The chip opens the editor prefilled; Save is the confirmation. Nothing is written
    by a reply.
  - **The ask has one action plus a look at the payload** (Explain with AI / See what's shared).
    No "not now", nothing to dismiss: an unanswered ask costs nothing and sends nothing.
  - **One transport per platform** (`ServiceTransport`) replaced the per-endpoint HTTP clients so
    parsing and insights share the wire code and the contract stays in commonMain.
  - **Not done:** prompt caching (Haiku's 4096-token floor makes it inert at this payload size),
    the per-install quota and the visible counter (PR 3, where free text makes them necessary).

## Risks
The deterministic layer may already deliver most of the perceived value, which would make the
narrative layer hard to justify against its privacy cost — that is a good outcome, and PR 1
shipping alone makes it measurable before any data leaves the phone. Brand names in the summary
are user- and bank-controlled text inside a prompt; with no tools and no writes the blast radius
is a wrong sentence, but the structured-output validator is what keeps a wrong sentence from
becoming a wrong card. Prompt caching only pays if the summary is byte-stable within a session,
so the builder must be deterministic in ordering and rounding. And the install ID is spoofable by design — a reinstall
resets it and a script can rotate it — which is why it only ever allocates a fair share and never
guards the bill; the IP tiers and the global budget do that, and attestation is the next rung if
they ever prove insufficient.
