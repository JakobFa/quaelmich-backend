#!/usr/bin/env bash
# Eine Runde des Dauerlaufs: abwechselnd Zufallssuche und Plan-Optimierung auf main, Bericht ins Ablage-Repo.
# systemd startet danach die nächste Runde und holt vorher den neuesten main-Stand (siehe installieren.sh).
# Ein fehlgeschlagener Push ist kein Fehler: Der Commit bleibt liegen und geht mit der nächsten Runde raus.
# Solange der Deploy-Key fehlt, scheitert das Klonen der Ablage, und es wird noch nichts gerechnet.
#
# Einstellbar über die Umgebung: DAUERLAUF_DIR, DAUERLAUF_ABLAGE, FUZZ_SEKUNDEN, OPTIMIERUNG_SEKUNDEN,
# OPTIMIERUNG_TRAIN, OPTIMIERUNG_HOLDOUT
set -euo pipefail

DIR="${DAUERLAUF_DIR:-$HOME/quaelmich-dauerlauf}"
ABLAGE_REMOTE="${DAUERLAUF_ABLAGE:-git@github.com:JakobFa/quaelmich-dauerlauf.git}"
CODE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ABLAGE="$DIR/ablage"
ZAEHLER="$DIR/runde.txt"

ablage_vorbereiten() {
  [[ -d "$ABLAGE/.git" ]] && return
  git clone -q "$ABLAGE_REMOTE" "$ABLAGE"
  git -C "$ABLAGE" config user.name "quaelmich-dauerlauf"
  git -C "$ABLAGE" config user.email "dauerlauf@users.noreply.github.com"
  git -C "$ABLAGE" symbolic-ref HEAD refs/heads/main
}

veroeffentlichen() {
  local nachricht="$1"
  printf 'Stand: %s, Runde %s, Commit %s von main\n' "$(date -u +%FT%TZ)" "$runde" "$commit" > "$ABLAGE/STAND.txt"
  git -C "$ABLAGE" add -A
  git -C "$ABLAGE" commit -q -m "$nachricht" || true
  git -C "$ABLAGE" push -q origin HEAD:main || echo "Push fehlgeschlagen, nächste Runde versucht es wieder"
}

fuzz_runde() {
  # Basis weit weg von den Nacht-Läufen auf GitHub (Epochentag), damit jede Runde neue Fahrer rechnet
  local basis=$((1000000 + runde))
  ./gradlew --no-daemon -q fuzzSimulation -PbudgetSeconds="${FUZZ_SEKUNDEN:-3600}" -PfuzzBase="$basis"
  cp build/fuzz-simulation.md "$ABLAGE/fuzz-latest.md"
  # Das Datum in der Zeile käme aus der Basis, darum hier die echte Zeit
  printf '%s\t%s\n' "$(date -u +%FT%TZ)" "$(cut -f2- build/fuzz-simulation.tsv)" >> "$ABLAGE/fuzz-history.tsv"
  # fuzz-latest.md wird jede Runde ersetzt, seltene harte Befunde bleiben hier erhalten
  grep '^| HART' build/fuzz-simulation.md | while IFS= read -r zeile; do
    printf '%s\t%s\t%s\n' "$(date -u +%F)" "$commit" "$zeile"
  done >> "$ABLAGE/harte-befunde.tsv" || true
  veroeffentlichen "Zufallssuche Runde $runde"
}

optimierung_runde() {
  # Macht beim besten Satz bisher weiter, beim ersten Mal bei den App-Werten
  local start=()
  [[ -f "$ABLAGE/optimization-best.txt" ]] && start=(-PstartFrom="$ABLAGE/optimization-best.txt")
  ./gradlew --no-daemon -q optimizePlan -PbudgetSeconds="${OPTIMIERUNG_SEKUNDEN:-10800}" \
    -PtrainRiders="${OPTIMIERUNG_TRAIN:-600}" -PholdoutRiders="${OPTIMIERUNG_HOLDOUT:-2000}" ${start[@]+"${start[@]}"}
  cp build/plan-optimization.md "$ABLAGE/optimization-latest.md"
  cp build/plan-optimization-best.txt "$ABLAGE/optimization-best.txt"
  printf '%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$commit" \
    "$(grep -m1 '^\*\*Ergebnis' build/plan-optimization.md | tr -d '*')" >> "$ABLAGE/optimization-history.tsv"
  veroeffentlichen "Plan-Optimierung Runde $runde"
}

cd "$CODE"
ablage_vorbereiten
runde=$(($(cat "$ZAEHLER" 2>/dev/null || echo 0) + 1))
commit="$(git rev-parse --short HEAD)"
# Schon vor der Rechnung hochzählen: Scheitert eine Art dauerhaft, läuft die andere trotzdem weiter
echo "$runde" > "$ZAEHLER"

if ((runde % 2 == 1)); then fuzz_runde; else optimierung_runde; fi
