#!/usr/bin/env bash
# Richtet den Dauerlauf auf einem Linux-Rechner mit systemd ein. Nötig sind git, curl, tar und ssh-keygen, aber kein
# root. Das Skript holt Java 25 und den Code (nur lesend), legt einen Schlüssel für das Ablage-Repo an und startet den
# Dienst. Erneut ausführen ist unbedenklich.
#
# Danach: den ausgegebenen öffentlichen Schlüssel als Deploy-Key mit Schreibrecht im Ablage-Repo eintragen.
set -euo pipefail

[[ "$(uname -s)" == Linux ]] || { echo "Nur für Linux gedacht" >&2; exit 1; }
for werkzeug in git curl tar ssh-keygen systemctl; do
  command -v "$werkzeug" >/dev/null || { echo "$werkzeug fehlt" >&2; exit 1; }
done

DIR="${DAUERLAUF_DIR:-$HOME/quaelmich-dauerlauf}"
SCHLUESSEL="$DIR/deploy-key"
mkdir -p "$DIR"

case "$(uname -m)" in
  x86_64) ARCH=x64 ;;
  aarch64) ARCH=aarch64 ;;
  *) echo "Prozessor $(uname -m) wird nicht unterstützt" >&2; exit 1 ;;
esac

if [[ ! -x "$DIR/jdk/bin/java" ]]; then
  echo "Hole Java 25 …"
  mkdir -p "$DIR/jdk"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/$ARCH/jdk/hotspot/normal/eclipse" \
    | tar -xz -C "$DIR/jdk" --strip-components=1
fi

[[ -d "$DIR/code/.git" ]] || git clone -q --branch main https://github.com/JakobFa/quaelmich-backend.git "$DIR/code"
[[ -f "$SCHLUESSEL" ]] || ssh-keygen -q -t ed25519 -N '' -C "quaelmich-dauerlauf@$(hostname)" -f "$SCHLUESSEL"

mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/quaelmich-dauerlauf.service" <<UNIT
[Unit]
Description=quaelmich: Zufallssuche und Plan-Optimierung im Dauerlauf
After=network-online.target
StartLimitIntervalSec=0

[Service]
Type=simple
WorkingDirectory=$DIR/code
Environment=JAVA_HOME=$DIR/jdk
Environment=DAUERLAUF_DIR=$DIR
Environment="GIT_SSH_COMMAND=ssh -i $SCHLUESSEL -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
# Vor jeder Runde neuester main-Stand; ohne Netz rechnet sie mit dem alten weiter
ExecStartPre=-/usr/bin/env git -C $DIR/code fetch -q origin main
ExecStartPre=-/usr/bin/env git -C $DIR/code reset -q --hard origin/main
ExecStart=/usr/bin/env bash $DIR/code/scripts/dauerlauf/runde.sh
Restart=always
RestartSec=300
Nice=15

[Install]
WantedBy=default.target
UNIT

systemctl --user daemon-reload
systemctl --user enable --now quaelmich-dauerlauf.service
loginctl enable-linger "$USER" \
  || echo "Achtung: Ohne 'sudo loginctl enable-linger $USER' stoppt der Dienst beim Abmelden."

echo
echo "Der Dienst ist eingerichtet und rechnet, sobald dieser Schlüssel im Ablage-Repo eingetragen ist:"
cat "$SCHLUESSEL.pub"
echo
echo "Logs: journalctl --user -u quaelmich-dauerlauf -f"
