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
# (Absätze, **fett**, eine Zeile nur in **…** = Zwischenüberschrift, Listen mit "- " oder "1. ",
# Knopf mit "[[Beschriftung|https://…]]"). Das Layout entspricht der Webseite.
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

# Layout wie die Webseite (src/assets/main.css im Frontend): dunkel, Lila, Barlow Condensed, Space Mono
INK, PANEL, RAISED, LILAC, TEXT, MUTED = "#141615", "#1f2422", "#2a312e", "#dcbaf7", "#f2edf6", "#98a29e"
BODY_FONT = "'Barlow Condensed','Arial Narrow',Arial,Helvetica,sans-serif"
HEAD_FONT = "'Saira Stencil One','Barlow Condensed','Arial Narrow',Arial,sans-serif"
MONO_FONT = "'Space Mono',Menlo,Consolas,monospace"
SITE = "https://quaelmich.com"
LOGO = "https://api.quaelmich.com/mail/logo.png"
P = f"margin:0 0 16px;font-family:{BODY_FONT};font-size:18px;font-weight:500;line-height:1.45;color:{TEXT}"

def inline(text):
    out = re.sub(r"\*\*(.+?)\*\*", r'<strong style="color:#ffffff;font-weight:700">\1</strong>', html.escape(text))
    return re.sub(r"(?<![\w/@.])(quaelmich\.com)\b", rf'<a href="{SITE}" style="color:{LILAC}">\1</a>', out)

blocks = [b for b in re.split(r"\n\s*\n", body.strip()) if b.strip()]
parts, plain = [], []
for block in blocks:
    lines = [l.rstrip() for l in block.splitlines()]
    button = re.fullmatch(r"\[\[(.+?)\|(https?://\S+)\]\]", lines[0].strip()) if len(lines) == 1 else None
    heading = re.fullmatch(r"\*\*([^*]+)\*\*", lines[0].strip()) if len(lines) == 1 else None
    if button:
        label, url = button.group(1), button.group(2)
        parts.append(f'<table role="presentation" cellpadding="0" cellspacing="0" style="margin:8px 0 24px"><tr>'
                     f'<td style="background:{LILAC}"><a href="{html.escape(url)}" style="display:inline-block;'
                     f'padding:12px 22px;font-family:{MONO_FONT};font-size:14px;font-weight:700;letter-spacing:0.08em;'
                     f'text-transform:uppercase;color:{INK};text-decoration:none">{html.escape(label)}</a></td></tr></table>')
        plain.append(f"{label}: {url}")
        continue
    if heading:
        parts.append(f'<h2 style="margin:28px 0 10px;font-family:{HEAD_FONT};font-size:24px;font-weight:400;'
                     f'line-height:1.2;letter-spacing:0.02em;text-transform:uppercase;color:{LILAC}">'
                     f"{html.escape(heading.group(1))}</h2>")
        plain.append(heading.group(1).upper())
        continue
    if all(re.match(r"- ", l) for l in lines):
        items = [l[2:] for l in lines]
        tag = "ul"
    elif all(re.match(r"\d+\. ", l) for l in lines):
        items = [re.sub(r"^\d+\. ", "", l) for l in lines]
        tag = "ol"
    else:
        items = None
    if items is not None:
        li = f"margin:0 0 8px;font-family:{BODY_FONT};font-size:18px;font-weight:500;line-height:1.45;color:{TEXT}"
        parts.append(f'<{tag} style="margin:0 0 16px;padding-left:24px;color:{LILAC}">'
                     + "".join(f'<li style="{li}">{inline(i)}</li>' for i in items) + f"</{tag}>")
    else:
        parts.append(f'<p style="{P}">' + "<br>".join(inline(l) for l in lines) + "</p>")
    plain.append("\n".join(re.sub(r"\*\*(.+?)\*\*", r"\1", l) for l in lines))

hidden = ""
if preheader:
    hidden = (f'<div style="display:none;max-height:0;overflow:hidden;mso-hide:all">'
              + html.escape(preheader.group(1).strip()) + "</div>")
footer = (f'<p style="margin:0;font-family:{MONO_FONT};font-size:11px;line-height:1.6;letter-spacing:0.06em;'
          f'text-transform:uppercase;color:{MUTED}">Du bekommst diese Mail, weil du bei '
          f'<a href="{SITE}" style="color:{MUTED}">quaelmich</a> angemeldet bist.</p>')
page = ('<!DOCTYPE html><html lang="de"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '<meta name="color-scheme" content="dark"><meta name="supported-color-schemes" content="dark">'
        '<link href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@500;700'
        '&family=Saira+Stencil+One&family=Space+Mono:wght@400;700&display=swap" rel="stylesheet">'
        f'<title>{html.escape(subject.group(1).strip())}</title></head>'
        f'<body style="margin:0;padding:0;background:{INK}">' + hidden
        + f'<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{INK}"><tr>'
        '<td align="center" style="padding:28px 12px">'
        '<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px">'
        f'<tr><td align="center" style="padding:0 0 22px"><a href="{SITE}">'
        f'<img src="{LOGO}" width="300" alt="quaelmich" style="display:block;width:300px;max-width:80%;height:auto;'
        'border:0"></a></td></tr>'
        f'<tr><td style="height:4px;background:{LILAC};font-size:0;line-height:0">&nbsp;</td></tr>'
        f'<tr><td style="background:{PANEL};padding:30px 28px 18px">'
        f'<p style="margin:0 0 18px;font-family:{MONO_FONT};font-size:12px;font-weight:700;letter-spacing:0.12em;'
        f'text-transform:uppercase;color:{LILAC}">Neu bei quaelmich</p>'
        + "".join(parts) + "</td></tr>"
        f'<tr><td style="background:{RAISED};padding:16px 28px">{footer}</td></tr>'
        "</table></td></tr></table></body></html>")
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
