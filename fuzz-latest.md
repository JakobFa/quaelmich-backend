# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1351 s (Startwert-Basis 20783 = 2026-11-26). 4 (0.0 %) mit harten Befunden, 75729 (75.7 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 4 | 0.0 % | 4 | Startwert 20783116844: Woche ab 2026-12-14: Frische bis -64 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42667 | 42.7 % | 119958 | Startwert 20783062823: Woche ab 2026-11-09: Frische bis -36 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27251 | 27.3 % | 104241 | Startwert 20783068822: Woche ab 2026-11-16: geplant 114 TSS, 2.0-mal so viel wie zuletzt gefahren (57/Woche, beste Woche 77) |
| # × … – eintönig | 24647 | 24.6 % | 104303 | Startwert 20783063773: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20607 | 20.6 % | 22284 | Startwert 20783062490: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4435 | 4.4 % | 27102 | Startwert 20783082777: 2026-11-09: harte Einheit (VO2max 4 × 4) am Tag nach 225 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4037 | 4.0 % | 4037 | Startwert 20783063648: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3044 | 3.0 % | 5752 | Startwert 20783076432: Woche ab 2026-11-16: Fitness steigt um 10.0 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3012 | 3.0 % | 3012 | Startwert 20783062882: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2776 | 2.8 % | 2776 | Startwert 20783062823: Event-Tag: Frische -7 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2677 | 2.7 % | 2677 | Startwert 20783063112: Event-Tag: Frische -6 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2361 | 2.4 % | 2361 | Startwert 20783144509: Woche ab 2026-11-30: nach 7 Tagen Krankheit 206 min geplant, mehr als davor (203) |
| Erholungswoche direkt vor dem Tapering | 691 | 0.7 % | 691 | Startwert 20783064882: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 481 | 0.5 % | 481 | Startwert 20783084569: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 120 | 0.1 % | 120 | Startwert 20783089771: Woche ab 2026-12-14: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20783116844: PERFORMANCE, BIKEPACKING GOOD_TIME 680 km/6800 hm in 35 Wochen, Zeit Mo–So [3.25, 1.75, 2.25, 3.5, 2.75, 5.5, 7.25] (26.3 h), Ausfälle 8 %, fährt 104 % der Dauer, pendelt 50 min [#20783116844]
- **Frische bis # TSB gefallen** – Startwert 20783062823: HOBBY, ROAD_RACE COMPETE 186 km/1488 hm in 2 Wochen, Zeit Mo–So [1.5, 2.0, 0.75, 1.5, 1.75, 3.5, 4.0] (15.0 h), Ausfälle 23 %, fährt 126 % der Dauer, pendelt 50 min [#20783062823]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20783068822: HOBBY, ULTRA GOOD_TIME 592 km/5920 hm in 2 Wochen, Zeit Mo–So [0.0, 1.25, 1.0, 0.0, 0.75, 0.0, 0.0] (3.0 h), Ausfälle 19 %, unter der Woche +52 %, fährt 87 % der Dauer [#20783068822]
- **# × … – eintönig** – Startwert 20783063773: BEGINNER, ROAD_RACE GOOD_TIME 183 km/732 hm in 2 Wochen, Zeit Mo–So [1.25, 1.25, 0.75, 1.5, 1.5, 3.25, 1.5] (11.0 h), Ausfälle 11 %, fährt 81 % der Dauer [#20783063773]
- **# Belastungswochen am Stück** – Startwert 20783062490: HOBBY, TIME_TRIAL COMPETE 50 km/800 hm in 6 Wochen, Zeit Mo–So [0.0, 1.0, 2.0, 1.75, 0.0, 2.25, 1.0] (8.0 h), Ausfälle 15 %, fährt 115 % der Dauer, Rennen Woche 6 [#20783062490]
- **harte Einheit am Tag nach # min geplant** – Startwert 20783082777: PERFORMANCE, ROAD_RACE GOOD_TIME 119 km/595 hm in 2 Wochen, Zeit Mo–So [2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 5.5] (9.5 h), Ausfälle 1 %, fährt 121 % der Dauer, pendelt 60 min [#20783082777]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20783063648: BEGINNER, ULTRA FINISH 991 km/17838 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 1.0, 0.75, 1.0, 0.0, 1.75] (4.5 h), Ausfälle 14 %, fährt 76 % der Dauer, Rennen Woche 2 [#20783063648]
- **Fitness steigt um # CTL** – Startwert 20783076432: PERFORMANCE, BIKEPACKING FINISH 1248 km/31200 hm in 4 Wochen, Zeit Mo–So [2.25, 1.5, 3.25, 2.25, 1.5, 3.75, 6.75] (21.3 h), Ausfälle 1 %, fährt 98 % der Dauer, pendelt 60 min, Reisewoche 2 [#20783076432]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20783062882: HOBBY, CRITERIUM GOOD_TIME 54 km/108 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.75, 3.25] (6.0 h), Ausfälle 2 %, fährt 113 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 2 (10 Tage) [#20783062882]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20783062823: HOBBY, ROAD_RACE COMPETE 186 km/1488 hm in 2 Wochen, Zeit Mo–So [1.5, 2.0, 0.75, 1.5, 1.75, 3.5, 4.0] (15.0 h), Ausfälle 23 %, fährt 126 % der Dauer, pendelt 50 min [#20783062823]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20783063112: BEGINNER, BIKEPACKING GOOD_TIME 233 km/2330 hm in 2 Wochen, Zeit Mo–So [0.0, 1.25, 2.25, 0.0, 2.0, 0.5, 1.25] (7.3 h), Ausfälle 16 %, unter der Woche +27 %, fährt 111 % der Dauer [#20783063112]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20783144509: PERFORMANCE, CRITERIUM COMPETE 38 km/836 hm in 4 Wochen, Zeit Mo–So [1.75, 3.0, 4.25, 0.0, 4.0, 0.0, 0.0] (13.0 h), Ausfälle 16 %, fährt 111 % der Dauer, krank Woche 4 (7 Tage), Reisewoche 1 [#20783144509]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20783064882: BEGINNER, CRITERIUM GOOD_TIME 68 km/272 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.25, 2.75] (5.0 h), Ausfälle 14 %, fährt 83 % der Dauer, krank Woche 1 (12 Tage) [#20783064882]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20783084569: BEGINNER, ROAD_RACE FINISH 113 km/904 hm in 2 Wochen, Zeit Mo–So [1.5, 0.0, 1.25, 1.75, 1.25, 0.0, 1.75] (7.5 h), Ausfälle 24 %, fährt 112 % der Dauer, pendelt 70 min [#20783084569]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20783089771: BEGINNER, ULTRA FINISH 641 km/1923 hm in 11 Wochen, Zeit Mo–So [0.75, 0.75, 1.5, 1.0, 1.5, 3.0, 1.75] (10.3 h), Ausfälle 16 %, fährt 92 % der Dauer, pendelt 90 min, Rennen Woche 7, krank Woche 2 (9 Tage) [#20783089771]
