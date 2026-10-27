#!/usr/bin/env bash
# Erhöht die Versionsnummer, committet, pusht, wartet auf den GitHub-Actions-Build
# und deployt die signierte Release-APK auf letheapp.de sowie ins F-Droid-Repo.
#
# Aufruf:
#   scripts/release-deploy.sh                 # Minor-Bump (0.6.0 -> 0.7.0), Standardfall
#   scripts/release-deploy.sh patch           # Patch-Bump (0.6.0 -> 0.6.1)
#   scripts/release-deploy.sh major           # Major-Bump (0.6.0 -> 1.0.0)
#   scripts/release-deploy.sh 1.2.3           # explizite Versionsnummer
#   scripts/release-deploy.sh resume          # nach Abbruch fortsetzen, OHNE neue Version zu ziehen
#   scripts/release-deploy.sh status          # nur den Fortschritt des laufenden/letzten Laufs anzeigen
#
# Das Skript klinkt sich beim ersten Aufruf per setsid/nohup komplett vom aufrufenden
# Terminal/Prozess ab (siehe "Selbst-Daemonisierung" unten) und läuft danach auf dem
# Pi unabhängig weiter -- auch wenn die aufrufende Claude-Code-Session/Shell beendet
# wird (Session-Limit, Neustart, Verbindungsabbruch). Fortschritt + Ergebnis stehen in
# $STATE_FILE bzw. $LOG_FILE und lassen sich jederzeit mit "status" abfragen.
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
STATE_FILE="/tmp/nocturn-release-deploy.state"
LOG_FILE="/tmp/nocturn-release-deploy.log"
LOCK_FILE="/tmp/nocturn-release-deploy.lock"

log() { echo "[release-deploy] $(date '+%Y-%m-%d %H:%M:%S') $*"; }
die() { echo "[release-deploy] $(date '+%Y-%m-%d %H:%M:%S') FEHLER: $*" >&2; set_phase "FAILED: $*"; exit 1; }

set_phase() {
    # Schreibt den aktuellen Fortschritt in die Statusdatei, damit "status" und künftige
    # Läufe (nach einem Abbruch) wissen, wo wir stehen -- ohne das ganze Skript zu parsen.
    local phase="$1"
    # %q quotet den Wert shell-sicher -- wichtig, da "resume" diese Datei per "source"
    # wieder einliest: eine ungequotete Fehlermeldung mit Leerzeichen/Sonderzeichen in
    # PHASE würde sonst als Befehl ausgeführt statt als reine Variablenzuweisung gelesen.
    {
        printf 'PHASE=%q\n' "$phase"
        printf 'TIMESTAMP=%q\n' "$(date '+%Y-%m-%d %H:%M:%S')"
        [[ -n "${NEW_NAME:-}" ]] && printf 'NEW_NAME=%q\n' "$NEW_NAME"
        [[ -n "${NEW_CODE:-}" ]] && printf 'NEW_CODE=%q\n' "$NEW_CODE"
        [[ -n "${HEAD_SHA:-}" ]] && printf 'HEAD_SHA=%q\n' "$HEAD_SHA"
        [[ -n "${RUN_ID:-}" ]] && printf 'RUN_ID=%q\n' "$RUN_ID"
        true
    } > "$STATE_FILE"
}

# ---------- Modus "status": nur Fortschritt zeigen, nichts tun ----------
if [[ "${1:-}" == "status" ]]; then
    if [[ -f "$STATE_FILE" ]]; then
        cat "$STATE_FILE"
    else
        echo "Kein Deploy-Lauf bekannt (keine $STATE_FILE vorhanden)."
    fi
    echo "---- letzte Log-Zeilen ($LOG_FILE) ----"
    tail -n 40 "$LOG_FILE" 2>/dev/null || echo "(kein Log vorhanden)"
    exit 0
fi

