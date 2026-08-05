#!/usr/bin/env bash
# Stop hook: run the JVM unit suite when Kotlin sources changed, and block (exit 2)
# on failure so a red suite is never handed back. Skips instantly on turns that
# touched no Kotlin, keeping chat/planning turns fast.
set -uo pipefail

input="$(cat)"

# Prevent re-run loops: if we're already inside a Stop-hook continuation, do nothing.
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# Only run when Kotlin sources/tests/build scripts changed (staged, unstaged, or untracked).
if ! git status --porcelain 2>/dev/null | grep -Eq '\.kts?"?$'; then
  exit 0
fi

# Kotlin/Native rejects some characters in backtick test names that the JVM tolerates
# (commas, semicolons, colons) — the JVM suite stays green while the iOS CI compile breaks.
# Catch it here, before the slow test run. (Bit us in PRs #122 and #125.)
illegal="$(grep -rn 'fun `[^`]*[,;:][^`]*`' shared/src/commonTest testutil/src --include='*.kt' 2>/dev/null)"
if [ -n "$illegal" ]; then
  echo "Test names contain characters Kotlin/Native rejects (, ; :) — rename before finishing:" >&2
  printf '%s\n' "$illegal" | sed 's/(.*//' >&2
  exit 2
fi

output="$(./gradlew unitTests --console=plain 2>&1)"
if [ $? -ne 0 ]; then
  echo "Unit tests failed — fix before finishing (./gradlew unitTests):" >&2
  printf '%s\n' "$output" | tail -40 >&2
  exit 2
fi
exit 0
