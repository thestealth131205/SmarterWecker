#!/usr/bin/env bash
# Erhöht die Versionsnummer, committet, pusht, wartet auf den GitHub-Actions-Build
# und deployt die signierte Release-APK auf letheapp.de (/nocturn.php).
#
# Aufruf:
#   scripts/release-deploy.sh                 # Minor-Bump (0.6.0 -> 0.7.0), Standardfall
#   scripts/release-deploy.sh patch           # Patch-Bump (0.6.0 -> 0.6.1)
#   scripts/release-deploy.sh major           # Major-Bump (0.6.0 -> 1.0.0)
#   scripts/release-deploy.sh 1.2.3           # explizite Versionsnummer
#
# Voraussetzungen: GitHub-Token mit Repo-Zugriff unter /opt/githubtok3n.txt,
# Zugangsdaten zum Lethe-Hauptserver in .server-credentials (gitignored, siehe dort).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_FILE="$REPO_DIR/app/build.gradle.kts"
TOKEN_FILE="/opt/githubtok3n.txt"
CREDENTIALS_FILE="$REPO_DIR/.server-credentials"
GH_REPO="thestealth131205/SmarterWecker"
WORKFLOW_FILE="build-apk.yml"
POLL_INTERVAL=15
POLL_TIMEOUT=1800   # 30 Minuten

log() { echo "[release-deploy] $*"; }
die() { echo "[release-deploy] FEHLER: $*" >&2; exit 1; }

[[ -f "$TOKEN_FILE" ]] || die "GitHub-Token nicht gefunden unter $TOKEN_FILE"
GH_TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
[[ -n "$GH_TOKEN" ]] || die "GitHub-Token-Datei ist leer"

[[ -f "$CREDENTIALS_FILE" ]] || die "Server-Zugangsdaten nicht gefunden unter $CREDENTIALS_FILE"
# shellcheck source=/dev/null
source "$CREDENTIALS_FILE"
[[ -n "${MAIN_SERVER_IP:-}" && -n "${MAIN_SERVER_USER:-}" && -n "${MAIN_SERVER_PASSWORD:-}" && -n "${MAIN_SERVER_DEPLOY_DIR:-}" ]] \
    || die "Server-Zugangsdaten in $CREDENTIALS_FILE unvollständig"
command -v sshpass >/dev/null || die "sshpass ist nicht installiert"

cd "$REPO_DIR"
[[ -d .git ]] || die "$REPO_DIR ist kein Git-Repository"

gh_api() {
    # $1 = Pfad ab https://api.github.com, weitere Args werden an curl durchgereicht
    local path="$1"; shift
    curl -sS -H "Authorization: Bearer $GH_TOKEN" \
         -H "Accept: application/vnd.github+json" \
         -H "X-GitHub-Api-Version: 2022-11-28" \
         "$@" "https://api.github.com/repos/$GH_REPO$path"
}

json_get() {
    # Liest ein Feld aus JSON (stdin) per simplem Python-Zugriffspfad, z.B. json_get ".sha"
    python3 -c "
import sys, json
data = json.load(sys.stdin)
path = sys.argv[1].lstrip('.')
cur = data
for part in path.split('.'):
    if part == '':
        continue
    if part.isdigit():
        cur = cur[int(part)]
    else:
        cur = cur[part]
print(cur if cur is not None else '')
" "$1"
}

# ---------- 1. Aktuelle Version auslesen ----------
CURRENT_CODE="$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$GRADLE_FILE")"
CURRENT_NAME="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$GRADLE_FILE")"
[[ -n "$CURRENT_CODE" && -n "$CURRENT_NAME" ]] || die "Konnte versionCode/versionName nicht aus $GRADLE_FILE lesen"

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"
BUMP="${1:-minor}"