# ---------- Selbst-Daemonisierung ----------
# Beim allerersten (nicht-daemonisierten) Aufruf startet sich das Skript selbst per
# setsid+nohup komplett losgelöst vom aufrufenden Prozessbaum neu und beendet sich dann
# sofort -- der eigentliche Deploy läuft danach als eigene Session weiter, unabhängig
# davon, ob die aufrufende Shell/Claude-Code-Session später beendet wird.
if [[ "${NOCTURN_DEPLOY_DAEMONIZED:-}" != "1" ]]; then
    # Vor dem Daemonisieren prüfen, ob schon ein Lauf aktiv ist -- sonst würde das Log
    # eines noch laufenden Deploys hier einfach überschrieben, bevor der eigentliche
    # flock-Check im Kind-Prozess überhaupt zum Zug kommt.
    if command -v flock >/dev/null && ! flock -n "$LOCK_FILE" true 2>/dev/null; then
        echo "[release-deploy] Es läuft bereits ein Deploy-Vorgang. Status: ${BASH_SOURCE[0]} status"
        exit 1
    fi
    : > "$LOG_FILE"
    NOCTURN_DEPLOY_DAEMONIZED=1 setsid nohup "${BASH_SOURCE[0]}" "$@" >> "$LOG_FILE" 2>&1 < /dev/null &
    DAEMON_PID=$!
    disown
    echo "[release-deploy] Gestartet als eigenständiger Hintergrundprozess (PID $DAEMON_PID)."
    echo "[release-deploy] Läuft unabhängig von dieser Shell/Session weiter."
    echo "[release-deploy] Fortschritt prüfen mit: ${BASH_SOURCE[0]} status"
    echo "[release-deploy] Live mitlesen mit:      tail -f $LOG_FILE"
    exit 0
fi

# Ab hier laeuft der eigentliche, daemonisierte Deploy-Prozess.
exec 9>"$LOCK_FILE"
flock -n 9 || die "Es läuft bereits ein anderer Deploy-Vorgang (siehe $LOCK_FILE)."

[[ -f "$TOKEN_FILE" ]] || die "GitHub-Token nicht gefunden unter $TOKEN_FILE"
GH_TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
[[ -n "$GH_TOKEN" ]] || die "GitHub-Token-Datei ist leer"

[[ -f "$CREDENTIALS_FILE" ]] || die "Server-Zugangsdaten nicht gefunden unter $CREDENTIALS_FILE"
# shellcheck source=/dev/null
source "$CREDENTIALS_FILE"
[[ -n "${MAIN_SERVER_IP:-}" && -n "${MAIN_SERVER_USER:-}" && -n "${MAIN_SERVER_PASSWORD:-}" && -n "${MAIN_SERVER_DEPLOY_DIR:-}" ]] \
    || die "Server-Zugangsdaten in $CREDENTIALS_FILE unvollständig"
command -v sshpass >/dev/null || die "sshpass ist nicht installiert"
command -v flock >/dev/null || die "flock ist nicht installiert"

cd "$REPO_DIR"
[[ -d .git ]] || die "$REPO_DIR ist kein Git-Repository"

COMMON_SSH_OPTS=(-o StrictHostKeyChecking=no -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=6)
SSH_PORT_OPT=(-p "${MAIN_SERVER_PORT:-22}")
SCP_PORT_OPT=(-P "${MAIN_SERVER_PORT:-22}")

ssh_remote() { sshpass -p "$MAIN_SERVER_PASSWORD" ssh "${COMMON_SSH_OPTS[@]}" "${SSH_PORT_OPT[@]}" "${MAIN_SERVER_USER}@${MAIN_SERVER_IP}" "$@"; }
scp_to_remote() { sshpass -p "$MAIN_SERVER_PASSWORD" scp "${COMMON_SSH_OPTS[@]}" "${SCP_PORT_OPT[@]}" "$@"; }

# Zweiter Satz Helper für den (optionalen) Nextcloud-Testserver -- separater Host, daher
# eigenes Passwort/eigene Port-Optionen statt der MAIN_SERVER_*-Variablen oben.
NC_SSH_PORT_OPT=(-p "${NEXTCLOUD_SERVER_PORT:-22}")
NC_SCP_PORT_OPT=(-P "${NEXTCLOUD_SERVER_PORT:-22}")
ssh_nc() { sshpass -p "$NEXTCLOUD_SERVER_PASSWORD" ssh "${COMMON_SSH_OPTS[@]}" "${NC_SSH_PORT_OPT[@]}" "${NEXTCLOUD_SERVER_USER}@${NEXTCLOUD_SERVER_IP}" "$@"; }
scp_to_nc() { sshpass -p "$NEXTCLOUD_SERVER_PASSWORD" scp "${COMMON_SSH_OPTS[@]}" "${NC_SCP_PORT_OPT[@]}" "$@"; }

