"""Run the eval cases against the real prompt and schema.

Imports `app.prompts` and `app.schema` rather than restating them. That is the whole point: a
hand-written probe with the prompt pasted inline reported a bug as unfixed after it had been
fixed, and hid a second bug entirely. An eval that does not exercise the shipped code path
measures nothing.

    python -m evals.run                 # run everything once
    python -m evals.run fx income       # only ids containing "fx" or "income"
    REPEAT=5 python -m evals.run        # each case five times

Exits non-zero if a decided case fails, so it can gate a deploy. Open questions never fail.

**A single pass is not proof.** Model output varies between runs: deleting a prompt rule that
three cases depend on made only one of them fail on the first attempt. Treat one run as a smoke
test and raise REPEAT before trusting a result you intend to act on.
"""

import asyncio
import json
import os
import sys
from pathlib import Path

import anthropic

from app.prompts import build
from app.schema import ParsedSms

CASES = Path(__file__).with_name("cases.json")
# ~400 in + ~40 out per case at Haiku 4.5 rates.
COST_PER_CASE = 0.0006


def _mismatches(expected: dict, got: ParsedSms) -> list[str]:
    """Only the fields a case actually names — a case about the amount says nothing about dates."""
    out = []
    for field, want in expected.items():
        have = getattr(got, field, "<missing>")
        if have != want:
            out.append(f"{field}: got {have!r}, want {want!r}")
    return out


async def _run_case(client: anthropic.AsyncAnthropic, case: dict, repeat: int) -> tuple[bool, list[str]]:
    """Fails if ANY attempt fails — a case that only sometimes passes is not passing."""
    problems: list[str] = []
    for attempt in range(repeat):
        ok, found = await _attempt(client, case)
        if not ok:
            prefix = f"run {attempt + 1}/{repeat}: " if repeat > 1 else ""
            problems += [prefix + f for f in found]
    return not problems, problems


async def _attempt(client: anthropic.AsyncAnthropic, case: dict) -> tuple[bool, list[str]]:
    prompt = build(
        free_text=case.get("free_text", False),
        today=case.get("today_iso"),
        known_brands=case.get("known_brands", []),
    )
    response = await client.messages.parse(
        model=os.getenv("HISABAK_MODEL", "claude-haiku-4-5"),
        max_tokens=256,
        system=prompt,
        messages=[{"role": "user", "content": case["text"]}],
        output_format=ParsedSms,
    )
    parsed = response.parsed_output
    if parsed is None:
        return False, ["model returned no structured output"]
    return not (m := _mismatches(case["expect"], parsed)), m


async def main() -> int:
    cases = json.loads(CASES.read_text())["cases"]
    if filters := sys.argv[1:]:
        cases = [c for c in cases if any(f in c["id"] for f in filters)]
    if not cases:
        print("no cases matched")
        return 1

    workspace = os.getenv("ANTHROPIC_WORKSPACE_ID", "").strip()
    client = anthropic.AsyncAnthropic(
        default_headers={"anthropic-workspace-id": workspace} if workspace else None
    )

    repeat = max(1, int(os.getenv("REPEAT", "1")))
    runs = len(cases) * repeat
    print(f"{len(cases)} cases x{repeat} = {runs} runs, about ${runs * COST_PER_CASE:.3f}\n")
    failed, open_failed = [], []
    for case in cases:
        ok, problems = await _run_case(client, case, repeat)
        is_open = case.get("open_question", False)
        mark = "ok  " if ok else ("open" if is_open else "FAIL")
        print(f"  {mark}  {case['id']}")
        for problem in problems:
            print(f"          {problem}")
        if not ok:
            (open_failed if is_open else failed).append(case["id"])

    print(f"\n{len(cases) - len(failed) - len(open_failed)}/{len(cases)} passed")
    if open_failed:
        print(f"{len(open_failed)} open question(s) differ from the recorded answer: {', '.join(open_failed)}")
        print("  Not a failure — decide the answer, update cases.json, drop open_question.")
    if failed:
        print(f"FAILED: {', '.join(failed)}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
