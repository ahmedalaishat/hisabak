# Hisabak parse service

Extracts transaction fields from bank SMS and typed notes, so devices with **no on-device model**
get the same confirm-first parsing the flagships get. Gemini Nano needs AICore (flagship silicon
only) and Apple Foundation Models needs an iPhone 15 Pro or newer — that is a small minority of
real devices. This service covers the rest.

## Why it costs almost nothing to run

The app turns each successful parse into a regex template (`SynthesizeTemplateUseCase`), so the
service is called roughly **once per bank format**, not once per message.

Measured against the real prompt: ~371 system tokens (with 50 known brands) + ~25 for the message,
~40 out. At Haiku 4.5 rates ($1/$5 per MTok) that is **$0.0006 per call — about 1,700 calls per
dollar**.

| Scenario | calls/user/mo | 1 user | 10 users | 100 users |
|---|---:|---:|---:|---:|
| Design intent — one call per bank format¹ | 10 | $0.006 | $0.06 | $0.60 |
| Synthesis fails half the time, 30 unmatched/mo | 30 | $0.018 | $0.18 | $1.80 |
| Worst case — every unmatched message, heavy user | 100 | $0.060 | $0.60 | $6.00 |

¹ One-off per format, not recurring: it stops once the formats a user's banks send are learned.

The VPS costs more than the API does. Self-hosting a model to avoid this spend would need 8–16 GB
of RAM to save roughly a dollar a year, and would extract worse. Don't.

**No prompt caching.** Haiku 4.5 will not cache a prefix under 4096 tokens and this one is ~371, so
a `cache_control` marker is silently inert. It would not help regardless — parses are minutes or
hours apart and the cache TTL is five minutes.

Self-hosting a model to avoid that spend would need 8–16 GB of RAM to save roughly a dollar a
year, and would extract worse. Don't.

## Requirements

Effectively nothing: it is an HTTP proxy, not an inference host. ~120 MB RSS, no GPU, negligible
CPU (every request is I/O-wait on the Anthropic API). It co-exists happily with other apps on a
small shared box — a Hetzner CX22 (2 vCPU / 4 GB) is far more than enough.

## Deploy

```bash
cp .env.example .env
# Set ANTHROPIC_API_KEY, and generate the shared secret:
#   openssl rand -hex 32
$EDITOR .env

docker compose up -d --build
curl -s localhost:8080/health     # {"status":"ok","provider":"anthropic"}
```

It binds **loopback only** (`127.0.0.1:${HISABAK_PORT:-8080}`). Set `HISABAK_PORT` if 8080 is
already taken on the box.

### Put TLS in front of it

The request body carries message text, so it must never travel over plain HTTP. Point your
existing reverse proxy at it. Caddy:

```
parse.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

nginx:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

`X-Forwarded-For` matters: the rate limiter keys on the client IP, and without it every request
looks like it came from the proxy.

## Point the app at it

Android — in `~/.gradle/gradle.properties` (never commit the token):

```properties
parseServiceUrl=https://parse.example.com
parseServiceToken=<the HISABAK_API_TOKEN value>
```

iOS — in `iosApp/Configuration/Local.xcconfig` (gitignored; `Config.xcconfig` is committed and
must stay blank):

```
HISABAK_PARSE_SERVICE_URL = https:/$()/parse.example.com
HISABAK_PARSE_SERVICE_TOKEN = <the HISABAK_API_TOKEN value>
```

The `$()` in the URL is not a typo — a bare `//` starts a comment in xcconfig, so the scheme's
slashes have to be split by an empty variable expansion or the value is truncated to `https:`.

Leave either blank and the remote parser reports `Unavailable`; the app behaves exactly as it does
today. **The user must also opt in** (Settings → SMS parsing) before any text is sent.

Note the ordering: `PreferOnDeviceAiSmsParser` tries the on-device model first, so a device that has
one (a flagship Android, or an iPhone 15 Pro or newer) will not call this service unless the local
model returns nothing. The service is there for the devices that have no local model — which is
where the feature was previously absent entirely.

## API

```
POST /v1/parse          Authorization: Bearer <HISABAK_API_TOKEN>
{ "text": "...", "known_brands": [...], "today_iso": "2026-09-02, Wednesday", "free_text": false }
→ { "brand": "...", "amount_minor": 12550, "currency": "AED", "date_iso": null, "model": "..." }
```

Every field may be null — "this isn't a transaction" is a valid answer, and the app's shared
`sanitize` step decides whether a partial result is usable. Non-200 means the app falls back to its
regex templates; a parse failure never costs a capture.

`GET /health` needs no token and doubles as an uptime check.

## Abuse and spend control

**The bearer token is not a secret.** It is compiled into a distributed app, so anyone with the
APK can extract it (`strings` finds it). It identifies the client and keeps the endpoint off the
open internet; it does not authenticate a person. The defence is therefore not secrecy but a
bounded bill and a useless-to-abuse surface.

**Bounded bill** — three tiers in `app/limits.py`, cheapest failure first:

| Tier | Default | Stops |
|---|---:|---|
| per-minute, per IP | 30 | a hot loop |
| per-day, per IP | 200 | a slow drip that never trips the minute window |
| **per-day, global** | **2000** | a spray across many addresses — the actual ceiling |

At ~$0.0006 a call the global cap is about **$1.20/day worst case**. Real usage is single digits
per day. The response never says which tier tripped: that would tell an abuser how much headroom
is left and how to pace around it. Set a **spend limit on the Anthropic key too** — that is the
backstop that survives a bug in any of this.

**Useless to abuse** — two things matter, and the second matters more:

- `text` must contain a digit and is capped at 800 chars. This trims obvious junk, but it is a
  weak filter on its own: *"write a 500 word essay"* contains a number and passes.
- The request uses **structured output** (`output_format=ParsedSms`) with `max_tokens=256`, so the
  model can only ever return `brand`, `amount_minor`, `currency`, `date_iso`. A prompt-injection or
  a translation request comes back as four nulls — verified against the live service. There is no
  path by which this endpoint emits free-form text.

**Rotating the token** — `./rotate-token.sh root@your-host`. Updates the server, both client
configs, and health-checks the result, rolling back the `.env` if the service does not come up.
Existing installs get 401 until you rebuild them.

## Privacy

**Message text is never logged or persisted.** Access logging is off (`--no-access-log`), no handler
writes `text` anywhere, and the error path logs only the provider name — a traceback could carry
the prompt. Verify after any change:

```bash
docker compose logs parse | grep -i carrefour   # must return nothing
```

Text is sent to Anthropic for inference and is not used for training. If you self-host a model
later, swap `ParseProvider` (`app/providers/base.py`) and nothing on the client changes.

## Swapping the model

`HISABAK_MODEL` picks the Anthropic model (default `claude-haiku-4-5`). For a different provider
entirely, implement the `ParseProvider` protocol and bind it in `app/main.py`.

## Tests

```bash
python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt pytest httpx
./.venv/bin/python -m pytest tests -q
```

Covers the contract, auth, validation, rate limiting, and failure handling with a stubbed provider —
no network, no API key, no spend. The model call itself is not covered.
