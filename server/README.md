# Hisabak parse service

Extracts transaction fields from bank SMS and typed notes, so devices with **no on-device model**
get the same confirm-first parsing the flagships get. Gemini Nano needs AICore (flagship silicon
only) and Apple Foundation Models needs an iPhone 15 Pro or newer — that is a small minority of
real devices. This service covers the rest.

## Why it costs almost nothing to run

The app turns each successful parse into a regex template (`SynthesizeTemplateUseCase`), so the
service is called roughly **once per bank format**, not once per message. At Haiku 4.5 rates a
call is ~700 input + ~80 output tokens ≈ **$0.0011**. Thirty-odd UAE bank formats across your users
is well under a dollar in total; even the pathological case where template synthesis never works
and every unmatched message hits the API lands around **$0.55/month** for a handful of users.

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

iOS — in `iosApp/Configuration/Config.xcconfig`:

```
HISABAK_PARSE_SERVICE_URL = https:/$()/parse.example.com
HISABAK_PARSE_SERVICE_TOKEN = <the HISABAK_API_TOKEN value>
```

Leave either blank and the remote parser reports `Unavailable`; the app behaves exactly as it does
today. **The user must also opt in** (Settings → SMS parsing) before any text is sent.

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
