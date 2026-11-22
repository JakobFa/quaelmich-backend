# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

96400 zufällige Fahrer in 1501 s (Startwert-Basis 20779 = 2026-11-22). 3 (0.0 %) mit harten Befunden, 73277 (76.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 3 | 0.0 % | 3 | Startwert 20779142902: Woche ab 2027-03-08: Frische bis -61 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 41533 | 43.1 % | 115995 | Startwert 20779062356: Woche ab 2026-11-02: Frische bis -36 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 26149 | 27.1 % | 99634 | Startwert 20779086479: Woche ab 2026-11-16: geplant 114 TSS, 1.8-mal so viel wie zuletzt gefahren (65/Woche, beste Woche 70) |
| # × … – eintönig | 23930 | 24.8 % | 102874 | Startwert 20779062562: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20197 | 21.0 % | 21890 | Startwert 20779062639: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4332 | 4.5 % | 26477 | Startwert 20779064608: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3913 | 4.1 % | 3913 | Startwert 20779062661: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2957 | 3.1 % | 5556 | Startwert 20779064631: Woche ab 2026-11-16: Fitness steigt um 12.3 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2907 | 3.0 % | 2907 | Startwert 20779064888: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2649 | 2.7 % | 2649 | Startwert 20779064771: Event-Tag: Frische -6 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2617 | 2.7 % | 2617 | Startwert 20779063011: Event-Tag: Frische -5 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2133 | 2.2 % | 2133 | Startwert 20779069696: Woche ab 2026-11-30: nach 7 Tagen Krankheit 300 min geplant, mehr als davor (237) |
| Erholungswoche direkt vor dem Tapering | 633 | 0.7 % | 633 | Startwert 20779064850: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 484 | 0.5 % | 484 | Startwert 20779075617: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 129 | 0.1 % | 129 | Startwert 20779077715: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20779142902: PERFORMANCE, ULTRA COMPETE 706 km/18356 hm in 20 Wochen, Zeit Mo–So [3.0, 3.0, 3.0, 3.5, 3.0, 7.25, 3.75] (26.5 h), Ausfälle 12 %, fährt 108 % der Dauer, Urlaub Woche 14 (2 Wo.), krank Woche 7 (14 Tage), Reisewoche 6 [#20779142902]
- **Frische bis # TSB gefallen** – Startwert 20779062356: AMBITIOUS, GRAN_FONDO GOOD_TIME 190 km/3800 hm in 2 Wochen, Zeit Mo–So [1.75, 0.75, 1.75, 2.0, 0.75, 3.25, 1.0] (11.3 h), Ausfälle 3 %, fährt 113 % der Dauer, pendelt 90 min [#20779062356]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20779086479: HOBBY, GRAVEL FINISH 236 km/5428 hm in 2 Wochen, Zeit Mo–So [0.75, 1.25, 0.0, 0.0, 0.75, 0.0, 0.0] (2.8 h), Ausfälle 4 %, unter der Woche +28 %, fährt 83 % der Dauer [#20779086479]
- **# × … – eintönig** – Startwert 20779062562: AMBITIOUS, GRAN_FONDO COMPETE 107 km/1391 hm in 2 Wochen, Zeit Mo–So [1.75, 1.5, 2.25, 1.25, 1.75, 2.25, 2.75] (13.5 h), Ausfälle 18 %, fährt 83 % der Dauer [#20779062562]
- **# Belastungswochen am Stück** – Startwert 20779062639: AMBITIOUS, GRAVEL GOOD_TIME 125 km/1250 hm in 6 Wochen, Zeit Mo–So [2.0, 1.25, 1.25, 2.0, 1.5, 4.5, 5.0] (17.5 h), Ausfälle 17 %, fährt 108 % der Dauer, Rennen Woche 5 [#20779062639]
- **harte Einheit am Tag nach # min geplant** – Startwert 20779064608: PERFORMANCE, BIKEPACKING GOOD_TIME 1375 km/26125 hm in 2 Wochen, Zeit Mo–So [1.75, 2.25, 1.5, 0.0, 1.75, 1.75, 4.25] (13.3 h), Ausfälle 2 %, fährt 127 % der Dauer, pendelt 60 min, Schichtdienst [#20779064608]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20779062661: AMBITIOUS, BIKEPACKING COMPETE 462 km/7392 hm in 5 Wochen, Zeit Mo–So [2.0, 1.0, 1.25, 1.75, 2.0, 4.25, 2.25] (14.5 h), Ausfälle 16 %, fährt 80 % der Dauer, Urlaub Woche 4 (3 Wo.), Rennen Woche 2, krank Woche 4 (17 Tage) [#20779062661]
- **Fitness steigt um # CTL** – Startwert 20779064631: PERFORMANCE, BIKEPACKING COMPETE 444 km/3996 hm in 4 Wochen, Zeit Mo–So [0.75, 0.0, 0.75, 2.5, 1.75, 4.0, 5.0] (14.8 h), Ausfälle 20 %, fährt 116 % der Dauer, pendelt 80 min, Schichtdienst [#20779064631]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20779064888: AMBITIOUS, GRAVEL FINISH 107 km/2461 hm in 5 Wochen, Zeit Mo–So [2.75, 0.75, 0.0, 0.0, 0.0, 1.25, 2.25] (7.0 h), Ausfälle 14 %, fährt 104 % der Dauer, pendelt 50 min, Urlaub Woche 2 (1 Wo.) [#20779064888]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20779064771: PERFORMANCE, TIME_TRIAL COMPETE 61 km/1586 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 3.75, 1.75, 1.75, 1.5, 0.0] (8.8 h), Ausfälle 2 %, fährt 118 % der Dauer, pendelt 90 min [#20779064771]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20779063011: AMBITIOUS, BIKEPACKING GOOD_TIME 1580 km/18960 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 3.5, 0.0, 3.75] (7.3 h), Ausfälle 15 %, fährt 86 % der Dauer, pendelt 80 min [#20779063011]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20779069696: PERFORMANCE, BIKEPACKING FINISH 1000 km/17000 hm in 5 Wochen, Zeit Mo–So [1.25, 4.25, 3.25, 0.0, 0.0, 0.0, 0.0] (8.8 h), Ausfälle 0 %, fährt 103 % der Dauer, krank Woche 4 (7 Tage) [#20779069696]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20779064850: AMBITIOUS, TIME_TRIAL GOOD_TIME 15 km/120 hm in 4 Wochen, Zeit Mo–So [2.0, 0.0, 2.0, 2.0, 1.25, 4.25, 3.0] (14.5 h), Ausfälle 18 %, fährt 105 % der Dauer, Rennen Woche 2, krank Woche 1 (12 Tage) [#20779064850]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20779075617: BEGINNER, BIKEPACKING FINISH 1594 km/23910 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.0, 2.75] (5.8 h), Ausfälle 5 %, unter der Woche +42 %, fährt 124 % der Dauer, pendelt 80 min [#20779075617]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20779077715: BEGINNER, GRAN_FONDO GOOD_TIME 185 km/2220 hm in 9 Wochen, Zeit Mo–So [0.0, 0.75, 1.75, 0.0, 0.0, 0.0, 1.5] (4.0 h), Ausfälle 16 %, fährt 108 % der Dauer, pendelt 60 min, Rennen Woche 3, krank Woche 2 (4 Tage) [#20779077715]
