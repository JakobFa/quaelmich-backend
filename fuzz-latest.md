# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

58400 zufällige Fahrer in 1503 s (Startwert-Basis 20765 = 2026-11-08). 4 (0.0 %) mit harten Befunden, 43588 (74.6 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 4 | 0.0 % | 5 | Startwert 20765094455: Woche ab 2027-01-25: Frische bis -64 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 24415 | 41.8 % | 67404 | Startwert 20765062477: Woche ab 2026-11-02: Frische bis -65 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 14816 | 25.4 % | 56488 | Startwert 20765064148: Woche ab 2026-11-16: geplant 171 TSS, 3.0-mal so viel wie zuletzt gefahren (58/Woche, beste Woche 154) |
| # × … – eintönig | 14615 | 25.0 % | 61974 | Startwert 20765062388: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 11423 | 19.6 % | 12413 | Startwert 20765062374: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 2439 | 4.2 % | 14743 | Startwert 20765062477: 2026-11-08: harte Einheit (Over-Unders 3 × 15) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 2333 | 4.0 % | 2333 | Startwert 20765066474: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 1767 | 3.0 % | 1767 | Startwert 20765066849: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 1379 | 2.4 % | 1379 | Startwert 20765063242: Event-Tag: Frische -15 TSB passt nicht zum Ziel COMPETE |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1364 | 2.3 % | 1364 | Startwert 20765084503: Woche ab 2026-12-07: nach 8 Tagen Krankheit 94 min geplant, mehr als davor (87) |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1360 | 2.3 % | 1360 | Startwert 20765062651: Event-Tag: Frische 29 TSB passt nicht zum Ziel GOOD_TIME |
| Fitness steigt um # CTL | 1218 | 2.1 % | 2258 | Startwert 20765062395: Woche ab 2026-11-16: Fitness steigt um 8.0 CTL (Grenze 8) |
| Erholungswoche direkt vor dem Tapering | 380 | 0.7 % | 380 | Startwert 20765063015: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 313 | 0.5 % | 313 | Startwert 20765063836: Event-Tag: Frische -13 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 75 | 0.1 % | 75 | Startwert 20765113597: Woche ab 2026-12-07: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20765094455: PERFORMANCE, GRAVEL COMPETE 193 km/1737 hm in 14 Wochen, Zeit Mo–So [3.25, 2.0, 2.75, 2.25, 3.25, 5.25, 7.5] (26.3 h), Ausfälle 11 %, fährt 105 % der Dauer, Urlaub Woche 7 (1 Wo.) [#20765094455]
- **Frische bis # TSB gefallen** – Startwert 20765062477: PERFORMANCE, GRAVEL COMPETE 159 km/2544 hm in 2 Wochen, Zeit Mo–So [0.0, 2.5, 3.75, 2.0, 3.75, 3.25, 2.0] (17.3 h), Ausfälle 15 %, fährt 128 % der Dauer, pendelt 70 min, Schichtdienst [#20765062477]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20765064148: BEGINNER, CRITERIUM COMPETE 61 km/1586 hm in 3 Wochen, Zeit Mo–So [0.0, 0.5, 0.5, 0.75, 0.5, 1.25, 2.25] (5.8 h), Ausfälle 14 %, fährt 122 % der Dauer, krank Woche 2 (16 Tage), Schichtdienst [#20765064148]
- **# × … – eintönig** – Startwert 20765062388: PERFORMANCE, CRITERIUM GOOD_TIME 64 km/1664 hm in 2 Wochen, Zeit Mo–So [2.5, 0.0, 2.25, 1.5, 1.5, 3.5, 3.0] (14.3 h), Ausfälle 16 %, fährt 121 % der Dauer, pendelt 80 min [#20765062388]
- **# Belastungswochen am Stück** – Startwert 20765062374: PERFORMANCE, CRITERIUM COMPETE 52 km/1300 hm in 6 Wochen, Zeit Mo–So [2.0, 3.0, 1.5, 1.5, 1.5, 6.75, 6.75] (23.0 h), Ausfälle 2 %, unter der Woche +20 %, fährt 98 % der Dauer [#20765062374]
- **harte Einheit am Tag nach # min geplant** – Startwert 20765062477: PERFORMANCE, GRAVEL COMPETE 159 km/2544 hm in 2 Wochen, Zeit Mo–So [0.0, 2.5, 3.75, 2.0, 3.75, 3.25, 2.0] (17.3 h), Ausfälle 15 %, fährt 128 % der Dauer, pendelt 70 min, Schichtdienst [#20765062477]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20765066474: PERFORMANCE, TIME_TRIAL COMPETE 48 km/720 hm in 5 Wochen, Zeit Mo–So [2.25, 3.25, 3.25, 2.75, 2.25, 7.25, 7.5] (28.5 h), Ausfälle 13 %, fährt 127 % der Dauer, Rennen Woche 2 [#20765066474]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20765066849: PERFORMANCE, ROAD_RACE FINISH 119 km/2737 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 4.0, 1.75, 0.0, 2.75, 0.0] (8.5 h), Ausfälle 4 %, fährt 113 % der Dauer, Urlaub Woche 2 (1 Wo.) [#20765066849]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20765063242: BEGINNER, BIKEPACKING COMPETE 905 km/15385 hm in 2 Wochen, Zeit Mo–So [1.5, 0.0, 0.75, 0.75, 1.25, 1.25, 0.0] (5.5 h), Ausfälle 20 %, fährt 102 % der Dauer, pendelt 90 min [#20765063242]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20765084503: HOBBY, TIME_TRIAL FINISH 85 km/1785 hm in 5 Wochen, Zeit Mo–So [0.0, 0.75, 2.75, 0.0, 0.0, 0.0, 0.0] (3.5 h), Ausfälle 12 %, fährt 116 % der Dauer, krank Woche 4 (8 Tage), Reisewoche 2 [#20765084503]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20765062651: PERFORMANCE, GRAVEL GOOD_TIME 83 km/249 hm in 2 Wochen, Zeit Mo–So [1.5, 2.5, 1.75, 1.75, 2.25, 4.0, 6.75] (20.5 h), Ausfälle 20 %, fährt 106 % der Dauer [#20765062651]
- **Fitness steigt um # CTL** – Startwert 20765062395: PERFORMANCE, TIME_TRIAL COMPETE 63 km/1512 hm in 4 Wochen, Zeit Mo–So [2.5, 4.0, 1.75, 0.0, 1.75, 4.0, 1.0] (15.0 h), Ausfälle 7 %, fährt 103 % der Dauer, pendelt 70 min [#20765062395]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20765063015: PERFORMANCE, TIME_TRIAL GOOD_TIME 66 km/330 hm in 4 Wochen, Zeit Mo–So [2.5, 2.25, 2.0, 2.5, 1.75, 5.0, 5.0] (21.0 h), Ausfälle 19 %, fährt 91 % der Dauer, Urlaub Woche 3 (2 Wo.), Rennen Woche 3, krank Woche 1 (12 Tage), Schichtdienst [#20765063015]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20765063836: BEGINNER, GRAN_FONDO FINISH 223 km/3568 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 1.25, 1.0, 1.5, 1.5, 0.0] (7.5 h), Ausfälle 0 %, fährt 120 % der Dauer, pendelt 90 min [#20765063836]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20765113597: HOBBY, TIME_TRIAL COMPETE 44 km/396 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 1.0, 2.5, 1.0, 0.0, 0.5] (5.0 h), Ausfälle 14 %, fährt 112 % der Dauer, pendelt 40 min, Rennen Woche 4, krank Woche 2 (9 Tage) [#20765113597]
