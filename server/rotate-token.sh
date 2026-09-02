#!/usr/bin/env bash
# Rotate the parse-service bearer token.
#
# The token is compiled into the apps, so rotating it is a coordinated change: server, then both
# client configs, then rebuild. Doing it by hand invites a half-rotation where the server rejects
# every client, so it lives in one script.
#
#   ./rotate-token.sh root@1.2.3.4 [remote-dir]
#
# Afterwards rebuild and reinstall both apps — until then they will get 401s.
set -euo pipefail

REMOTE="${1:?usage: rotate-token.sh user@host [remote-dir]}"
DIR="${2:-/root/hisabak-parse}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
LOCAL_XCCONFIG="$REPO_ROOT/iosApp/Configuration/Local.xcconfig"

NEW="$(openssl rand -hex 32)"

echo "==> server: $REMOTE:$DIR"
ssh "$REMOTE" "
  set -e
  cd '$DIR'
  cp .env .env.bak
  # Replace in place, preserving every other setting.
  sed -i 's|^HISABAK_API_TOKEN=.*|HISABAK_API_TOKEN=$NEW|' .env
  chmod 600 .env
  docker compose up -d --force-recreate >/dev/null 2>&1
  sleep 4
  curl -sf http://127.0.0.1:8080/health >/dev/null && echo '    service healthy' || { echo '    UNHEALTHY - restoring'; mv .env.bak .env; docker compose up -d --force-recreate >/dev/null 2>&1; exit 1; }
  rm -f .env.bak
"

echo "==> android: $GRADLE_PROPS"
if grep -q '^parseServiceToken=' "$GRADLE_PROPS" 2>/dev/null; then
  sed -i '' "s|^parseServiceToken=.*|parseServiceToken=$NEW|" "$GRADLE_PROPS"
  echo "    updated"
else
  echo "    no parseServiceToken entry - skipped"
fi

echo "==> ios: $LOCAL_XCCONFIG"
if [ -f "$LOCAL_XCCONFIG" ]; then
  sed -i '' "s|^HISABAK_PARSE_SERVICE_TOKEN = .*|HISABAK_PARSE_SERVICE_TOKEN = $NEW|" "$LOCAL_XCCONFIG"
  echo "    updated"
else
  echo "    not present - skipped"
fi

echo
echo "Rotated. Existing installs now get 401 until you rebuild:"
echo "  ./gradlew :androidApp:installStagingDebug"
echo "  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosAppStaging ... build"
