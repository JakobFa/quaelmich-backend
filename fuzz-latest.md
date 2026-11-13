# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1489 s (Startwert-Basis 20770 = 2026-11-13). 6 (0.0 %) mit harten Befunden, 76106 (76.1 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 6 | 0.0 % | 8 | Startwert 20770149146: Woche ab 2026-12-14: Frische bis -65 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42990 | 43.0 % | 121075 | Startwert 20770066979: Woche ab 2026-11-02: Frische bis -42 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27165 | 27.2 % | 103655 | Startwert 20770065826: Woche ab 2026-11-16: geplant 169 TSS, 1.5-mal so viel wie zuletzt gefahren (112/Woche, beste Woche 153) |
| # × … – eintönig | 24941 | 24.9 % | 105141 | Startwert 20770064086: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20707 | 20.7 % | 22456 | Startwert 20770064276: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4416 | 4.4 % | 27419 | Startwert 20770069756: 2026-11-09: harte Einheit (VO2max 4 × 4) am Tag nach 225 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4027 | 4.0 % | 4027 | Startwert 20770066704: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3084 | 3.1 % | 5807 | Startwert 20770114207: Woche ab 2026-11-16: Fitness steigt um 8.4 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3013 | 3.0 % | 3013 | Startwert 20770063638: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2785 | 2.8 % | 2785 | Startwert 20770063195: Event-Tag: Frische -10 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2657 | 2.7 % | 2657 | Startwert 20770062714: Event-Tag: Frische -5 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2253 | 2.3 % | 2253 | Startwert 20770127175: Woche ab 2026-12-07: nach 7 Tagen Krankheit 94 min geplant, mehr als davor (93) |
| Erholungswoche direkt vor dem Tapering | 625 | 0.6 % | 625 | Startwert 20770064260: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 494 | 0.5 % | 494 | Startwert 20770063319: Event-Tag: Frische -10 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 132 | 0.1 % | 132 | Startwert 20770125111: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20770149146: PERFORMANCE, BIKEPACKING COMPETE 560 km/12320 hm in 9 Wochen, Zeit Mo–So [1.75, 2.25, 3.5, 3.25, 2.75, 7.0, 5.5] (26.0 h), Ausfälle 2 %, fährt 107 % der Dauer, Reisewoche 1 [#20770149146]
- **Frische bis # TSB gefallen** – Startwert 20770066979: HOBBY, ROAD_RACE FINISH 207 km/3312 hm in 2 Wochen, Zeit Mo–So [2.0, 1.0, 2.0, 1.5, 1.75, 4.0, 3.0] (15.3 h), Ausfälle 6 %, fährt 122 % der Dauer, pendelt 60 min [#20770066979]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20770065826: BEGINNER, GRAN_FONDO COMPETE 128 km/384 hm in 3 Wochen, Zeit Mo–So [1.0, 1.25, 0.25, 1.0, 0.5, 1.0, 0.0] (5.0 h), Ausfälle 11 %, fährt 102 % der Dauer, Schichtdienst [#20770065826]
- **# × … – eintönig** – Startwert 20770064086: PERFORMANCE, CRITERIUM GOOD_TIME 52 km/1196 hm in 2 Wochen, Zeit Mo–So [2.5, 2.0, 2.25, 0.0, 1.0, 6.0, 6.5] (20.3 h), Ausfälle 20 %, fährt 114 % der Dauer [#20770064086]
- **# Belastungswochen am Stück** – Startwert 20770064276: PERFORMANCE, GRAN_FONDO COMPETE 154 km/2002 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 5.5, 5.0] (10.5 h), Ausfälle 8 %, fährt 86 % der Dauer [#20770064276]
- **harte Einheit am Tag nach # min geplant** – Startwert 20770069756: AMBITIOUS, ROAD_RACE GOOD_TIME 107 km/856 hm in 2 Wochen, Zeit Mo–So [1.25, 2.25, 2.5, 1.0, 1.25, 3.75, 4.25] (16.3 h), Ausfälle 17 %, fährt 94 % der Dauer, Schichtdienst [#20770069756]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20770066704: HOBBY, ROAD_RACE FINISH 66 km/462 hm in 5 Wochen, Zeit Mo–So [0.75, 1.25, 0.75, 0.0, 0.0, 2.0, 3.25] (8.0 h), Ausfälle 6 %, fährt 116 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 2, krank Woche 1 (8 Tage) [#20770066704]
- **Fitness steigt um # CTL** – Startwert 20770114207: PERFORMANCE, BIKEPACKING COMPETE 906 km/15402 hm in 3 Wochen, Zeit Mo–So [3.0, 3.0, 2.25, 2.5, 3.25, 7.75, 3.0] (24.8 h), Ausfälle 7 %, fährt 120 % der Dauer, pendelt 90 min, krank Woche 1 (8 Tage) [#20770114207]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20770063638: PERFORMANCE, ULTRA GOOD_TIME 1232 km/30800 hm in 5 Wochen, Zeit Mo–So [1.75, 2.25, 1.75, 3.5, 3.5, 5.25, 7.0] (25.0 h), Ausfälle 16 %, fährt 82 % der Dauer, pendelt 50 min, Urlaub Woche 2 (1 Wo.), Rennen Woche 5 [#20770063638]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20770063195: BEGINNER, GRAVEL COMPETE 102 km/1020 hm in 2 Wochen, Zeit Mo–So [0.0, 1.0, 1.0, 0.0, 0.5, 2.75, 2.0] (7.3 h), Ausfälle 7 %, fährt 98 % der Dauer, pendelt 90 min [#20770063195]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20770062714: BEGINNER, GRAVEL GOOD_TIME 247 km/5187 hm in 2 Wochen, Zeit Mo–So [1.0, 1.0, 1.0, 1.5, 0.75, 3.25, 2.0] (10.5 h), Ausfälle 10 %, fährt 110 % der Dauer, pendelt 60 min [#20770062714]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20770127175: HOBBY, GRAN_FONDO COMPETE 247 km/1729 hm in 5 Wochen, Zeit Mo–So [2.5, 0.0, 2.75, 0.0, 0.0, 0.0, 0.0] (5.3 h), Ausfälle 13 %, fährt 117 % der Dauer, pendelt 70 min, Rennen Woche 3, krank Woche 5 (7 Tage), Reisewoche 3, Schichtdienst [#20770127175]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20770064260: PERFORMANCE, CRITERIUM FINISH 50 km/650 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.75, 5.5] (9.3 h), Ausfälle 2 %, fährt 97 % der Dauer, Rennen Woche 4, krank Woche 1 (12 Tage) [#20770064260]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20770063319: BEGINNER, ULTRA FINISH 535 km/7490 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.5, 2.0] (4.5 h), Ausfälle 2 %, fährt 128 % der Dauer, pendelt 80 min [#20770063319]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20770125111: BEGINNER, GRAVEL GOOD_TIME 130 km/3250 hm in 11 Wochen, Zeit Mo–So [1.25, 0.75, 1.0, 0.0, 0.0, 0.0, 1.0] (4.0 h), Ausfälle 14 %, fährt 90 % der Dauer, pendelt 40 min, Rennen Woche 3, krank Woche 2 (5 Tage) [#20770125111]