if [[ "$BUMP" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    NEW_NAME="$BUMP"
else
    case "$BUMP" in
        major) NEW_NAME="$((MAJOR + 1)).0.0" ;;
        minor) NEW_NAME="${MAJOR}.$((MINOR + 1)).0" ;;
        patch) NEW_NAME="${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
        *) die "Unbekannter Bump-Typ '$BUMP' (erwartet: major|minor|patch|X.Y.Z)" ;;
    esac
fi
NEW_CODE=$((CURRENT_CODE + 1))

log "Version $CURRENT_NAME (Code $CURRENT_CODE) -> $NEW_NAME (Code $NEW_CODE)"

# ---------- 2. Gradle-Datei anpassen (nur der jeweils erste Treffer in der Datei) ----------
sed -i "0,/versionCode = [0-9]\+/s//versionCode = $NEW_CODE/" "$GRADLE_FILE"
sed -i "0,/versionName = \"[0-9.]\+\"/s//versionName = \"$NEW_NAME\"/" "$GRADLE_FILE"
grep -q "versionCode = $NEW_CODE" "$GRADLE_FILE" || die "versionCode-Ersetzung fehlgeschlagen"
grep -q "versionName = \"$NEW_NAME\"" "$GRADLE_FILE" || die "versionName-Ersetzung fehlgeschlagen"

# ---------- 3. Commit + Push ----------
git add -A
if git diff --cached --quiet; then
    die "Keine Änderungen zum Committen (versionCode/versionName wurden nicht angepasst?)"
fi

COMMIT_MSG="Version ${NEW_NAME}: Release-Deploy

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
git commit -m "$COMMIT_MSG"
HEAD_SHA="$(git rev-parse HEAD)"
log "Commit $HEAD_SHA erstellt, pushe nach main..."
git push origin main

# ---------- 4. Auf den passenden Workflow-Run warten ----------
log "Warte auf den Start des GitHub-Actions-Runs für $HEAD_SHA..."
RUN_ID=""
START_TIME=$(date +%s)
while [[ -z "$RUN_ID" ]]; do
    (( $(date +%s) - START_TIME > POLL_TIMEOUT )) && die "Timeout beim Warten auf den Workflow-Start"
    RESPONSE="$(gh_api "/actions/workflows/$WORKFLOW_FILE/runs?branch=main&event=push&per_page=10")"
    RUN_ID="$(python3 -c "
import sys, json
data = json.load(sys.stdin)
sha = sys.argv[1]
for run in data.get('workflow_runs', []):
    if run['head_sha'] == sha:
        print(run['id'])
        break
" "$RESPONSE" "$HEAD_SHA" 2>/dev/null || true)"
    [[ -n "$RUN_ID" ]] || sleep "$POLL_INTERVAL"
done
log "Workflow-Run gefunden: https://github.com/$GH_REPO/actions/runs/$RUN_ID"

# ---------- 5. Auf Abschluss des Runs warten ----------
STATUS=""
START_TIME=$(date +%s)
while [[ "$STATUS" != "completed" ]]; do
    (( $(date +%s) - START_TIME > POLL_TIMEOUT )) && die "Timeout beim Warten auf den Build-Abschluss"
    RUN_JSON="$(gh_api "/actions/runs/$RUN_ID")"
    STATUS="$(echo "$RUN_JSON" | json_get ".status")"
    [[ "$STATUS" == "completed" ]] || sleep "$POLL_INTERVAL"
done
CONCLUSION="$(echo "$RUN_JSON" | json_get ".conclusion")"
[[ "$CONCLUSION" == "success" ]] || die "Build abgeschlossen mit Ergebnis '$CONCLUSION' - kein Deploy. Siehe https://github.com/$GH_REPO/actions/runs/$RUN_ID"
log "Build erfolgreich."

