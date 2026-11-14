# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

79600 zufällige Fahrer in 1509 s (Startwert-Basis 20771 = 2026-11-14). 2 (0.0 %) mit harten Befunden, 60391 (75.9 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 2 | 0.0 % | 2 | Startwert 20771076491: Woche ab 2027-01-25: Frische bis -65 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 34366 | 43.2 % | 96157 | Startwert 20771063238: Woche ab 2026-11-02: Frische bis -52 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 21732 | 27.3 % | 84634 | Startwert 20771084106: Woche ab 2026-11-16: geplant 116 TSS, 1.7-mal so viel wie zuletzt gefahren (69/Woche, beste Woche 98) |
| # × … – eintönig | 19537 | 24.5 % | 83014 | Startwert 20771062375: Woche ab 2026-11-09: 4 × "Grundlage 60 min" – eintönig |
| # Belastungswochen am Stück | 16413 | 20.6 % | 17802 | Startwert 20771063396: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3548 | 4.5 % | 21265 | Startwert 20771073310: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 210 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3193 | 4.0 % | 3193 | Startwert 20771063772: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2508 | 3.2 % | 2508 | Startwert 20771069710: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2323 | 2.9 % | 4347 | Startwert 20771062396: Woche ab 2026-11-16: Fitness steigt um 9.6 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2162 | 2.7 % | 2162 | Startwert 20771063576: Event-Tag: Frische 36 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2075 | 2.6 % | 2075 | Startwert 20771062685: Event-Tag: Frische -6 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1818 | 2.3 % | 1818 | Startwert 20771113030: Woche ab 2026-11-30: nach 7 Tagen Krankheit 293 min geplant, mehr als davor (282) |
| Erholungswoche direkt vor dem Tapering | 511 | 0.6 % | 511 | Startwert 20771065250: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 418 | 0.5 % | 418 | Startwert 20771065341: Event-Tag: Frische -16 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 102 | 0.1 % | 102 | Startwert 20771101341: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20771076491: PERFORMANCE, GRAVEL COMPETE 218 km/2180 hm in 15 Wochen, Zeit Mo–So [1.75, 3.0, 2.5, 1.75, 1.5, 8.0, 5.0] (23.5 h), Ausfälle 14 %, fährt 108 % der Dauer, Rennen Woche 6, krank Woche 7 (20 Tage), Reisewoche 4 [#20771076491]
- **Frische bis # TSB gefallen** – Startwert 20771063238: PERFORMANCE, TIME_TRIAL FINISH 15 km/285 hm in 2 Wochen, Zeit Mo–So [2.5, 2.25, 2.75, 3.5, 2.75, 6.5, 7.25] (27.5 h), Ausfälle 23 %, fährt 115 % der Dauer, pendelt 20 min [#20771063238]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20771084106: PERFORMANCE, ULTRA GOOD_TIME 576 km/1152 hm in 2 Wochen, Zeit Mo–So [0.0, 1.0, 2.75, 0.0, 0.0, 0.0, 0.0] (3.8 h), Ausfälle 1 %, fährt 125 % der Dauer, Schichtdienst [#20771084106]
- **# × … – eintönig** – Startwert 20771062375: PERFORMANCE, ROAD_RACE COMPETE 120 km/720 hm in 2 Wochen, Zeit Mo–So [2.25, 1.75, 2.0, 3.0, 3.0, 6.75, 3.25] (22.0 h), Ausfälle 18 %, fährt 115 % der Dauer [#20771062375]
- **# Belastungswochen am Stück** – Startwert 20771063396: PERFORMANCE, BIKEPACKING FINISH 1101 km/13212 hm in 6 Wochen, Zeit Mo–So [0.0, 1.25, 0.0, 3.0, 3.75, 0.0, 4.0] (12.0 h), Ausfälle 1 %, unter der Woche +26 %, fährt 120 % der Dauer [#20771063396]
- **harte Einheit am Tag nach # min geplant** – Startwert 20771073310: HOBBY, GRAN_FONDO GOOD_TIME 159 km/477 hm in 2 Wochen, Zeit Mo–So [1.75, 1.0, 1.75, 1.25, 1.5, 3.0, 4.5] (14.8 h), Ausfälle 9 %, unter der Woche +52 %, fährt 102 % der Dauer, Schichtdienst [#20771073310]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20771063772: PERFORMANCE, TIME_TRIAL FINISH 32 km/608 hm in 5 Wochen, Zeit Mo–So [2.25, 3.5, 2.25, 2.0, 1.75, 5.0, 3.0] (19.8 h), Ausfälle 8 %, fährt 99 % der Dauer, Rennen Woche 2 [#20771063772]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20771069710: HOBBY, GRAVEL COMPETE 204 km/4692 hm in 5 Wochen, Zeit Mo–So [1.5, 0.0, 1.0, 0.0, 1.25, 0.0, 0.5] (4.3 h), Ausfälle 15 %, fährt 97 % der Dauer, pendelt 80 min, Urlaub Woche 2 (1 Wo.) [#20771069710]
- **Fitness steigt um # CTL** – Startwert 20771062396: PERFORMANCE, BIKEPACKING GOOD_TIME 798 km/9576 hm in 4 Wochen, Zeit Mo–So [2.0, 1.75, 3.0, 1.75, 1.5, 6.5, 5.0] (21.5 h), Ausfälle 5 %, fährt 113 % der Dauer, pendelt 90 min, Rennen Woche 2 [#20771062396]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20771063576: PERFORMANCE, ULTRA COMPETE 310 km/5580 hm in 2 Wochen, Zeit Mo–So [2.5, 1.75, 2.25, 2.5, 2.5, 3.25, 4.75] (19.5 h), Ausfälle 20 %, unter der Woche +29 %, fährt 90 % der Dauer, Schichtdienst [#20771063576]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20771062685: BEGINNER, ULTRA GOOD_TIME 1156 km/16184 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 1.0, 1.25, 0.75, 1.5] (4.5 h), Ausfälle 7 %, fährt 86 % der Dauer, pendelt 40 min [#20771062685]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20771113030: AMBITIOUS, GRAN_FONDO GOOD_TIME 107 km/2782 hm in 5 Wochen, Zeit Mo–So [1.5, 0.75, 0.0, 0.75, 0.0, 3.0, 2.0] (8.0 h), Ausfälle 2 %, fährt 75 % der Dauer, Urlaub Woche 3 (2 Wo.), Rennen Woche 4, krank Woche 4 (7 Tage) [#20771113030]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20771065250: BEGINNER, ROAD_RACE FINISH 193 km/2702 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 1.5, 3.0] (4.5 h), Ausfälle 17 %, unter der Woche +56 %, fährt 109 % der Dauer, krank Woche 1 (12 Tage) [#20771065250]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20771065341: BEGINNER, GRAVEL FINISH 228 km/1368 hm in 2 Wochen, Zeit Mo–So [1.5, 1.25, 1.25, 1.25, 1.25, 2.25, 2.75] (11.5 h), Ausfälle 14 %, fährt 99 % der Dauer, pendelt 80 min [#20771065341]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20771101341: AMBITIOUS, GRAVEL GOOD_TIME 189 km/3213 hm in 11 Wochen, Zeit Mo–So [1.5, 0.75, 0.0, 0.0, 1.0, 2.5, 2.5] (8.3 h), Ausfälle 16 %, fährt 112 % der Dauer, Rennen Woche 3, Reisewoche 1 [#20771101341]
