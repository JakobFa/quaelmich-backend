# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

57600 zufällige Fahrer in 1508 s (Startwert-Basis 20766 = 2026-11-09). 2 (0.0 %) mit harten Befunden, 43217 (75.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 2 | 0.0 % | 4 | Startwert 20766072946: Woche ab 2027-02-01: Frische bis -61 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 24333 | 42.2 % | 67441 | Startwert 20766067881: Woche ab 2026-11-09: Frische bis -39 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 14605 | 25.4 % | 55231 | Startwert 20766081334: Woche ab 2026-11-16: geplant 165 TSS, 2.2-mal so viel wie zuletzt gefahren (76/Woche, beste Woche 147) |
| # × … – eintönig | 14604 | 25.4 % | 62252 | Startwert 20766062446: Woche ab 2026-11-09: 4 × "Grundlage 60 min" – eintönig |
| # Belastungswochen am Stück | 11749 | 20.4 % | 12762 | Startwert 20766063168: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 2491 | 4.3 % | 15531 | Startwert 20766063726: 2026-11-09: harte Einheit (VO2max 3 × 13 × 30/15) am Tag nach 210 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 2349 | 4.1 % | 2349 | Startwert 20766064762: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 1710 | 3.0 % | 1710 | Startwert 20766062400: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 1508 | 2.6 % | 1508 | Startwert 20766063342: Event-Tag: Frische -6 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1456 | 2.5 % | 1456 | Startwert 20766062513: Event-Tag: Frische 28 TSB passt nicht zum Ziel GOOD_TIME |
| Fitness steigt um # CTL | 1365 | 2.4 % | 2561 | Startwert 20766067356: Woche ab 2026-11-16: Fitness steigt um 9.3 CTL (Grenze 8) |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1327 | 2.3 % | 1327 | Startwert 20766063296: Woche ab 2026-12-07: nach 10 Tagen Krankheit 150 min geplant, mehr als davor (100) |
| Erholungswoche direkt vor dem Tapering | 343 | 0.6 % | 343 | Startwert 20766073063: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 291 | 0.5 % | 291 | Startwert 20766064072: Event-Tag: Frische -11 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 62 | 0.1 % | 62 | Startwert 20766091169: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20766072946: PERFORMANCE, ULTRA COMPETE 1037 km/14518 hm in 18 Wochen, Zeit Mo–So [2.25, 2.25, 1.75, 1.75, 3.25, 4.75, 7.0] (23.0 h), Ausfälle 11 %, fährt 110 % der Dauer, pendelt 30 min [#20766072946]
- **Frische bis # TSB gefallen** – Startwert 20766067881: AMBITIOUS, GRAVEL COMPETE 191 km/2101 hm in 2 Wochen, Zeit Mo–So [1.25, 0.75, 1.0, 1.5, 0.0, 2.25, 4.0] (10.8 h), Ausfälle 19 %, fährt 127 % der Dauer, pendelt 80 min [#20766067881]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20766081334: BEGINNER, BIKEPACKING FINISH 1272 km/5088 hm in 3 Wochen, Zeit Mo–So [0.5, 1.75, 1.5, 0.75, 1.0, 0.75, 0.0] (6.3 h), Ausfälle 7 %, fährt 93 % der Dauer, krank Woche 3 (5 Tage) [#20766081334]
- **# × … – eintönig** – Startwert 20766062446: PERFORMANCE, GRAVEL COMPETE 130 km/650 hm in 2 Wochen, Zeit Mo–So [2.25, 2.75, 2.0, 3.0, 2.0, 4.25, 7.75] (24.0 h), Ausfälle 1 %, fährt 123 % der Dauer [#20766062446]
- **# Belastungswochen am Stück** – Startwert 20766063168: BEGINNER, ROAD_RACE FINISH 129 km/1806 hm in 6 Wochen, Zeit Mo–So [1.5, 1.25, 0.0, 1.0, 1.5, 0.0, 1.5] (6.8 h), Ausfälle 17 %, unter der Woche +21 %, fährt 88 % der Dauer, pendelt 80 min, Reisewoche 6 [#20766063168]
- **harte Einheit am Tag nach # min geplant** – Startwert 20766063726: PERFORMANCE, ROAD_RACE COMPETE 134 km/2278 hm in 2 Wochen, Zeit Mo–So [3.0, 3.0, 3.5, 3.0, 3.5, 4.75, 5.75] (26.5 h), Ausfälle 19 %, fährt 94 % der Dauer [#20766063726]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20766064762: AMBITIOUS, BIKEPACKING COMPETE 1078 km/28028 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.75, 1.75] (6.5 h), Ausfälle 24 %, fährt 113 % der Dauer, Rennen Woche 2, krank Woche 5 (5 Tage), Reisewoche 1 [#20766064762]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20766062400: PERFORMANCE, ROAD_RACE GOOD_TIME 182 km/546 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.75, 6.25] (10.0 h), Ausfälle 0 %, fährt 87 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 5 (12 Tage), Reisewoche 5 [#20766062400]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20766063342: BEGINNER, ULTRA COMPETE 1201 km/9608 hm in 2 Wochen, Zeit Mo–So [0.0, 1.0, 1.0, 0.0, 0.0, 0.75, 0.0] (2.8 h), Ausfälle 14 %, fährt 118 % der Dauer, pendelt 70 min [#20766063342]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20766062513: PERFORMANCE, CRITERIUM GOOD_TIME 63 km/1260 hm in 2 Wochen, Zeit Mo–So [2.75, 1.5, 0.0, 2.0, 2.5, 6.0, 6.25] (21.0 h), Ausfälle 24 %, fährt 105 % der Dauer, Schichtdienst [#20766062513]
- **Fitness steigt um # CTL** – Startwert 20766067356: AMBITIOUS, ULTRA GOOD_TIME 651 km/8463 hm in 4 Wochen, Zeit Mo–So [1.75, 2.0, 0.5, 1.5, 0.75, 5.0, 3.0] (14.5 h), Ausfälle 1 %, fährt 119 % der Dauer, pendelt 50 min, Reisewoche 1, Schichtdienst [#20766067356]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20766063296: BEGINNER, ULTRA GOOD_TIME 574 km/5166 hm in 6 Wochen, Zeit Mo–So [0.0, 0.75, 0.0, 0.0, 0.5, 1.0, 1.5] (3.8 h), Ausfälle 18 %, fährt 101 % der Dauer, krank Woche 4 (10 Tage), Reisewoche 1 [#20766063296]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20766073063: PERFORMANCE, GRAVEL COMPETE 95 km/570 hm in 4 Wochen, Zeit Mo–So [2.0, 2.25, 2.5, 2.5, 3.25, 5.5, 5.75] (23.8 h), Ausfälle 19 %, fährt 91 % der Dauer, krank Woche 1 (13 Tage) [#20766073063]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20766064072: BEGINNER, ULTRA FINISH 1328 km/17264 hm in 2 Wochen, Zeit Mo–So [1.5, 1.0, 1.5, 0.5, 1.25, 2.5, 2.0] (10.3 h), Ausfälle 24 %, fährt 107 % der Dauer, pendelt 80 min, Schichtdienst [#20766064072]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20766091169: BEGINNER, BIKEPACKING GOOD_TIME 1105 km/15470 hm in 11 Wochen, Zeit Mo–So [1.5, 1.0, 0.75, 1.0, 1.25, 1.75, 1.25] (8.5 h), Ausfälle 5 %, fährt 113 % der Dauer, Rennen Woche 3, Reisewoche 2 [#20766091169]
