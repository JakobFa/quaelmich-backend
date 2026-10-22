#!/usr/bin/env bash
# Einladungscodes für quaelmich verwalten – nur für den Betreiber.
#
#   scripts/invite.sh neu "für Lina"     neuen Code + Link erstellen (gilt 1x, 14 Tage)
#   scripts/invite.sh liste              alle Codes mit Status anzeigen
#   scripts/invite.sh zurueckziehen 7    offenen Code Nr. 7 ungültig machen
#
# Das Geheimnis (INTERVALS_SYNC_TOKEN von Render) wird aus dem macOS-Schlüsselbund gelesen:
#   security add-generic-password -a "$USER" -s quaelmich-sync-token -w
# Ist dort nichts hinterlegt, fragt das Skript verdeckt danach.
set -euo pipefail

API="${QUAELMICH_API:-https://api.quaelmich.com}"
FRONTEND="${QUAELMICH_FRONTEND:-https://quaelmich.com}"

case "${1:-}" in
  neu|liste|zurueckziehen) ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac

token="$(security find-generic-password -s quaelmich-sync-token -w 2>/dev/null || true)"
if [[ -z "$token" ]]; then
  read -rsp "Sync-Token: " token
  echo
fi

# Render schläft auf dem Free-Tier – der erste Aufruf kann bis zu einer Minute dauern.
call() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS --max-time 90 -X "$method" -H "X-Sync-Token: $token" -w '\n%{http_code}')
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out
  out="$(curl "${args[@]}" "$API$path")"
  status="${out##*$'\n'}"
  response="${out%$'\n'*}"
  if [[ "$status" == 404 ]]; then
    echo "Nicht gefunden – Token falsch oder (bei zurueckziehen) die Nummer gibt es nicht." >&2
    exit 1
  elif [[ "$status" -ge 400 ]]; then
    echo "Fehler $status: $response" >&2
    exit 1
  fi
}

case "${1:-}" in
  neu)
    note="${2:-}"
    body="$(python3 -c 'import json,sys; print(json.dumps({"note": sys.argv[1]}))' "$note")"
    call POST /internal/invitations "$body"
    python3 -c '
import json, sys, urllib.parse
inv = json.loads(sys.argv[1])
code, nr, until = inv["code"], inv["id"], inv["expiresAt"][:10]
print(f"Code:  {code}   (Nr. {nr}, gültig bis {until})")
print(f"Link:  {sys.argv[2]}/login?mode=register&code={urllib.parse.quote(code)}")
print("Der Code wird nur jetzt angezeigt – quaelmich speichert ihn nicht im Klartext.")
' "$response" "$FRONTEND"
    ;;
  liste)
    call GET /internal/invitations
    python3 -c '
import json, sys
labels = {"OPEN": "offen", "USED": "benutzt", "EXPIRED": "abgelaufen", "REVOKED": "zurückgezogen"}
rows = json.loads(sys.argv[1])
if not rows:
    print("Noch keine Codes.")
for r in rows:
    who = " von " + r["usedBy"] if r.get("usedBy") else ""
    nr, last, status, created = r["id"], r["lastChars"], labels[r["status"]], r["createdAt"][:10]
    note = r.get("note") or ""
    print(f"{nr:>3}  …{last}  {status:<13} {created}  {note}{who}")
' "$response"
    ;;
  zurueckziehen)
    [[ -n "${2:-}" ]] || { echo "Welche Nummer? (siehe: scripts/invite.sh liste)" >&2; exit 1; }
    call DELETE "/internal/invitations/$2"
    echo "Code Nr. $2 ist jetzt ungültig."
    ;;
esac
