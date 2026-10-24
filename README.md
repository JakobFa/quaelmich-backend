# quaelmich

**quaelmich** ist eine Trainingsplan-App fürs Radfahren. Radfahrer:innen können ihre Trainingseinheiten planen, absolvierte Fahrten festhalten und ihren Fortschritt über die Zeit verfolgen.

Dieses Repository enthält das **Backend** (REST-API mit Spring Boot).

## Geplante Funktionen

- Trainingseinheiten (z. B. Grundlage, Intervalle, Regeneration) anlegen, anzeigen, bearbeiten und löschen
- Einheiten als absolviert markieren und tatsächliche Werte (Dauer, Intensität) erfassen
- Wochenübersicht und Filter nach Trainingsart oder Zeitraum
- Statistiken zum Trainingsfortschritt (Trainingsstunden, Intensität auf einer Skala von 1–100)

## Tech-Stack

- **Backend:** Java 25, Spring Boot, Gradle
- **Frontend:** Vue.js (separates Repository)
- **Datenbank:** PostgreSQL
- **Deployment:** Render

## Lokal starten

```bash
./gradlew bootRun
```

Die API ist dann unter `http://localhost:8080` erreichbar.
Beispiel-Workouts: `http://localhost:8080/workouts`

## Projektinfo

- **Modul:** Web-Technologien, HTW Berlin
- **Team:** Jakob (Einzelarbeit)
- **Übungsgruppe:** Montag, 12 Uhr – Prof. Dr. Arif Wider



## Freigabe

Gearbeitet wird auf `develop`. Jede Nacht übernimmt der Ablauf „Nächtliche Freigabe“ den Stand nach `main` (nur wenn die Tests grün sind); Render liefert `main` aus. Dringendes per Hand im Tab „Actions“.