retry() {
    # retry <Versuche> <Wartezeit-Sekunden> -- <Befehl...>
    # Für Netzwerk-Operationen (ssh/scp), die bei einer wackeligen Verbindung
    # (z.B. WLAN auf dem Pi) auch mal transient fehlschlagen können.
    local attempts="$1" wait_s="$2"; shift 2
    [[ "$1" == "--" ]] && shift
    local n=1
    while true; do
        if "$@"; then return 0; fi
        if (( n >= attempts )); then return 1; fi
        log "Versuch $n/$attempts fehlgeschlagen, erneuter Versuch in ${wait_s}s..."
        sleep "$wait_s"
        n=$((n + 1))
    done
}

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

RESUME=0
if [[ "${1:-}" == "resume" ]]; then
    RESUME=1
fi

if [[ "$RESUME" == "1" ]]; then
    [[ -f "$STATE_FILE" ]] || die "Kein vorheriger Lauf gefunden ($STATE_FILE fehlt) -- resume nicht möglich."
    # shellcheck source=/dev/null
    source "$STATE_FILE"
    [[ -n "${NEW_NAME:-}" && -n "${NEW_CODE:-}" && -n "${HEAD_SHA:-}" ]] \
        || die "Statusdatei $STATE_FILE ist unvollständig -- resume nicht möglich."
    CURRENT_HEAD="$(git rev-parse HEAD)"
    [[ "$CURRENT_HEAD" == "$HEAD_SHA" ]] \
        || die "HEAD hat sich seit dem letzten Lauf geändert (war $HEAD_SHA, ist jetzt $CURRENT_HEAD) -- resume nicht sinnvoll, bitte neu starten."
    log "Setze vorherigen Lauf fort: Version $NEW_NAME (Code $NEW_CODE), Commit $HEAD_SHA"
    set_phase "resumed"
else
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
            *) die "Unbekannter Bump-Typ '$BUMP' (erwartet: major|minor|patch|X.Y.Z|resume|status)" ;;
        esac
    fi
    NEW_CODE=$((CURRENT_CODE + 1))

    log "Version $CURRENT_NAME (Code $CURRENT_CODE) -> $NEW_NAME (Code $NEW_CODE)"
    set_phase "bumping_version"

    # ---------- 2. Gradle-Datei anpassen (nur der jeweils erste Treffer in der Datei) ----------
    sed -i "0,/versionCode = [0-9]\+/s//versionCode = $NEW_CODE/" "$GRADLE_FILE"
    sed -i "0,/versionName = \"[0-9.]\+\"/s//versionName = \"$NEW_NAME\"/" "$GRADLE_FILE"
    grep -q "versionCode = $NEW_CODE" "$GRADLE_FILE" || die "versionCode-Ersetzung fehlgeschlagen"
    grep -q "versionName = \"$NEW_NAME\"" "$GRADLE_FILE" || die "versionName-Ersetzung fehlgeschlagen"

    # ---------- 2b. NocTurn-Kalender-App-Version mitziehen ----------
    # Stand lange statisch auf "6.7.0-dev.1" über viele Deploys hinweg -- Nextclouds
    # Migrations-Runner (occ upgrade) erkennt neue, auszuführende Migrationen aber nur über
    # einen Versionsunterschied zwischen info.xml und der in oc_appconfig.installed_version
    # gespeicherten Version. Eine nie wechselnde Versionsnummer bedeutete: neue Migrationen
    # wurden zwar als Datei ausgeliefert, aber nie tatsächlich ausgeführt (fehlende Tabellen/
    # Spalten blieben bis zu einem manuellen Eingriff liegen). Ab jetzt an den monoton
    # steigenden NocTurn-versionCode gekoppelt, damit jeder Deploy garantiert einen echten
    # Versionssprung auslöst.
    NK_VERSION="6.7.0-dev.${NEW_CODE}"
    NK_INFO_XML="$REPO_DIR/Nocturn-Kalender/appinfo/info.xml"
    NK_PACKAGE_JSON="$REPO_DIR/Nocturn-Kalender/package.json"
    sed -i "s|<version>[^<]*</version>|<version>${NK_VERSION}</version>|" "$NK_INFO_XML"
    sed -i "s|\"version\": \"[^\"]*\"|\"version\": \"${NK_VERSION}\"|" "$NK_PACKAGE_JSON"

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
    set_phase "pushing"
    GH_AUTH_B64="$(printf 'x-access-token:%s' "$GH_TOKEN" | base64 -w0)"
    retry 3 10 -- git -c http.extraheader="Authorization: Basic ${GH_AUTH_B64}" push origin main \
        || die "git push nach main fehlgeschlagen"
    set_phase "pushed"
