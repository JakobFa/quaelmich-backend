#!/usr/bin/env bash
# Rundmail an alle Nutzer von quaelmich – nur für den Betreiber.
#
#   scripts/newsletter.sh vorschau mail.md               Empfänger zeigen, nichts verschicken
#   scripts/newsletter.sh test mail.md du@example.com    nur an diese eine Adresse schicken
#   scripts/newsletter.sh senden mail.md                 an alle schicken (zeigt vorher die Liste, fragt nach)
#
# Antworten gehen an NEWSLETTER_REPLY_TO, sonst an dieselbe Adresse wie das Feedback (ADMIN_NOTIFY_EMAIL auf Render).
# Wer keine Mails mehr will: Adresse in NEWSLETTER_EXCLUDE eintragen (mit Komma getrennt).
#
# Aufbau von mail.md: eine Zeile "**Betreff:** …", optional "**Vorschautext:** …", dann "---" und der Text
# (Absätze, **fett**, Listen mit "- " oder "1. ").
#
# Das Geheimnis (ADMIN_TOKEN von Render) wird aus dem macOS-Schlüsselbund gelesen, wie bei scripts/invite.sh.
set -euo pipefail

API="${QUAELMICH_API:-https://api.quaelmich.com}"

mode="${1:-}"
file="${2:-}"
case "$mode" in
  vorschau|senden) ;;
  test) [[ -n "${3:-}" ]] || { echo "An welche Adresse? scripts/newsletter.sh test mail.md du@example.com" >&2; exit 1; } ;;
  *) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
[[ -f "$file" ]] || { echo "Datei nicht gefunden: $file" >&2; exit 1; }

token="$(security find-generic-password -s quaelmich-admin-token -w 2>/dev/null || true)"
if [[ -z "$token" ]]; then
  read -rsp "Admin-Token: " token
  echo
fi

# Markdown → Betreff, Text- und HTML-Fassung, als JSON für den Endpunkt
build() { # $1 = send (true/false), $2 = expectedCount oder leer, $3 = nur diese Adresse oder leer
  python3 - "$file" "$1" "$2" "$3" "${NEWSLETTER_REPLY_TO:-}" "${NEWSLETTER_EXCLUDE:-}" <<'PY'
import html, json, re, sys
path, send, expected, only, reply_to, exclude = sys.argv[1:7]
raw = open(path, encoding="utf-8").read()
head, _, body = raw.partition("\n---\n")
subject = re.search(r"\*\*Betreff:\*\*\s*(.+)", head)
if not subject or not body.strip():
    sys.exit("In der Datei fehlt die Zeile '**Betreff:** …' oder der Text nach '---'.")
preheader = re.search(r"\*\*Vorschautext:\*\*\s*(.+)", head)

def inline(text):
    return re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", html.escape(text))

blocks = [b for b in re.split(r"\n\s*\n", body.strip()) if b.strip()]
parts, plain = [], []
for block in blocks:
    lines = [l.rstrip() for l in block.splitlines()]
    if all(re.match(r"- ", l) for l in lines):
        parts.append("<ul>" + "".join(f"<li>{inline(l[2:])}</li>" for l in lines) + "</ul>")
    elif all(re.match(r"\d+\. ", l) for l in lines):
        items = [re.sub(r"^\d+\. ", "", l) for l in lines]
        parts.append("<ol>" + "".join("<li>" + inline(i) + "</li>" for i in items) + "</ol>")
    else:
        parts.append("<p>" + "<br>".join(inline(l) for l in lines) + "</p>")
    plain.append("\n".join(re.sub(r"\*\*(.+?)\*\*", r"\1", l) for l in lines))

hidden = ""
if preheader:
    hidden = ('<div style="display:none;max-height:0;overflow:hidden">'
              + html.escape(preheader.group(1).strip()) + "</div>")
page = ('<!DOCTYPE html><html lang="de"><body style="margin:0;padding:24px;background:#ffffff">' + hidden
        + '<div style="max-width:600px;margin:0 auto;font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif;'
        + 'font-size:16px;line-height:1.5;color:#1a1a1a">' + "".join(parts) + "</div></body></html>")
request = {"subject": subject.group(1).strip(), "text": "\n\n".join(plain) + "\n", "html": page,
           "replyTo": reply_to or None, "send": send == "true",
           "expectedCount": int(expected) if expected else None,
           "only": [only] if only else None,
           "exclude": [e.strip() for e in exclude.split(",") if e.strip()] or None}
print(json.dumps(request))
PY
}

# Render schläft auf dem Free-Tier – der erste Aufruf kann bis zu einer Minute dauern; Senden dauert ~1 s je Mail.
call() {
  local out
  out="$(curl -sS --max-time 600 -X POST -H "X-Admin-Token: $token" -H 'Content-Type: application/json' \
    --data-binary @- -w '\n%{http_code}' "$API/internal/newsletter")"
  status="${out##*$'\n'}"
  response="${out%$'\n'*}"
  if [[ "$status" == 404 ]]; then
    echo "Nicht gefunden – Token falsch oder der Endpunkt ist noch nicht ausgeliefert." >&2
    exit 1
  elif [[ "$status" == 409 ]]; then
    echo "Die Empfängerliste hat sich seit der Vorschau geändert – nichts verschickt. Bitte nochmal starten." >&2
    exit 1
  elif [[ "$status" -ge 400 ]]; then
    echo "Fehler $status: $response" >&2
    exit 1
  fi
}

show() {
  python3 - "$response" <<'PY'
import json, sys
r = json.loads(sys.argv[1])
for email in r["recipients"]:
    print("  " + email)
print(str(r["count"]) + " Empfänger")
print("Antworten an: " + (r.get("replyTo") or "niemand (noreply) – ADMIN_NOTIFY_EMAIL ist auf Render nicht gesetzt"))
if r["sent"]:
    failed = r["failed"]
    print("Verschickt: " + str(r["count"] - len(failed)) + (", fehlgeschlagen: " + str(len(failed)) if failed else ""))
    for email in failed:
        print("  fehlgeschlagen: " + email)
PY
}

count() { python3 -c 'import json,sys; print(json.loads(sys.argv[1])["count"])' "$response"; }

only="${3:-}"
build false "" "$only" | call
echo "Betreff: $(python3 -c 'import json,sys; print(json.load(sys.stdin)["subject"])' < <(build false "" "$only"))"
show
[[ "$mode" == vorschau ]] && exit 0

n="$(count)"
[[ "$n" -gt 0 ]] || { echo "Keine Empfänger." >&2; exit 1; }
read -rp "Jetzt an diese $n Adresse(n) schicken? Zum Bestätigen 'ja' tippen: " answer
[[ "$answer" == ja ]] || { echo "Abgebrochen, nichts verschickt."; exit 0; }
build true "$n" "$only" | call
show
