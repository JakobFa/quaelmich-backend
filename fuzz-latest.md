# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 908 s (Startwert-Basis 20772 = 2026-11-15). 5 (0.0 %) mit harten Befunden, 76013 (76.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 5 | 0.0 % | 5 | Startwert 20772073306: Woche ab 2026-11-09: Frische bis -68 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42901 | 42.9 % | 120212 | Startwert 20772064475: Woche ab 2026-11-02: Frische bis -50 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27161 | 27.2 % | 103796 | Startwert 20772062316: Woche ab 2026-11-23: geplant 114 TSS, 2.0-mal so viel wie zuletzt gefahren (58/Woche, beste Woche 98) |
| # × … – eintönig | 24834 | 24.8 % | 104590 | Startwert 20772062773: Woche ab 2026-11-02: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20832 | 20.8 % | 22535 | Startwert 20772062502: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4377 | 4.4 % | 27431 | Startwert 20772070403: 2026-11-06: harte Einheit (Over-Unders 3 × 16 (3/1)) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4033 | 4.0 % | 4033 | Startwert 20772063577: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3045 | 3.0 % | 3045 | Startwert 20772063577: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3033 | 3.0 % | 5779 | Startwert 20772073471: Woche ab 2026-11-16: Fitness steigt um 9.3 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2811 | 2.8 % | 2811 | Startwert 20772063398: Event-Tag: Frische 34 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2671 | 2.7 % | 2671 | Startwert 20772063515: Event-Tag: Frische 26 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2277 | 2.3 % | 2277 | Startwert 20772160815: Woche ab 2026-11-30: nach 7 Tagen Krankheit 105 min geplant, mehr als davor (97) |
| Erholungswoche direkt vor dem Tapering | 610 | 0.6 % | 610 | Startwert 20772062869: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 523 | 0.5 % | 523 | Startwert 20772073943: Event-Tag: Frische -14 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 134 | 0.1 % | 134 | Startwert 20772091229: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20772073306: PERFORMANCE, GRAVEL GOOD_TIME 241 km/1687 hm in 2 Wochen, Zeit Mo–So [2.75, 1.5, 2.5, 3.0, 3.25, 7.0, 6.5] (26.5 h), Ausfälle 12 %, fährt 107 % der Dauer, pendelt 50 min [#20772073306]
- **Frische bis # TSB gefallen** – Startwert 20772064475: AMBITIOUS, ULTRA GOOD_TIME 1451 km/15961 hm in 2 Wochen, Zeit Mo–So [2.25, 2.0, 1.75, 1.25, 2.25, 6.0, 3.25] (18.8 h), Ausfälle 13 %, fährt 113 % der Dauer, pendelt 50 min [#20772064475]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20772062316: AMBITIOUS, BIKEPACKING COMPETE 278 km/2780 hm in 3 Wochen, Zeit Mo–So [1.75, 0.0, 1.5, 0.75, 1.0, 1.5, 3.25] (9.8 h), Ausfälle 23 %, unter der Woche +41 %, fährt 97 % der Dauer, krank Woche 2 (9 Tage) [#20772062316]
- **# × … – eintönig** – Startwert 20772062773: AMBITIOUS, BIKEPACKING COMPETE 1168 km/30368 hm in 2 Wochen, Zeit Mo–So [1.25, 2.25, 1.5, 2.75, 0.75, 1.0, 1.25] (10.8 h), Ausfälle 6 %, unter der Woche +55 %, fährt 83 % der Dauer [#20772062773]
- **# Belastungswochen am Stück** – Startwert 20772062502: AMBITIOUS, CRITERIUM GOOD_TIME 39 km/351 hm in 6 Wochen, Zeit Mo–So [2.0, 2.0, 1.0, 1.25, 2.0, 4.25, 5.75] (18.3 h), Ausfälle 8 %, fährt 112 % der Dauer [#20772062502]
- **harte Einheit am Tag nach # min geplant** – Startwert 20772070403: PERFORMANCE, GRAVEL COMPETE 247 km/5434 hm in 2 Wochen, Zeit Mo–So [0.0, 2.5, 4.0, 3.25, 3.5, 0.0, 3.0] (16.3 h), Ausfälle 1 %, unter der Woche +33 %, fährt 76 % der Dauer, pendelt 70 min, Schichtdienst [#20772070403]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20772063577: PERFORMANCE, TIME_TRIAL COMPETE 16 km/336 hm in 5 Wochen, Zeit Mo–So [3.0, 3.25, 3.25, 2.25, 3.25, 6.75, 3.25] (25.0 h), Ausfälle 11 %, fährt 84 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 2 [#20772063577]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20772063577: PERFORMANCE, TIME_TRIAL COMPETE 16 km/336 hm in 5 Wochen, Zeit Mo–So [3.0, 3.25, 3.25, 2.25, 3.25, 6.75, 3.25] (25.0 h), Ausfälle 11 %, fährt 84 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 2 [#20772063577]
- **Fitness steigt um # CTL** – Startwert 20772073471: PERFORMANCE, GRAN_FONDO FINISH 229 km/5725 hm in 4 Wochen, Zeit Mo–So [1.75, 1.5, 2.0, 1.5, 2.75, 3.5, 7.0] (20.0 h), Ausfälle 11 %, fährt 77 % der Dauer, pendelt 60 min, Reisewoche 2 [#20772073471]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20772063398: PERFORMANCE, TIME_TRIAL COMPETE 10 km/230 hm in 2 Wochen, Zeit Mo–So [4.25, 2.25, 3.0, 0.0, 0.0, 5.0, 4.5] (19.0 h), Ausfälle 16 %, fährt 97 % der Dauer [#20772063398]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20772063515: PERFORMANCE, TIME_TRIAL GOOD_TIME 64 km/1280 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 5.5, 3.25] (8.8 h), Ausfälle 7 %, fährt 85 % der Dauer [#20772063515]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20772160815: BEGINNER, TIME_TRIAL GOOD_TIME 75 km/1875 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 1.5, 0.25, 0.75] (2.5 h), Ausfälle 17 %, fährt 130 % der Dauer, krank Woche 4 (7 Tage) [#20772160815]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20772062869: AMBITIOUS, ROAD_RACE GOOD_TIME 189 km/756 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.0, 1.5] (5.5 h), Ausfälle 23 %, fährt 85 % der Dauer, pendelt 30 min, Urlaub Woche 3 (1 Wo.), Rennen Woche 4, krank Woche 1 (13 Tage) [#20772062869]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20772073943: HOBBY, ULTRA FINISH 728 km/18928 hm in 2 Wochen, Zeit Mo–So [0.75, 0.0, 0.5, 1.25, 0.75, 3.0, 3.0] (9.3 h), Ausfälle 15 %, unter der Woche +31 %, fährt 119 % der Dauer, pendelt 80 min [#20772073943]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20772091229: AMBITIOUS, CRITERIUM FINISH 53 km/1325 hm in 11 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.75, 4.75] (8.5 h), Ausfälle 17 %, unter der Woche +37 %, fährt 124 % der Dauer, Reisewoche 2 [#20772091229]