fi

# ---------- 4. Auf den passenden Workflow-Run warten ----------
log "Warte auf den Start des GitHub-Actions-Runs für $HEAD_SHA..."
set_phase "waiting_for_run_start"
RUN_ID=""
START_TIME=$(date +%s)
while [[ -z "$RUN_ID" ]]; do
    (( $(date +%s) - START_TIME > POLL_TIMEOUT )) && die "Timeout beim Warten auf den Workflow-Start"
    RESPONSE="$(gh_api "/actions/workflows/$WORKFLOW_FILE/runs?branch=main&event=push&per_page=10" || true)"
    RUN_ID="$(printf '%s' "$RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
sha = sys.argv[1]
for run in data.get('workflow_runs', []):
    if run['head_sha'] == sha:
        print(run['id'])
        break
" "$HEAD_SHA" 2>/dev/null || true)"
    [[ -n "$RUN_ID" ]] || sleep "$POLL_INTERVAL"
done
log "Workflow-Run gefunden: https://github.com/$GH_REPO/actions/runs/$RUN_ID"
set_phase "run_found"

# ---------- 5. Auf Abschluss des Runs warten ----------
STATUS=""
START_TIME=$(date +%s)
while [[ "$STATUS" != "completed" ]]; do
    (( $(date +%s) - START_TIME > POLL_TIMEOUT )) && die "Timeout beim Warten auf den Build-Abschluss"
    RUN_JSON="$(gh_api "/actions/runs/$RUN_ID" || true)"
    STATUS="$(echo "$RUN_JSON" | json_get ".status" 2>/dev/null || true)"
    [[ "$STATUS" == "completed" ]] || sleep "$POLL_INTERVAL"
done
CONCLUSION="$(echo "$RUN_JSON" | json_get ".conclusion")"
[[ "$CONCLUSION" == "success" ]] || die "Build abgeschlossen mit Ergebnis '$CONCLUSION' - kein Deploy. Siehe https://github.com/$GH_REPO/actions/runs/$RUN_ID"
log "Build erfolgreich."
set_phase "build_success"

# ---------- 6. Release + APK-Asset finden ----------
log "Suche GitHub Release für Version $NEW_NAME..."
RELEASE_JSON=""
START_TIME=$(date +%s)
while [[ -z "$RELEASE_JSON" ]]; do
    (( $(date +%s) - START_TIME > 600 )) && die "Timeout beim Warten auf das GitHub Release"
    RELEASES="$(gh_api "/releases?per_page=10" || true)"
    RELEASE_JSON="$(printf '%s' "$RELEASES" | python3 -c "
import sys, json
data = json.load(sys.stdin)
prefix = sys.argv[1]
for rel in data:
    if rel['tag_name'].startswith(prefix):
        print(json.dumps(rel))
        break
" "v${NEW_NAME}-" 2>/dev/null || true)"
    [[ -n "$RELEASE_JSON" ]] || sleep 10
done
set_phase "release_found"

ASSET_ID=""
START_TIME=$(date +%s)
while [[ -z "$ASSET_ID" ]]; do
    (( $(date +%s) - START_TIME > 300 )) && die "Kein APK-Asset im Release gefunden (Timeout)"
    ASSET_ID="$(echo "$RELEASE_JSON" | python3 -c "
import sys, json
rel = json.load(sys.stdin)
for a in rel['assets']:
    if a['name'].endswith('.apk'):
        print(a['id'])
        break
" 2>/dev/null || true)"
    if [[ -z "$ASSET_ID" ]]; then
        sleep 10
        RELEASE_JSON="$(gh_api "/releases/$(echo "$RELEASE_JSON" | json_get '.id')" || true)"
    fi
done
log "APK-Asset gefunden (ID $ASSET_ID), lade herunter..."
set_phase "asset_found"

# ---------- 7. APK herunterladen ----------
TMP_APK="$(mktemp --suffix=.apk)"
download_apk() {
    local http_code
    http_code="$(curl -sS -L -w '%{http_code}' -o "$TMP_APK" \
        -H "Authorization: Bearer $GH_TOKEN" \
        -H "Accept: application/octet-stream" \
        "https://api.github.com/repos/$GH_REPO/releases/assets/$ASSET_ID")"
    [[ "$http_code" == "200" ]] && [[ -s "$TMP_APK" ]]
}
retry 5 15 -- download_apk || die "APK-Download fehlgeschlagen"
set_phase "apk_downloaded"

