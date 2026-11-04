# Strava: Pflichten und wie quaelmich sie erfüllt

Stand 04.11.2026, Grundlage: Strava API Agreement und API Policy (beide gültig seit 1. Juni 2026). Kein Rechtsrat,
nur die Lesart des Textes. Bei Zweifeln gilt der Originaltext: <https://www.strava.com/legal/api> und
<https://www.strava.com/legal/api_policy>.

## Was im Code umgesetzt ist

| Pflicht | Umsetzung |
|---|---|
| Strava-Werte höchstens 7 Tage speichern (Policy §6.2) | `StravaRetentionService`: täglich 04:20 MEZ; `StravaConnection.lastSyncAt`; Werte höchstens 6 Tage alt |
| Löschung bei Strava nachziehen, in 48 h (§6.3) | Import bei jedem Öffnen und im nächtlichen Sync (`IntervalsAutoSyncService.syncAll`) |
| Löschen bei Trennen, Widerruf, Konto-Löschung (§2.5, §7.4) | `StravaService.disconnect`, `removeConnectionAndData`, `AccountService.delete` |
| Schriftliche Bestätigung der Löschung (§2.1 (v), §2.5) | `DeletionNotice`: Mail an den Nutzer |
| Anzeige nur für den Nutzer selbst (§2.3) | Keine Weitergabe, keine Auswertung über Nutzer hinweg |
| Keine Strava-Daten an KI-Anbieter (§5.3, §5.10) | MCP liefert nur Plan, Profil und Bibliothek (`McpTools`) |
| Zugriff des Nutzers auf seine Daten (§2.2) | Export "Meine Daten herunterladen" |

## Abläufe, die kein Code sind

### Datenpanne (Policy §8.3)

Jede Sicherheitspanne mit Bezug zu Strava-Daten oder zur Anwendung **innerhalb von 24 Stunden** schriftlich an
**legal@strava.com** melden: was ist passiert, welche Daten, wie viele Nutzer, was wurde getan. Zusätzlich die
Datenschutzbehörde (72 Stunden, DSGVO Art. 33) und die Betroffenen informieren, wenn ein hohes Risiko besteht.
Verantwortlich: Betreiber.

### Gestohlener API-Token (Agreement §1.2)

Verdacht auf Missbrauch des Client-Secrets: innerhalb von 24 Stunden an developers@strava.com melden, das Secret bei
Strava erneuern und bei Render eintragen.

### Löschanfragen (Policy §7.4)

Strava-Daten eines Nutzers müssen auf Anfrage, nach Widerruf der Erlaubnis und nach Löschung seines Strava-Kontos
**innerhalb von 30 Tagen** aus allen Systemen verschwinden. Im Code geschieht das beim Trennen sofort. Backups der
Datenbank (Render) halten gelöschte Daten noch bis zum Ablauf ihrer Aufbewahrung. Laut Render-Dokumentation
(Stand 04.11.2026) sind es 7 Tage für logische Backups und 3 bis 7 Tage Wiederherstellung, auf dem Free-Tarif gibt es
keine Backups. Das liegt unter den 30 Tagen. Nach außen nennen wir vorsichtshalber 30 Tage.

### Auftragsverarbeiter (Policy §7.7)

Strava kann eine Liste verlangen. Aktuell: Render Services, Inc. (Server, Datenbank, USA), Cloudflare, Inc. (DNS),
Resend (Mailversand, EU). Strava-Daten selbst liegen nur bei Render.

### Nutzerlimit (Policy §3.3)

Standardstufe: höchstens 10 registrierte Strava-Nutzer, danach höchstens 9.999. Vor dem Erhöhen der Grenze
Antrag bei Strava und Prüfung der dort genannten Bedingungen (u. a. Strava-Abo).

### Presse (Policy §4.6)

Keine Pressemitteilung oder Ankündigung, die Strava nennt, ohne vorherige schriftliche Zustimmung
(developers@strava.com).

### Offen

Siehe Mail an Strava (Fragen zu abgeleiteten Werten, Plan, MCP-Konnektor, Markenregeln, Garmin-Nennung).
