# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1340 s (Startwert-Basis 20775 = 2026-11-18). 5 (0.0 %) mit harten Befunden, 75786 (75.8 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 5 | 0.0 % | 5 | Startwert 20775093128: Woche ab 2026-11-23: Frische bis -60 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42605 | 42.6 % | 120019 | Startwert 20775062461: Woche ab 2026-11-09: Frische bis -38 TSB gefallen (Warnung ab -35) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27071 | 27.1 % | 102982 | Startwert 20775063599: Woche ab 2026-11-16: geplant 175 TSS, 2.5-mal so viel wie zuletzt gefahren (69/Woche, beste Woche 157) |
| # × … – eintönig | 24810 | 24.8 % | 105330 | Startwert 20775062357: Woche ab 2026-11-09: 4 × "Grundlage 60 min" – eintönig |
| # Belastungswochen am Stück | 20667 | 20.7 % | 22318 | Startwert 20775062830: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4331 | 4.3 % | 26474 | Startwert 20775064847: 2026-11-16: harte Einheit (Schwelle 2 × 15) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3966 | 4.0 % | 3966 | Startwert 20775065594: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3027 | 3.0 % | 3027 | Startwert 20775063045: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2888 | 2.9 % | 5577 | Startwert 20775068755: Woche ab 2026-11-16: Fitness steigt um 8.5 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2667 | 2.7 % | 2667 | Startwert 20775062461: Event-Tag: Frische 26 TSB passt nicht zum Ziel GOOD_TIME |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2642 | 2.6 % | 2642 | Startwert 20775062881: Event-Tag: Frische 30 TSB passt nicht zum Ziel COMPETE |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2196 | 2.2 % | 2196 | Startwert 20775119841: Woche ab 2026-11-30: nach 7 Tagen Krankheit 105 min geplant, mehr als davor (100) |
| Erholungswoche direkt vor dem Tapering | 665 | 0.7 % | 665 | Startwert 20775063718: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 545 | 0.5 % | 545 | Startwert 20775063571: Event-Tag: Frische -15 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 118 | 0.1 % | 118 | Startwert 20775135121: Woche ab 2026-12-07: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20775093128: AMBITIOUS, BIKEPACKING COMPETE 1053 km/20007 hm in 4 Wochen, Zeit Mo–So [1.25, 0.0, 0.0, 0.0, 0.0, 4.25, 4.25] (9.8 h), Ausfälle 19 %, fährt 110 % der Dauer, pendelt 40 min, Rennen Woche 2 [#20775093128]
- **Frische bis # TSB gefallen** – Startwert 20775062461: PERFORMANCE, GRAN_FONDO GOOD_TIME 171 km/4104 hm in 2 Wochen, Zeit Mo–So [0.75, 2.5, 2.0, 1.5, 2.5, 4.75, 6.75] (20.8 h), Ausfälle 14 %, fährt 103 % der Dauer, pendelt 20 min [#20775062461]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20775063599: BEGINNER, ULTRA COMPETE 1417 km/12753 hm in 3 Wochen, Zeit Mo–So [0.0, 0.0, 1.0, 1.75, 0.75, 1.25, 0.5] (5.3 h), Ausfälle 21 %, fährt 116 % der Dauer [#20775063599]
- **# × … – eintönig** – Startwert 20775062357: PERFORMANCE, ROAD_RACE FINISH 140 km/3500 hm in 2 Wochen, Zeit Mo–So [2.25, 1.0, 0.0, 2.5, 2.25, 4.0, 6.25] (18.3 h), Ausfälle 10 %, fährt 116 % der Dauer, pendelt 40 min [#20775062357]
- **# Belastungswochen am Stück** – Startwert 20775062830: PERFORMANCE, TIME_TRIAL COMPETE 77 km/231 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.25, 4.5] (8.8 h), Ausfälle 19 %, fährt 96 % der Dauer [#20775062830]
- **harte Einheit am Tag nach # min geplant** – Startwert 20775064847: PERFORMANCE, BIKEPACKING GOOD_TIME 728 km/2184 hm in 2 Wochen, Zeit Mo–So [3.25, 3.0, 2.0, 3.25, 1.75, 6.5, 7.75] (27.5 h), Ausfälle 12 %, fährt 84 % der Dauer, pendelt 30 min [#20775064847]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20775065594: PERFORMANCE, TIME_TRIAL FINISH 81 km/891 hm in 5 Wochen, Zeit Mo–So [3.25, 0.0, 1.25, 0.0, 0.0, 3.0, 2.0] (9.5 h), Ausfälle 14 %, unter der Woche +35 %, fährt 106 % der Dauer, pendelt 30 min, Rennen Woche 2 [#20775065594]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20775063045: AMBITIOUS, TIME_TRIAL GOOD_TIME 33 km/528 hm in 5 Wochen, Zeit Mo–So [1.5, 1.5, 1.75, 1.25, 1.0, 3.25, 4.0] (14.3 h), Ausfälle 5 %, fährt 97 % der Dauer, Urlaub Woche 2 (1 Wo.) [#20775063045]
- **Fitness steigt um # CTL** – Startwert 20775068755: PERFORMANCE, BIKEPACKING GOOD_TIME 998 km/1996 hm in 4 Wochen, Zeit Mo–So [1.0, 0.0, 0.0, 0.0, 1.0, 6.75, 6.75] (15.5 h), Ausfälle 16 %, unter der Woche +55 %, fährt 113 % der Dauer, pendelt 80 min, Rennen Woche 4, Reisewoche 2 [#20775068755]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20775062461: PERFORMANCE, GRAN_FONDO GOOD_TIME 171 km/4104 hm in 2 Wochen, Zeit Mo–So [0.75, 2.5, 2.0, 1.5, 2.5, 4.75, 6.75] (20.8 h), Ausfälle 14 %, fährt 103 % der Dauer, pendelt 20 min [#20775062461]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20775062881: PERFORMANCE, CRITERIUM COMPETE 67 km/1541 hm in 2 Wochen, Zeit Mo–So [2.25, 2.75, 0.0, 0.0, 1.75, 4.0, 3.75] (14.5 h), Ausfälle 8 %, fährt 100 % der Dauer, pendelt 70 min, Schichtdienst [#20775062881]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20775119841: HOBBY, ULTRA COMPETE 1316 km/13160 hm in 5 Wochen, Zeit Mo–So [0.0, 0.5, 0.0, 0.0, 0.0, 1.5, 0.75] (2.8 h), Ausfälle 18 %, fährt 88 % der Dauer, pendelt 30 min, Urlaub Woche 2 (1 Wo.), krank Woche 4 (7 Tage), Reisewoche 3 [#20775119841]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20775063718: BEGINNER, CRITERIUM GOOD_TIME 67 km/1407 hm in 4 Wochen, Zeit Mo–So [2.0, 0.0, 1.75, 0.75, 0.5, 0.5, 0.75] (6.3 h), Ausfälle 16 %, fährt 80 % der Dauer, pendelt 60 min, Rennen Woche 2, krank Woche 1 (11 Tage), Reisewoche 2 [#20775063718]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20775063571: BEGINNER, BIKEPACKING FINISH 1230 km/12300 hm in 2 Wochen, Zeit Mo–So [1.5, 1.25, 1.0, 0.75, 1.0, 1.75, 2.75] (10.0 h), Ausfälle 14 %, fährt 83 % der Dauer, pendelt 90 min [#20775063571]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20775135121: AMBITIOUS, ULTRA FINISH 312 km/1560 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.25, 4.5] (8.8 h), Ausfälle 22 %, fährt 83 % der Dauer, pendelt 90 min, krank Woche 1 (9 Tage), Reisewoche 10 [#20775135121]
