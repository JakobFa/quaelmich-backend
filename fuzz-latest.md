# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

78800 zufällige Fahrer in 1504 s (Startwert-Basis 20781 = 2026-11-24). 6 (0.0 %) mit harten Befunden, 59724 (75.8 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 6 | 0.0 % | 6 | Startwert 20781064714: Woche ab 2027-04-19: Frische bis -63 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 33618 | 42.7 % | 94398 | Startwert 20781063110: Woche ab 2026-11-09: Frische bis -40 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 21432 | 27.2 % | 81738 | Startwert 20781076885: Woche ab 2026-11-16: geplant 101 TSS, 1.9-mal so viel wie zuletzt gefahren (54/Woche, beste Woche 74) |
| # × … – eintönig | 19886 | 25.2 % | 84922 | Startwert 20781063673: Woche ab 2026-11-02: 4 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 16131 | 20.5 % | 17489 | Startwert 20781063071: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3434 | 4.4 % | 21088 | Startwert 20781083156: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 240 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3204 | 4.1 % | 3204 | Startwert 20781064074: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2389 | 3.0 % | 2389 | Startwert 20781062835: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2316 | 2.9 % | 4537 | Startwert 20781071059: Woche ab 2026-11-16: Fitness steigt um 8.0 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2115 | 2.7 % | 2115 | Startwert 20781063487: Event-Tag: Frische -7 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2023 | 2.6 % | 2023 | Startwert 20781064442: Event-Tag: Frische -11 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1789 | 2.3 % | 1789 | Startwert 20781079755: Woche ab 2026-11-30: nach 7 Tagen Krankheit 353 min geplant, mehr als davor (272) |
| Erholungswoche direkt vor dem Tapering | 516 | 0.7 % | 516 | Startwert 20781071780: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 396 | 0.5 % | 396 | Startwert 20781068569: Event-Tag: Frische -11 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 87 | 0.1 % | 87 | Startwert 20781068593: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20781064714: PERFORMANCE, GRAVEL FINISH 243 km/4860 hm in 25 Wochen, Zeit Mo–So [3.5, 2.0, 2.25, 3.5, 2.5, 3.25, 5.25] (22.3 h), Ausfälle 16 %, fährt 108 % der Dauer, Urlaub Woche 18 (3 Wo.) [#20781064714]
- **Frische bis # TSB gefallen** – Startwert 20781063110: HOBBY, BIKEPACKING GOOD_TIME 633 km/7596 hm in 2 Wochen, Zeit Mo–So [1.25, 1.75, 1.0, 1.5, 1.75, 1.75, 4.0] (13.0 h), Ausfälle 6 %, fährt 125 % der Dauer [#20781063110]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20781076885: HOBBY, BIKEPACKING GOOD_TIME 608 km/6688 hm in 2 Wochen, Zeit Mo–So [0.0, 1.25, 0.0, 0.0, 1.25, 0.0, 0.0] (2.5 h), Ausfälle 16 %, unter der Woche +20 %, fährt 126 % der Dauer, Schichtdienst [#20781076885]
- **# × … – eintönig** – Startwert 20781063673: BEGINNER, ULTRA FINISH 800 km/15200 hm in 2 Wochen, Zeit Mo–So [0.0, 1.0, 1.0, 1.0, 0.5, 2.25, 0.75] (6.5 h), Ausfälle 7 %, fährt 81 % der Dauer [#20781063673]
- **# Belastungswochen am Stück** – Startwert 20781063071: HOBBY, TIME_TRIAL GOOD_TIME 42 km/630 hm in 6 Wochen, Zeit Mo–So [1.25, 1.25, 1.5, 1.25, 0.75, 4.75, 4.75] (15.5 h), Ausfälle 1 %, unter der Woche +45 %, fährt 118 % der Dauer, Reisewoche 6 [#20781063071]
- **harte Einheit am Tag nach # min geplant** – Startwert 20781083156: HOBBY, ULTRA GOOD_TIME 529 km/5290 hm in 2 Wochen, Zeit Mo–So [2.0, 1.75, 1.0, 1.25, 1.75, 1.75, 3.5] (13.0 h), Ausfälle 19 %, fährt 130 % der Dauer, pendelt 30 min, Schichtdienst [#20781083156]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20781064074: BEGINNER, BIKEPACKING COMPETE 1643 km/13144 hm in 5 Wochen, Zeit Mo–So [1.5, 1.0, 1.0, 1.0, 1.0, 3.0, 2.25] (10.8 h), Ausfälle 3 %, unter der Woche +57 %, fährt 96 % der Dauer, pendelt 60 min, Rennen Woche 2, krank Woche 3 (13 Tage) [#20781064074]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20781062835: HOBBY, CRITERIUM FINISH 41 km/82 hm in 5 Wochen, Zeit Mo–So [0.0, 1.5, 2.0, 1.5, 0.0, 1.0, 1.75] (7.8 h), Ausfälle 11 %, unter der Woche +58 %, fährt 81 % der Dauer, pendelt 20 min, Urlaub Woche 2 (1 Wo.), Rennen Woche 3, Reisewoche 5 [#20781062835]
- **Fitness steigt um # CTL** – Startwert 20781071059: AMBITIOUS, BIKEPACKING GOOD_TIME 440 km/1320 hm in 4 Wochen, Zeit Mo–So [1.75, 2.25, 1.0, 1.25, 2.0, 3.5, 4.75] (16.5 h), Ausfälle 16 %, fährt 118 % der Dauer, pendelt 40 min [#20781071059]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20781063487: BEGINNER, BIKEPACKING COMPETE 1410 km/5640 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 1.75, 2.25] (4.0 h), Ausfälle 6 %, unter der Woche +38 %, fährt 126 % der Dauer, pendelt 80 min, Schichtdienst [#20781063487]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20781064442: BEGINNER, ROAD_RACE GOOD_TIME 93 km/1488 hm in 2 Wochen, Zeit Mo–So [0.0, 1.0, 1.75, 0.0, 0.75, 1.5, 1.5] (6.5 h), Ausfälle 10 %, fährt 110 % der Dauer, pendelt 80 min, Schichtdienst [#20781064442]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20781079755: HOBBY, GRAN_FONDO GOOD_TIME 133 km/1862 hm in 5 Wochen, Zeit Mo–So [0.75, 0.5, 1.0, 0.0, 0.5, 2.5, 3.75] (9.0 h), Ausfälle 0 %, fährt 88 % der Dauer, krank Woche 4 (7 Tage), Schichtdienst [#20781079755]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20781071780: AMBITIOUS, GRAN_FONDO GOOD_TIME 174 km/2262 hm in 4 Wochen, Zeit Mo–So [0.0, 1.5, 1.25, 0.0, 0.5, 1.75, 2.5] (7.5 h), Ausfälle 24 %, fährt 76 % der Dauer, krank Woche 1 (13 Tage) [#20781071780]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20781068569: HOBBY, GRAN_FONDO FINISH 230 km/5520 hm in 2 Wochen, Zeit Mo–So [0.0, 1.25, 0.0, 2.25, 1.75, 2.75, 3.0] (11.0 h), Ausfälle 12 %, unter der Woche +36 %, fährt 123 % der Dauer, pendelt 90 min [#20781068569]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20781068593: HOBBY, ROAD_RACE FINISH 112 km/2240 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 1.75, 3.25] (5.0 h), Ausfälle 18 %, unter der Woche +27 %, fährt 76 % der Dauer, pendelt 30 min, Rennen Woche 3, Reisewoche 1 [#20781068593]
