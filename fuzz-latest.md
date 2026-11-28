# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1268 s (Startwert-Basis 20785 = 2026-11-28). 6 (0.0 %) mit harten Befunden, 75737 (75.7 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 6 | 0.0 % | 6 | Startwert 20785147901: Woche ab 2026-12-28: Frische bis -61 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42701 | 42.7 % | 120201 | Startwert 20785062358: Woche ab 2026-11-02: Frische bis -41 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 26867 | 26.9 % | 104229 | Startwert 20785118423: Woche ab 2026-11-16: geplant 86 TSS, 1.6-mal so viel wie zuletzt gefahren (54/Woche, beste Woche 77) |
| # × … – eintönig | 25010 | 25.0 % | 106086 | Startwert 20785062358: Woche ab 2026-11-09: 5 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20654 | 20.7 % | 22456 | Startwert 20785062396: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4410 | 4.4 % | 27649 | Startwert 20785062916: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 315 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4012 | 4.0 % | 4012 | Startwert 20785062851: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3052 | 3.1 % | 3052 | Startwert 20785066743: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2938 | 2.9 % | 5510 | Startwert 20785063988: Woche ab 2026-11-16: Fitness steigt um 8.3 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2665 | 2.7 % | 2665 | Startwert 20785066242: Event-Tag: Frische -8 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2600 | 2.6 % | 2600 | Startwert 20785062358: Event-Tag: Frische 30 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2272 | 2.3 % | 2272 | Startwert 20785154937: Woche ab 2026-11-30: nach 7 Tagen Krankheit 191 min geplant, mehr als davor (157) |
| Erholungswoche direkt vor dem Tapering | 637 | 0.6 % | 637 | Startwert 20785064825: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 508 | 0.5 % | 508 | Startwert 20785068763: Event-Tag: Frische -15 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 127 | 0.1 % | 127 | Startwert 20785162299: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20785147901: PERFORMANCE, GRAN_FONDO COMPETE 216 km/5616 hm in 10 Wochen, Zeit Mo–So [2.5, 2.5, 1.75, 2.0, 2.25, 6.5, 4.5] (22.0 h), Ausfälle 6 %, fährt 106 % der Dauer [#20785147901]
- **Frische bis # TSB gefallen** – Startwert 20785062358: AMBITIOUS, CRITERIUM GOOD_TIME 30 km/240 hm in 2 Wochen, Zeit Mo–So [2.5, 1.25, 2.0, 2.25, 2.25, 4.25, 5.5] (20.0 h), Ausfälle 15 %, fährt 122 % der Dauer, Schichtdienst [#20785062358]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20785118423: HOBBY, ULTRA GOOD_TIME 427 km/854 hm in 2 Wochen, Zeit Mo–So [0.5, 0.0, 1.5, 0.0, 0.0, 0.0, 0.0] (2.0 h), Ausfälle 13 %, fährt 107 % der Dauer [#20785118423]
- **# × … – eintönig** – Startwert 20785062358: AMBITIOUS, CRITERIUM GOOD_TIME 30 km/240 hm in 2 Wochen, Zeit Mo–So [2.5, 1.25, 2.0, 2.25, 2.25, 4.25, 5.5] (20.0 h), Ausfälle 15 %, fährt 122 % der Dauer, Schichtdienst [#20785062358]
- **# Belastungswochen am Stück** – Startwert 20785062396: AMBITIOUS, GRAN_FONDO GOOD_TIME 160 km/4160 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 1.0, 0.0, 0.5, 3.0, 2.0] (6.5 h), Ausfälle 3 %, fährt 85 % der Dauer, Rennen Woche 5, Schichtdienst [#20785062396]
- **harte Einheit am Tag nach # min geplant** – Startwert 20785062916: AMBITIOUS, BIKEPACKING GOOD_TIME 242 km/6050 hm in 2 Wochen, Zeit Mo–So [2.25, 2.25, 1.25, 1.0, 1.5, 4.0, 4.5] (16.8 h), Ausfälle 11 %, unter der Woche +35 %, fährt 94 % der Dauer, pendelt 80 min, Schichtdienst [#20785062916]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20785062851: AMBITIOUS, ROAD_RACE COMPETE 203 km/406 hm in 5 Wochen, Zeit Mo–So [1.75, 1.0, 0.0, 1.25, 1.0, 0.0, 1.5] (6.5 h), Ausfälle 3 %, unter der Woche +34 %, fährt 104 % der Dauer, Urlaub Woche 3 (2 Wo.), Rennen Woche 2 [#20785062851]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20785066743: PERFORMANCE, ULTRA GOOD_TIME 942 km/23550 hm in 5 Wochen, Zeit Mo–So [2.0, 1.5, 1.5, 3.5, 2.75, 5.0, 3.25] (19.5 h), Ausfälle 2 %, fährt 76 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 4 (18 Tage) [#20785066743]
- **Fitness steigt um # CTL** – Startwert 20785063988: PERFORMANCE, ULTRA FINISH 337 km/6066 hm in 4 Wochen, Zeit Mo–So [3.0, 1.5, 2.5, 2.75, 3.25, 6.0, 3.25] (22.3 h), Ausfälle 1 %, fährt 119 % der Dauer, Rennen Woche 4 [#20785063988]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20785066242: BEGINNER, ULTRA COMPETE 1340 km/14740 hm in 2 Wochen, Zeit Mo–So [0.75, 0.75, 1.25, 0.5, 0.0, 2.25, 2.5] (8.0 h), Ausfälle 8 %, fährt 116 % der Dauer, pendelt 40 min [#20785066242]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20785062358: AMBITIOUS, CRITERIUM GOOD_TIME 30 km/240 hm in 2 Wochen, Zeit Mo–So [2.5, 1.25, 2.0, 2.25, 2.25, 4.25, 5.5] (20.0 h), Ausfälle 15 %, fährt 122 % der Dauer, Schichtdienst [#20785062358]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20785154937: BEGINNER, TIME_TRIAL COMPETE 43 km/516 hm in 5 Wochen, Zeit Mo–So [0.5, 1.75, 0.0, 0.0, 0.75, 0.0, 1.5] (4.5 h), Ausfälle 22 %, fährt 120 % der Dauer, krank Woche 4 (7 Tage), Schichtdienst [#20785154937]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20785064825: PERFORMANCE, CRITERIUM FINISH 33 km/858 hm in 4 Wochen, Zeit Mo–So [3.25, 2.25, 3.0, 3.5, 2.75, 6.25, 5.5] (26.5 h), Ausfälle 6 %, unter der Woche +32 %, fährt 120 % der Dauer, krank Woche 1 (12 Tage) [#20785064825]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20785068763: BEGINNER, ULTRA FINISH 1444 km/12996 hm in 2 Wochen, Zeit Mo–So [0.5, 0.0, 1.25, 1.0, 1.5, 1.0, 0.0] (5.3 h), Ausfälle 19 %, fährt 98 % der Dauer, pendelt 80 min, Schichtdienst [#20785068763]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20785162299: AMBITIOUS, GRAN_FONDO COMPETE 142 km/568 hm in 9 Wochen, Zeit Mo–So [1.25, 1.5, 1.25, 1.75, 2.5, 2.5, 3.5] (14.3 h), Ausfälle 23 %, fährt 104 % der Dauer, pendelt 50 min, Rennen Woche 9, krank Woche 5 (18 Tage) [#20785162299]
