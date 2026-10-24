#!/usr/bin/env bash
# Feedback aus der App abrufen – nur für den Betreiber.
#
#   scripts/feedback.sh neu              offenes Feedback anzeigen (Standard)
#   scripts/feedback.sh alle             alles, auch Erledigtes
#   scripts/feedback.sh erledigt 7       Feedback Nr. 7 als erledigt markieren
#
# Das Geheimnis (ADMIN_TOKEN von Render) wird aus dem macOS-Schlüsselbund gelesen:
#   security add-generic-password -a "$USER" -s quaelmich-admin-token -w
# Ist dort nichts hinterlegt, fragt das Skript verdeckt danach.
set -euo pipefail

API="${QUAELMICH_API:-https://api.quaelmich.com}"
mode="${1:-neu}"
case "$mode" in
  neu|alle|erledigt) ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac

token="$(security find-generic-password -s quaelmich-admin-token -w 2>/dev/null || true)"
if [[ -z "$token" ]]; then
  read -rsp "Admin-Token: " token
  echo
fi

call() {
  local method="$1" path="$2" out
  out="$(curl -sS --max-time 60 -X "$method" -H "X-Admin-Token: $token" -w '\n%{http_code}' "$API$path")"
  status="${out##*$'\n'}"
  response="${out%$'\n'*}"
  if [[ "$status" == 404 ]]; then
    echo "Nicht gefunden – Token falsch oder die Nummer gibt es nicht." >&2
    exit 1
  elif [[ "$status" -ge 400 ]]; then
    echo "Fehler $status" >&2
    exit 1
  fi
}

if [[ "$mode" == erledigt ]]; then
  [[ -n "${2:-}" ]] || { echo "Welche Nummer? (siehe: scripts/feedback.sh neu)" >&2; exit 1; }
  call PUT "/internal/feedback/$2/done"
  echo "Feedback Nr. $2 ist erledigt."
  exit 0
fi

call GET /internal/feedback
python3 -c '
import json, sys
rows = json.loads(sys.argv[1])
if sys.argv[2] == "neu":
    rows = [r for r in rows if not r["done"]]
if not rows:
    print("Kein offenes Feedback.")
for r in rows:
    mark = "✓" if r["done"] else "•"
    nr, when, page, who = r["id"], r["createdAt"][:16].replace("T", " "), r.get("page") or "-", r["from"]
    print(f"{mark} Nr. {nr}  {when} UTC  {page}  ({who})")
    for line in r["message"].splitlines():
        print("    " + line)
    print()
' "$response" "$mode"