# ---------- 8. Auf den Hauptserver hochladen ----------
REMOTE_APK_NAME="NocTurn-${NEW_NAME}.apk"
REMOTE_TMP_PATH="/tmp/${REMOTE_APK_NAME}"
REMOTE_DEPLOY_PATH="${MAIN_SERVER_DEPLOY_DIR}/${REMOTE_APK_NAME}"

log "Lade APK zum Hauptserver hoch ($MAIN_SERVER_IP:$REMOTE_DEPLOY_PATH)..."
retry 5 20 -- scp_to_remote "$TMP_APK" "${MAIN_SERVER_USER}@${MAIN_SERVER_IP}:${REMOTE_TMP_PATH}" \
    || die "scp-Upload zum Hauptserver fehlgeschlagen (auch nach mehreren Versuchen)"
rm -f "$TMP_APK"
set_phase "apk_uploaded"

# Als einzelner Kommando-String statt Heredoc, damit "retry" das bei einem abgerissenen
# Versuch gefahrlos nochmal ausführen kann (ein Heredoc würde beim zweiten Versuch mit
# bereits leerem Stdin ankommen). "if [ -f ... ]" macht den mv-Schritt idempotent, falls
# der vorherige Versuch die Datei schon verschoben, die Verbindung aber danach abgerissen
# hatte.
REMOTE_DEPLOY_CMD="set -e; mkdir -p '${MAIN_SERVER_DEPLOY_DIR}'; if [ -f '${REMOTE_TMP_PATH}' ]; then mv '${REMOTE_TMP_PATH}' '${REMOTE_DEPLOY_PATH}'; fi; chmod 644 '${REMOTE_DEPLOY_PATH}'; cd '${MAIN_SERVER_DEPLOY_DIR}' && ls -t ./*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f"
retry 3 10 -- ssh_remote "$REMOTE_DEPLOY_CMD" || die "Deploy-Befehl auf dem Hauptserver fehlgeschlagen"

log "Fertig. APK deployed: ${MAIN_SERVER_IP}:${REMOTE_DEPLOY_PATH}"
log "Download-Seite: https://letheapp.de/nocturn.php"
set_phase "website_deployed"

# ---------- 9. F-Droid-Repo aktualisieren ----------
# Nutzt dasselbe, bereits laufende F-Droid-Repo von Lethe (fdroid.letheapp.de) auf dem
# Hauptserver statt eines eigenen Repos -- NocTurn und Lethe teilen sich Repo-Verzeichnis
# und Signierschluessel (/root/my-fdroid-repo), damit Nutzer beide Apps aus einer Quelle
# aktuell halten koennen. Laeuft unconditional bei jedem Release (kein Flag nötig), da
# dieses Skript immer nur die eine NocTurn-APK baut.
log "Aktualisiere F-Droid-Repo..."
FDROID_PACKAGE="de.letheapp.nocturn"
FDROID_REPO_DIR="/root/my-fdroid-repo/repo"
FDROID_META_FILE="/root/my-fdroid-repo/metadata/${FDROID_PACKAGE}.yml"
FDROID_APK_NAME="${FDROID_PACKAGE}_${NEW_CODE}.apk"

retry 5 15 -- ssh_remote "cp '${REMOTE_DEPLOY_PATH}' '${FDROID_REPO_DIR}/${FDROID_APK_NAME}'" \
    || die "Kopieren der APK ins F-Droid-Repo fehlgeschlagen"
set_phase "fdroid_apk_copied"

FDROID_META_EXISTS="$(retry 3 10 -- ssh_remote "[ -f '${FDROID_META_FILE}' ] && echo yes || echo no")"
if [[ "$FDROID_META_EXISTS" == "no" ]]; then
    log "Lege F-Droid-Metadata für ${FDROID_PACKAGE} an..."
    TMP_YAML="$(mktemp)"
    cat > "$TMP_YAML" <<YAML_EOF
Categories:
  - System
License: Proprietary
WebSite: "https://letheapp.de/nocturn.php"
Name: NocTurn
Summary: Wecker, Kalender & Schichtplaner
Description: |
  NocTurn ist ein Wecker, der dich wirklich aufstehen laesst: Aufsteh-Erkennung
  per Sensorik und Health Connect, eskalierender Notfall-Alarm mit
  Zuordnungs-Puzzle, dazu ein Schichtkalender mit Rotationen,
  Schueler/Student-Stundenplan-Modus und Foto-Import per OCR.

