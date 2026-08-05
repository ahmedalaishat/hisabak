---
name: feature
description: Automated SDLC pipeline for Hisabak. Turn a high-level requirement into a tested, documented PR against develop that auto-merges on green CI. Use when the user types /feature "<requirement>" or asks to build a feature end-to-end. Runs spec → design → branch → code+tests → QA → docs → PR → enable auto-merge.
user-invocable: true
---

# Hisabak feature pipeline

Turn one high-level requirement into a tested, documented PR that **auto-merges on green
CI**. This is a playbook you (Claude) execute in the current session — not background
automation. Compose the existing tooling; don't reinvent it.

**Input:** the requirement in `$ARGUMENTS` (e.g. `/feature "let users archive a category"`).
If `$ARGUMENTS` is empty, ask the user what to build, then proceed.

**Golden rule:** autonomous from intake through the open PR, then **enable auto-merge** —
the PR lands on `develop` itself once CI is green. Don't wait for a "merge it"; that phrase
is now only for the manually-gated `develop`→`main` release PR (via `git-workflow`), *not*
this pipeline. (See the `automate-the-dev-workflow` memory.)

---

## 1. Intake & clarify

Read `$ARGUMENTS`. Ask **1–2 clarifying questions only if it's too ambiguous to spec
safely** (unclear scope, multiple plausible behaviors, a data/UX decision you can't default).
Otherwise proceed and capture any guesses under **Assumptions** in the spec. Keep upfront
questions minimal — the end review is the real gate.

Derive a short kebab `<slug>` from the requirement (e.g. `archive-category`).

## 2. Requirements spec + 3. Design note

Write both to `docs/features/<slug>.md` (committed on the branch, summarized later in the
PR body). Keep it tight:

```markdown
# <Feature title>

## Requirement
<the user's one-liner, verbatim>

## Spec
- **Goal:** …
- **In scope:** …
- **Out of scope:** …
- **Acceptance criteria:** bullet list, each verifiable
- **Edge cases:** …
- **Assumptions:** … (any guess you made instead of asking)

## Design
- **Domain/model changes:** entities, use cases, repos touched
- **Files to add/change:** representative paths
- **Test strategy:** which unit tests prove the acceptance criteria
- **Trade-offs / decisions:** …
```

Follow the codebase's clean-architecture-per-feature layout and `CLAUDE.md` conventions
(domain/data/presentation, Koin DI, MVI `BaseViewModel`, the design system). For any UI,
use the **`hisabak-design`** skill and match existing screens — don't invent new flows.

## 4. Branch

Per **`git-workflow`**: branch `feat/<slug>` off `develop`.
```bash
git checkout develop && git pull --ff-only origin develop
git checkout -b feat/<slug>
```

## 5. Implement (code + tests together)

Build the feature **and its tests in the same change** — this is a hard `CLAUDE.md` rule.
- Reuse the `com.hisabak.testutil` harness (`TestClock`, `MainDispatcherRule`, `Fake*`
  repositories, `TestData`); prefer fakes over a mocking framework. Full guide:
  `docs/testing.md`.
- Cover each acceptance criterion with a test. New logic/use-case/ViewModel → new tests.
- Only pull in the `testing-setup` skill if genuinely new test infrastructure is required.

## 6. QA

- Run `./gradlew unitTests` until green (the Stop hook also enforces this).
- Run a self **`code-review`** pass over the diff and fix real findings.
- CI (`.github/workflows/test.yml`) re-verifies on the PR.
- No real-device step for now (out of scope; add later via `verify`/`run` when an emulator
  is available).

## 7. Docs + changelog (when user-visible)

If the change is user-facing: update the relevant `README.md`/`docs/`, and add an entry to
`CHANGELOG.md` under an `## [Unreleased]` heading (Keep a Changelog: Added/Changed/Fixed).
Skip for purely internal changes — say so in the PR instead.

## 8. Consistency check (after code review, before the PR)

Before opening the PR, confirm the diff didn't leave any docs/skills stale, and update
whatever it touched **in this same PR**:
- **CLAUDE.md** — stack/architecture/storage facts, Gradle commands, conventions.
- **README.md** — build/run commands, features, badges, tech stack.
- **docs/** — `testing.md` (test command + coverage), `cd.md`.
- **.claude/skills/** — `git-workflow` (Gradle task names, release steps), `feature` (pipeline
  commands), `hisabak-design/compose-bridge.md` (tokens/components vs the app theme).
- **.claude/hooks/run-tests.sh** + **.github/workflows/*.yml** — task names/triggers.
- **CHANGELOG.md** — entry for user-visible changes.

Common triggers: renamed Gradle tasks/variants, changed `applicationId`/package, changed
test/build/run commands, storage/architecture changes, design token/component changes, new
dependencies, user-visible behavior. (A `gh pr create` hook re-surfaces this checklist too.)

## 9. Open the PR

Commit on the branch (clear messages, Co-Authored-By trailer), push, then:
```bash
gh pr create --base develop --fill
```
PR body must include: a one-paragraph summary, the spec + design (or a link to
`docs/features/<slug>.md`), what tests prove it, and the changelog line. End the body with
the `🤖 Generated with Claude Code` footer.

## 10. Enable auto-merge

Enable auto-merge so the PR lands on `develop` itself once CI is green:
```bash
gh pr merge --auto --merge --delete-branch   # merges on green CI; deletes the remote branch
```
Then present to the user: the PR link, the acceptance criteria with how each is met, the
test additions, CI status, and any assumptions made — noting that it will auto-merge on
green CI. Once it has merged, sync local `develop` (`git checkout develop &&
git pull --ff-only origin develop && git branch -d feat/<slug>`). Releases (`develop → main`
+ version tag) stay a separate, deliberate step via the `git-workflow` skill — never bundle
them here.

---

## Checklist (every run)

- [ ] Spec + design in `docs/features/<slug>.md`, assumptions recorded
- [ ] `feat/<slug>` off `develop`
- [ ] Code **and** tests for every acceptance criterion
- [ ] `./gradlew unitTests` green + self code-review done
- [ ] Docs/changelog updated (or explicitly noted N/A)
- [ ] **Consistency check** — CLAUDE.md / README / docs / skills / hooks / workflows / CHANGELOG reviewed against the diff
- [ ] PR to `develop` with structured body
- [ ] **Auto-merge enabled** (`gh pr merge --auto`) — lands on green CI, no manual gate