# ---------- 6. Release + APK-Asset finden ----------
log "Suche GitHub Release für Version $NEW_NAME..."
RELEASE_JSON=""
START_TIME=$(date +%s)
while [[ -z "$RELEASE_JSON" ]]; do
    (( $(date +%s) - START_TIME > 300 )) && die "Timeout beim Warten auf das GitHub Release"
    RELEASES="$(gh_api "/releases?per_page=10")"
    RELEASE_JSON="$(python3 -c "
import sys, json
data = json.load(sys.stdin)
prefix = sys.argv[1]
for rel in data:
    if rel['tag_name'].startswith(prefix):
        print(json.dumps(rel))
        break
" "$RELEASES" "v${NEW_NAME}-" 2>/dev/null || true)"
    [[ -n "$RELEASE_JSON" ]] || sleep 10
done

ASSET_ID="$(echo "$RELEASE_JSON" | python3 -c "
import sys, json
rel = json.load(sys.stdin)
for a in rel['assets']:
    if a['name'].endswith('.apk'):
        print(a['id'])
        break
")"
[[ -n "$ASSET_ID" ]] || die "Kein APK-Asset im Release gefunden"
log "APK-Asset gefunden (ID $ASSET_ID), lade herunter..."

# ---------- 7. APK herunterladen ----------
TMP_APK="$(mktemp --suffix=.apk)"
HTTP_CODE="$(curl -sS -L -w '%{http_code}' -o "$TMP_APK" \
    -H "Authorization: Bearer $GH_TOKEN" \
    -H "Accept: application/octet-stream" \
    "https://api.github.com/repos/$GH_REPO/releases/assets/$ASSET_ID")"
[[ "$HTTP_CODE" == "200" ]] || die "APK-Download fehlgeschlagen (HTTP $HTTP_CODE)"
[[ -s "$TMP_APK" ]] || die "Heruntergeladene APK ist leer"

# ---------- 8. Auf den Hauptserver hochladen ----------
REMOTE_APK_NAME="NocTurn-${NEW_NAME}.apk"
REMOTE_TMP_PATH="/tmp/${REMOTE_APK_NAME}"
REMOTE_DEPLOY_PATH="${MAIN_SERVER_DEPLOY_DIR}/${REMOTE_APK_NAME}"
COMMON_SSH_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=10)
SSH_PORT_OPT=(-p "${MAIN_SERVER_PORT:-22}")
SCP_PORT_OPT=(-P "${MAIN_SERVER_PORT:-22}")

log "Lade APK zum Hauptserver hoch ($MAIN_SERVER_IP:$REMOTE_DEPLOY_PATH)..."
sshpass -p "$MAIN_SERVER_PASSWORD" scp "${COMMON_SSH_OPTS[@]}" "${SCP_PORT_OPT[@]}" \
    "$TMP_APK" "${MAIN_SERVER_USER}@${MAIN_SERVER_IP}:${REMOTE_TMP_PATH}" \
    || die "scp-Upload zum Hauptserver fehlgeschlagen"
rm -f "$TMP_APK"

sshpass -p "$MAIN_SERVER_PASSWORD" ssh "${COMMON_SSH_OPTS[@]}" "${SSH_PORT_OPT[@]}" "${MAIN_SERVER_USER}@${MAIN_SERVER_IP}" bash -s <<REMOTE_SCRIPT || die "Deploy-Befehl auf dem Hauptserver fehlgeschlagen"
set -euo pipefail
mkdir -p "$MAIN_SERVER_DEPLOY_DIR"
mv "$REMOTE_TMP_PATH" "$REMOTE_DEPLOY_PATH"
chmod 644 "$REMOTE_DEPLOY_PATH"
# Alte APKs aufräumen, die letzten 3 Versionen als Backup behalten
cd "$MAIN_SERVER_DEPLOY_DIR"
ls -t ./*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f
REMOTE_SCRIPT

log "Fertig. APK deployed: ${MAIN_SERVER_IP}:${REMOTE_DEPLOY_PATH}"
log "Download-Seite: https://letheapp.de/nocturn.php"