AutoUpdateMode: None
UpdateCheckMode: None
CurrentVersion: '${NEW_NAME}'
CurrentVersionCode: ${NEW_CODE}
YAML_EOF
    retry 5 15 -- scp_to_remote "$TMP_YAML" "${MAIN_SERVER_USER}@${MAIN_SERVER_IP}:${FDROID_META_FILE}" \
        || die "Hochladen der F-Droid-Metadata fehlgeschlagen"
    rm -f "$TMP_YAML"
else
    retry 3 10 -- ssh_remote "
        sed -i \"s|^CurrentVersion:.*|CurrentVersion: '${NEW_NAME}'|\" '${FDROID_META_FILE}'
        sed -i \"s|^CurrentVersionCode:.*|CurrentVersionCode: ${NEW_CODE}|\" '${FDROID_META_FILE}'
    " || die "Aktualisieren der F-Droid-Metadata fehlgeschlagen"
fi
set_phase "fdroid_metadata_updated"

# "fdroid update --clean" kann bei >300 APKs im Repo viele Minuten dauern. Statt die ganze
# Zeit eine einzelne SSH-Verbindung offen zu halten (die bei einer wackeligen Pi-Verbindung
# zwischendurch abreissen kann und den Rebuild dann abbricht), wird der Lauf auf dem Server
# selbst per nohup im Hintergrund gestartet und von hier aus nur noch per kurzen,
# unabhaengigen SSH-Befehlen abgefragt -- ein einzelner Verbindungsabbruch beim Poll killt
# den Rebuild dann nicht mehr.
log "Starte F-Droid-Index-Rebuild auf dem Server (läuft dort unabhängig im Hintergrund)..."
# pgrep-Check macht den Start resume-sicher: läuft (z.B. nach einem Abbruch und "resume")
# bereits ein Rebuild auf dem Server, wird kein zweiter, konkurrierender Lauf gestartet.
FDROID_START_CMD="cd /root/my-fdroid-repo && if pgrep -f 'fdroid update' >/dev/null 2>&1; then echo already_running; else rm -f fdroid_update.exit; nohup bash -c 'fdroid update --clean; echo \$? > fdroid_update.exit' > fdroid_update.log 2>&1 < /dev/null & disown; sleep 1; echo started; fi"
retry 3 10 -- ssh_remote "$FDROID_START_CMD" \
    || die "Konnte F-Droid-Index-Rebuild auf dem Server nicht starten"
set_phase "fdroid_update_started"

FDROID_TIMEOUT=1800
START_TIME=$(date +%s)
FDROID_EXIT=""
while [[ -z "$FDROID_EXIT" ]]; do
    (( $(date +%s) - START_TIME > FDROID_TIMEOUT )) && die "Timeout beim Warten auf den F-Droid-Index-Rebuild (läuft ggf. noch auf dem Server, siehe /root/my-fdroid-repo/fdroid_update.log)"
    FDROID_EXIT="$(retry 5 15 -- ssh_remote "[ -f /root/my-fdroid-repo/fdroid_update.exit ] && cat /root/my-fdroid-repo/fdroid_update.exit || true" || true)"
    [[ -n "$FDROID_EXIT" ]] || sleep 20
done
[[ "$FDROID_EXIT" == "0" ]] || die "fdroid update --clean ist mit Exit-Code $FDROID_EXIT fehlgeschlagen (siehe /root/my-fdroid-repo/fdroid_update.log auf dem Server)"

log "NocTurn im F-Droid-Repo aktualisiert: ${FDROID_APK_NAME}"
log "F-Droid-Repo-URL (in F-Droid-Client eintragen): https://fdroid.letheapp.de/repo"
set_phase "fdroid_done"

