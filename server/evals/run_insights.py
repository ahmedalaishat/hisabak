"""Property evals for `/v1/insights`, against the shipped prompt and schema.

A narrative has no single right answer, so these check properties instead: the over-limit
category leads, no category id is invented, a hostile category name is not obeyed, Arabic comes
back in Arabic. Same rules as `evals/run.py`: it imports `app.prompts`/`app.schema` rather than
restating them, one pass is a smoke test, and REPEAT raises the bar.

    python -m evals.run_insights            # all cases
    python -m evals.run_insights arabic     # ids containing "arabic"
    REPEAT=3 python -m evals.run_insights
"""

import asyncio
import json
import os
import re
import sys
from pathlib import Path

import anthropic

from app.prompts import build_insights
from app.schema import InsightsRequest, Narrative

CASES = Path(__file__).with_name("insights_cases.json")
# ~600 in + ~300 out per case at Haiku 4.5 rates.
COST_PER_CASE = 0.002
_ARABIC = re.compile(r"[؀-ۿ]")


def _check(case: dict, got: Narrative) -> list[str]:
    ids = {c["id"] for c in case["summary"]["categories"]}
    problems = []
    if not 1 <= len(got.items) <= 5:
        problems.append(f"item count {len(got.items)} outside 1..5")
    for item in got.items:
        if item.category_id is not None and item.category_id not in ids:
            problems.append(f"invented category_id {item.category_id!r}")
        if len(item.headline) > 80:
            problems.append(f"headline too long ({len(item.headline)}): {item.headline!r}")
    mentioned = {i.category_id for i in got.items}
    if "must_mention" in case and not mentioned & set(case["must_mention"]):
        problems.append(f"none of {case['must_mention']} mentioned; got {sorted(m for m in mentioned if m)}")
    if "first_must_be" in case and (not got.items or got.items[0].category_id != case["first_must_be"]):
        first = got.items[0].category_id if got.items else None
        problems.append(f"first item is {first!r}, want {case['first_must_be']!r}")
    text = " ".join(f"{i.headline} {i.detail}" for i in got.items).lower()
    for word in case.get("forbidden_text", []):
        if word in text:
            problems.append(f"forbidden text {word!r} present")
    if case.get("script") == "arabic" and not all(_ARABIC.search(i.headline) for i in got.items):
        problems.append("a headline is not in Arabic script")
    return problems


async def _attempt(client: anthropic.AsyncAnthropic, case: dict) -> list[str]:
    request = InsightsRequest(**case["summary"])
    system, user = build_insights(request)
    response = await client.messages.parse(
        model=os.getenv("HISABAK_MODEL", "claude-haiku-4-5"),
        max_tokens=1024,
        system=system,
        messages=[{"role": "user", "content": user}],
        output_format=Narrative,
    )
    got = response.parsed_output or Narrative(items=[])
    return _check(case, got)


async def _main(filters: list[str]) -> int:
    cases = json.loads(CASES.read_text())["cases"]
    if filters:
        cases = [c for c in cases if any(f in c["id"] for f in filters)]
    repeat = int(os.getenv("REPEAT", "1"))
    workspace = os.getenv("ANTHROPIC_WORKSPACE_ID", "").strip()
    client = anthropic.AsyncAnthropic(
        default_headers={"anthropic-workspace-id": workspace} if workspace else None
    )

    failed = 0
    for case in cases:
        problems: list[str] = []
        for attempt in range(repeat):
            found = await _attempt(client, case)
            prefix = f"run {attempt + 1}/{repeat}: " if repeat > 1 else ""
            problems += [prefix + p for p in found]
        status = "ok  " if not problems else "FAIL"
        print(f"{status} {case['id']}")
        for p in problems:
            print(f"       {p}")
        failed += bool(problems)
    print(f"\n{len(cases) - failed}/{len(cases)} passed, ~${len(cases) * repeat * COST_PER_CASE:.3f}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(asyncio.run(_main(sys.argv[1:])))
