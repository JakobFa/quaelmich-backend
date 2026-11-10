# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

76000 zufällige Fahrer in 1506 s (Startwert-Basis 20767 = 2026-11-10). 4 (0.0 %) mit harten Befunden, 56933 (74.9 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 4 | 0.0 % | 4 | Startwert 20767125301: Woche ab 2027-01-04: Frische bis -65 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 31901 | 42.0 % | 88909 | Startwert 20767062441: Woche ab 2026-11-02: Frische bis -37 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 19640 | 25.8 % | 74336 | Startwert 20767066686: Woche ab 2026-11-16: geplant 196 TSS, 3.1-mal so viel wie zuletzt gefahren (64/Woche, beste Woche 154) |
| # × … – eintönig | 18331 | 24.1 % | 75695 | Startwert 20767062376: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 15772 | 20.8 % | 17028 | Startwert 20767062696: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3489 | 4.6 % | 21148 | Startwert 20767071200: 2026-11-09: harte Einheit (VO2max 4 × 4) am Tag nach 300 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3063 | 4.0 % | 3063 | Startwert 20767062332: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2354 | 3.1 % | 2354 | Startwert 20767062445: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 1942 | 2.6 % | 1942 | Startwert 20767062441: Event-Tag: Frische -12 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1919 | 2.5 % | 1919 | Startwert 20767062392: Event-Tag: Frische -7 TSB passt nicht zum Ziel GOOD_TIME |
| Fitness steigt um # CTL | 1839 | 2.4 % | 3411 | Startwert 20767076626: Woche ab 2026-11-16: Fitness steigt um 8.1 CTL (Grenze 8) |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1724 | 2.3 % | 1724 | Startwert 20767072080: Woche ab 2026-11-30: nach 7 Tagen Krankheit 332 min geplant, mehr als davor (329) |
| Erholungswoche direkt vor dem Tapering | 482 | 0.6 % | 482 | Startwert 20767067137: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 362 | 0.5 % | 362 | Startwert 20767064893: Event-Tag: Frische -11 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 78 | 0.1 % | 78 | Startwert 20767105100: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20767125301: PERFORMANCE, BIKEPACKING COMPETE 1010 km/5050 hm in 18 Wochen, Zeit Mo–So [2.0, 3.0, 3.0, 3.0, 2.0, 5.5, 7.0] (25.5 h), Ausfälle 12 %, fährt 102 % der Dauer, pendelt 20 min, Urlaub Woche 13 (2 Wo.) [#20767125301]
- **Frische bis # TSB gefallen** – Startwert 20767062441: HOBBY, ULTRA COMPETE 328 km/2296 hm in 2 Wochen, Zeit Mo–So [0.75, 2.25, 1.25, 0.75, 2.25, 1.0, 0.75] (9.0 h), Ausfälle 1 %, fährt 127 % der Dauer, pendelt 80 min [#20767062441]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20767066686: HOBBY, GRAVEL COMPETE 100 km/1400 hm in 3 Wochen, Zeit Mo–So [0.0, 2.0, 0.0, 2.25, 0.0, 0.5, 1.0] (5.8 h), Ausfälle 20 %, fährt 90 % der Dauer, krank Woche 3 (12 Tage), Schichtdienst [#20767066686]
- **# × … – eintönig** – Startwert 20767062376: HOBBY, GRAVEL COMPETE 194 km/3686 hm in 2 Wochen, Zeit Mo–So [0.5, 1.0, 1.5, 0.75, 0.0, 1.75, 1.0] (6.5 h), Ausfälle 0 %, fährt 115 % der Dauer [#20767062376]
- **# Belastungswochen am Stück** – Startwert 20767062696: HOBBY, TIME_TRIAL GOOD_TIME 43 km/731 hm in 6 Wochen, Zeit Mo–So [0.0, 3.0, 0.75, 0.0, 1.5, 2.25, 1.75] (9.3 h), Ausfälle 12 %, fährt 86 % der Dauer, pendelt 50 min, Rennen Woche 5 [#20767062696]
- **harte Einheit am Tag nach # min geplant** – Startwert 20767071200: AMBITIOUS, ROAD_RACE GOOD_TIME 165 km/3630 hm in 2 Wochen, Zeit Mo–So [1.25, 1.25, 1.5, 2.0, 1.25, 1.75, 4.5] (13.5 h), Ausfälle 13 %, unter der Woche +47 %, fährt 93 % der Dauer, pendelt 70 min, Schichtdienst [#20767071200]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20767062332: HOBBY, CRITERIUM COMPETE 38 km/874 hm in 5 Wochen, Zeit Mo–So [2.0, 1.25, 2.25, 0.0, 0.0, 0.5, 1.0] (7.0 h), Ausfälle 20 %, fährt 80 % der Dauer, Urlaub Woche 3 (1 Wo.), Rennen Woche 2, krank Woche 3 (10 Tage) [#20767062332]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20767062445: HOBBY, ROAD_RACE GOOD_TIME 155 km/1240 hm in 5 Wochen, Zeit Mo–So [1.0, 1.5, 1.0, 1.25, 1.5, 4.0, 2.25] (12.5 h), Ausfälle 1 %, fährt 100 % der Dauer, pendelt 30 min, Urlaub Woche 2 (1 Wo.) [#20767062445]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20767062441: HOBBY, ULTRA COMPETE 328 km/2296 hm in 2 Wochen, Zeit Mo–So [0.75, 2.25, 1.25, 0.75, 2.25, 1.0, 0.75] (9.0 h), Ausfälle 1 %, fährt 127 % der Dauer, pendelt 80 min [#20767062441]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20767062392: HOBBY, TIME_TRIAL GOOD_TIME 89 km/801 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 1.25, 1.25, 0.5, 1.5, 2.0] (6.5 h), Ausfälle 18 %, fährt 84 % der Dauer, pendelt 70 min [#20767062392]
- **Fitness steigt um # CTL** – Startwert 20767076626: PERFORMANCE, ULTRA COMPETE 844 km/19412 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 1.0, 0.0, 1.25, 4.25, 6.0] (12.5 h), Ausfälle 19 %, unter der Woche +44 %, fährt 129 % der Dauer, pendelt 20 min [#20767076626]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20767072080: AMBITIOUS, ULTRA COMPETE 845 km/19435 hm in 5 Wochen, Zeit Mo–So [0.75, 1.25, 1.25, 0.5, 1.5, 2.25, 0.0] (7.5 h), Ausfälle 9 %, unter der Woche +51 %, fährt 96 % der Dauer, pendelt 50 min, Rennen Woche 3, krank Woche 4 (7 Tage), Schichtdienst [#20767072080]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20767067137: BEGINNER, CRITERIUM FINISH 50 km/1150 hm in 4 Wochen, Zeit Mo–So [1.25, 0.75, 1.25, 1.0, 0.75, 2.75, 1.5] (9.3 h), Ausfälle 19 %, fährt 84 % der Dauer, pendelt 80 min, krank Woche 1 (12 Tage) [#20767067137]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20767064893: BEGINNER, GRAN_FONDO FINISH 102 km/2244 hm in 2 Wochen, Zeit Mo–So [0.5, 1.25, 0.0, 0.75, 1.25, 1.5, 0.5] (5.8 h), Ausfälle 7 %, fährt 116 % der Dauer, pendelt 80 min, Schichtdienst [#20767064893]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20767105100: HOBBY, GRAVEL FINISH 50 km/550 hm in 10 Wochen, Zeit Mo–So [1.0, 0.75, 1.25, 1.5, 1.25, 2.0, 1.75] (9.5 h), Ausfälle 1 %, unter der Woche +55 %, fährt 121 % der Dauer, Reisewoche 1 [#20767105100]