# ---------- 10. NocTurn-Kalender-App auf dem Nextcloud-Testserver aktualisieren ----------
# Nextcloud selbst bietet KEIN per-App-Update-Feed (kein <releases>-Tag in info.xml, keine
# pro-App "appstoreurl") -- das native Update-System ist ein einziger, instanzweiter
# Appstore-Endpunkt (config.php "appstoreurl", Default https://apps.nextcloud.com/api/v1),
# der für ALLE Apps gleichzeitig gilt. Einen eigenen Mirror dieses Formats zu bauen, würde
# bedeuten, auch alle anderen Apps der Instanz darüber zu bedienen -- unverhältnismäßig für
# eine einzelne private App auf einem selbst kontrollierten Server. Der praktische Ersatz
# hier: Push-Deploy direkt in diesem Skript, bei jedem Release automatisch, ganz ohne
# manuellen Klick auf "Update" in der Nextcloud-Apps-Seite.
if [[ -n "${NEXTCLOUD_SERVER_IP:-}" && -n "${NEXTCLOUD_SERVER_USER:-}" && -n "${NEXTCLOUD_SERVER_PASSWORD:-}" && -n "${NEXTCLOUD_APP_DIR:-}" ]]; then
    log "Baue NocTurn-Kalender-App (npm/composer) für den Nextcloud-Deploy..."
    NK_DIR="$REPO_DIR/Nocturn-Kalender"
    [[ -d "$NK_DIR" ]] || die "Nocturn-Kalender-Ordner nicht gefunden unter $NK_DIR"
    log "NocTurn-Kalender-App-Version: ${NK_VERSION:-$(grep -oP '(?<=<version>)[^<]+' "$NK_DIR/appinfo/info.xml")}"

    (cd "$NK_DIR" && npm run build) || die "npm run build für Nocturn-Kalender fehlgeschlagen"
    (cd "$NK_DIR" && composer install --no-dev --optimize-autoloader) || die "composer install für Nocturn-Kalender fehlgeschlagen"
    set_phase "nextcloud_app_built"

    log "Packe und lade NocTurn-Kalender-App zum Nextcloud-Server hoch ($NEXTCLOUD_SERVER_IP)..."
    NK_TAR="$(mktemp --suffix=.tar.gz)"
    (cd "$NK_DIR" && tar czf "$NK_TAR" \
        --exclude='node_modules' --exclude='src' --exclude='tests' --exclude='.git' \
        appinfo composer css img js l10n lib templates vendor) \
        || die "Packen der Nocturn-Kalender-App fehlgeschlagen"
    NK_REMOTE_TMP="/tmp/nocturn-kalender-deploy.tar.gz"
    retry 5 20 -- scp_to_nc "$NK_TAR" "${NEXTCLOUD_SERVER_USER}@${NEXTCLOUD_SERVER_IP}:${NK_REMOTE_TMP}" \
        || die "scp-Upload der Nocturn-Kalender-App zum Nextcloud-Server fehlgeschlagen"
    rm -f "$NK_TAR"
    set_phase "nextcloud_app_uploaded"

    # Wartung an/aus statt eines reinen Datei-Austauschs ohne Absicherung: verhindert, dass
    # ein Nutzer mitten im Austausch halbe/inkonsistente App-Dateien ausgeliefert bekommt,
    # und zwingt Nextcloud danach zu einem sauberen Cache-Rebuild der App-Routen/Scripts.
    # Der abschließende "occ nocturnkalender:migrate" (eigener, app-interner occ-Befehl, siehe
    # lib/Command/Migrate.php) führt gezielt nur die NocTurn-Kalender-eigenen DB-Migrationen
    # (calendar_noct_*-Tabellen) aus, OHNE Nextcloud-Core oder andere Apps anzufassen -- das
    # allgemeine "occ upgrade" hätte dasselbe geleistet, aber bei jedem Deploy riskiert, einen
    # eventuell noch anstehenden, unabhängigen Core-Update mit auszulösen. Vorher wurden neue
    # Migrationen nur als Datei ausgeliefert, aber nie tatsächlich ausgeführt (die statische
    # info.xml-Versionsnummer änderte sich nie, und reines Datei-Kopieren/Wartungsmodus-Toggle
    # löst in Nextcloud von sich aus keinen Migrations-Lauf aus) -- fehlende Tabellen/Spalten
    # blieben dadurch bis zu einem manuellen Eingriff liegen.
    # Backup bewusst AUSSERHALB von .../apps/ (z. B. .old direkt neben dem App-Ordner läge selbst
    # noch innerhalb des von Nextcloud gescannten apps-Verzeichnisses) -- Nextclouds App-Scanner
    # las ein dort liegendes "nocturnkalender.old" sonst als eigene, kaputte Pseudo-App
    # ("nocturnkalender.old"/"nocturnkalender.old2" tauchten in occ app:list auf) und brachte
    # dadurch ein nachfolgendes "occ upgrade" mit "Could not find path for nocturnkalenderold"
    # zum Abbruch -- live auf thestealth.de beobachtet und bereinigt.
    NK_BACKUP_DIR="/var/www/nocturnkalender-backups"
    NK_DEPLOYED_VERSION="${NK_VERSION:-$(grep -oP '(?<=<version>)[^<]+' "$NK_DIR/appinfo/info.xml")}"
    # Reihenfolge ist hier bewusst nicht die naheliegende (migrate, dann Version setzen,
    # dann Wartungsmodus aus) -- das fuehrt live zuverlaessig in einen haengenden
    # Wartungsmodus. Grund: Sobald die neuen Dateien kopiert sind, weicht die Version in
    # appinfo/info.xml von "installed_version" in oc_appconfig ab, und Nextcloud stuft die
    # App dadurch sofort als "braucht Upgrade" ein -- in diesem Zustand laedt es GAR KEINE
    # app-eigenen occ-Befehle mehr (auch waehrend des separat aktiven Wartungsmodus laedt es
    # ebenfalls keine App-Befehle: "Nextcloud is in maintenance mode, no apps are loaded").
    # "nocturnkalender:migrate" ist aber genau so ein app-eigener Befehl und faellt damit in
    # beiden Zustaenden komplett weg ("There are no commands defined in the
    # "nocturnkalender" namespace") -- das brachte den vorherigen Ablauf per "set -e" vor dem
    # abschliessenden "maintenance:mode --off" zum Abbruch, der Server blieb dauerhaft im
    # Wartungsmodus haengen (live auf thestealth.de wiederholt beobachtet und behoben).
    # "config:app:set"/"maintenance:mode" sind dagegen Nextcloud-Kernbefehle und bleiben in
    # jedem Zustand nutzbar. Deshalb jetzt: Version-Mismatch per "config:app:set" sofort
    # beheben (behebt "needsDbUpgrade", noch waehrend Wartungsmodus an), erst danach
    # Wartungsmodus aus (die App gilt ab hier wieder als aktuell und laedt normal), und erst
    # jetzt den eigentlichen Migrations-Befehl ausfuehren (setzt "installed_version" intern
    # ohnehin nochmal, siehe Migrate.php -- hier nur, um das kurze Fenster zwischen
    # Dateikopie und Versions-Abgleich so kurz wie moeglich zu halten).
    NK_DEPLOY_CMD="set -e; sudo -u www-data php '${NEXTCLOUD_OCC_PATH}' maintenance:mode --on; \
        sudo mkdir -p '${NK_BACKUP_DIR}'; \
        sudo rm -rf '${NK_BACKUP_DIR}/nocturnkalender.old' && sudo cp -a '${NEXTCLOUD_APP_DIR}' '${NK_BACKUP_DIR}/nocturnkalender.old' 2>/dev/null || true; \
        sudo mkdir -p '${NEXTCLOUD_APP_DIR}'; \
        sudo tar xzf '${NK_REMOTE_TMP}' -C '${NEXTCLOUD_APP_DIR}'; \
        sudo rm -f '${NK_REMOTE_TMP}'; \
        sudo chown -R www-data:www-data '${NEXTCLOUD_APP_DIR}'; \
        sudo -u www-data php '${NEXTCLOUD_OCC_PATH}' config:app:set nocturnkalender installed_version --value='${NK_DEPLOYED_VERSION}'; \
        sudo -u www-data php '${NEXTCLOUD_OCC_PATH}' maintenance:mode --off; \
        sudo -u www-data php '${NEXTCLOUD_OCC_PATH}' nocturnkalender:migrate"
    retry 3 15 -- ssh_nc "$NK_DEPLOY_CMD" \
        || die "Deploy der Nocturn-Kalender-App auf dem Nextcloud-Server fehlgeschlagen (Wartungsmodus ggf. manuell prüfen/ausschalten: occ maintenance:mode --off)"
    set_phase "nextcloud_app_deployed"
    log "NocTurn-Kalender-App auf ${NEXTCLOUD_SERVER_IP}:${NEXTCLOUD_APP_DIR} aktualisiert (Version ${NEW_NAME})."
else
    log "Keine NEXTCLOUD_SERVER_*-Zugangsdaten in $CREDENTIALS_FILE hinterlegt -- Nextcloud-Kalender-Deploy übersprungen."
fi

set_phase "SUCCESS"
