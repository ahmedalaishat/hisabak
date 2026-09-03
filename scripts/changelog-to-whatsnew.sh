#!/usr/bin/env bash
# Generate Play "What's new" notes from the latest released section of CHANGELOG.md.
#
# Takes the first "## [x.y.z]" section (skipping "## [Unreleased]"), flattens its bullets to
# plain text — dropping the "### Added/Fixed" subheaders, joining wrapped lines, stripping
# markdown code ticks and bold markers — and writes each bullet's headline (its bold lead
# phrase, else its first sentence) up to Play's 500-char limit. release.yml runs this before
# the Play upload so the storefront notes always match the CHANGELOG.
set -euo pipefail

changelog="${1:-CHANGELOG.md}"
out_dir="${2:-distribution/whatsnew}"
locale="${3:-en-US}"
limit=500

mkdir -p "$out_dir"

awk -v limit="$limit" '
  # The storefront gets the headline of each entry, not its paragraph. CHANGELOG bullets open with
  # a bold lead phrase and run 250-700 chars; whole bullets meant one ever fit under 500, so
  # 2.2.0 shipped with a single note. The lead phrase (or, failing that, the first sentence)
  # is what a reader skims on Play anyway.
  function headline(b,    t) {
    gsub(/`/, "", b); gsub(/[[:space:]]+/, " ", b)
    sub(/^ /, "", b); sub(/ $/, "", b)
    if (match(b, /^\*\*[^*]+\*\*/)) {
      t = substr(b, 3, RLENGTH - 4)
    } else {
      t = b
      if (match(t, /[.!?] /)) t = substr(t, 1, RSTART)
    }
    gsub(/\*\*/, "", t)
    sub(/[[:space:]]*[—.:-]+[[:space:]]*$/, "", t)
    return t
  }
  /^## \[[0-9]/ { if (started) exit; started = 1; next }   # first versioned section
  !started { next }
  /^## / { exit }                                          # next top-level section ends it
  /^### / { next }                                         # drop Added/Fixed subheaders
  {
    line = $0
    sub(/^[[:space:]]+/, "", line)
    if (line ~ /^- /) {                  # a new bullet
      if (cur != "") bullets[++n] = cur
      sub(/^- /, "", line); cur = line
    } else if (line != "" && cur != "") {
      cur = cur " " line                 # continuation of the current wrapped bullet
    }
  }
  END {
    if (cur != "") bullets[++n] = cur
    out = ""
    for (i = 1; i <= n; i++) {
      b = headline(bullets[i])
      cand = (out == "" ? "• " b : out "\n• " b)
      # Skip rather than stop: bailing on the first over-long bullet dropped every shorter
      # one after it, and an over-long *first* bullet emptied the file entirely -- which the
      # Play upload would have accepted in silence.
      if (length(cand) > limit) continue
      out = cand
    }
    # Last resort: every bullet exceeds the limit alone. A truncated headline still beats a
    # blank storefront note.
    if (out == "" && n > 0) {
      b = headline(bullets[1])
      t = substr(b, 1, limit - 4)
      if (match(t, / [^ ]*$/)) t = substr(t, 1, RSTART - 1)
      out = "• " t "\xe2\x80\xa6"
    }
    print out
  }
' "$changelog" > "$out_dir/whatsnew-$locale"

echo "Wrote $out_dir/whatsnew-$locale ($(wc -m < "$out_dir/whatsnew-$locale" | tr -d ' ') chars):"
cat "$out_dir/whatsnew-$locale"
