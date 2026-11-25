# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1104 s (Startwert-Basis 20782 = 2026-11-25). 6 (0.0 %) mit harten Befunden, 75830 (75.8 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 6 | 0.0 % | 7 | Startwert 20782070196: Woche ab 2027-01-18: Frische bis -64 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42721 | 42.7 % | 120118 | Startwert 20782062354: Woche ab 2026-11-02: Frische bis -36 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27252 | 27.3 % | 105118 | Startwert 20782079596: Woche ab 2026-11-16: geplant 120 TSS, 1.7-mal so viel wie zuletzt gefahren (72/Woche, beste Woche 98) |
| # × … – eintönig | 24878 | 24.9 % | 105885 | Startwert 20782062529: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20827 | 20.8 % | 22553 | Startwert 20782062796: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4411 | 4.4 % | 27213 | Startwert 20782064018: 2026-11-09: harte Einheit (Schwelle 1 × 20) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4045 | 4.0 % | 4045 | Startwert 20782063281: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3158 | 3.2 % | 3158 | Startwert 20782077764: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2942 | 2.9 % | 5599 | Startwert 20782068051: Woche ab 2026-11-16: Fitness steigt um 10.3 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2735 | 2.7 % | 2735 | Startwert 20782062413: Event-Tag: Frische 26 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2683 | 2.7 % | 2683 | Startwert 20782063370: Event-Tag: Frische -10 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2245 | 2.2 % | 2245 | Startwert 20782072831: Woche ab 2026-11-30: nach 7 Tagen Krankheit 458 min geplant, mehr als davor (320) |
| Erholungswoche direkt vor dem Tapering | 620 | 0.6 % | 620 | Startwert 20782062487: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 521 | 0.5 % | 521 | Startwert 20782079374: Event-Tag: Frische -14 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 91 | 0.1 % | 91 | Startwert 20782147978: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20782070196: PERFORMANCE, BIKEPACKING COMPETE 1530 km/38250 hm in 12 Wochen, Zeit Mo–So [1.75, 3.5, 2.25, 2.0, 3.25, 6.25, 8.0] (27.0 h), Ausfälle 8 %, fährt 108 % der Dauer [#20782070196]
- **Frische bis # TSB gefallen** – Startwert 20782062354: AMBITIOUS, GRAN_FONDO COMPETE 234 km/468 hm in 2 Wochen, Zeit Mo–So [1.5, 1.75, 2.5, 1.5, 1.75, 4.75, 3.5] (17.3 h), Ausfälle 22 %, unter der Woche +54 %, fährt 113 % der Dauer, pendelt 30 min [#20782062354]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20782079596: BEGINNER, GRAN_FONDO FINISH 139 km/1946 hm in 2 Wochen, Zeit Mo–So [0.25, 1.0, 1.0, 0.0, 0.75, 0.0, 0.75] (3.8 h), Ausfälle 22 %, fährt 106 % der Dauer, Schichtdienst [#20782079596]
- **# × … – eintönig** – Startwert 20782062529: AMBITIOUS, ROAD_RACE FINISH 76 km/1900 hm in 2 Wochen, Zeit Mo–So [1.75, 1.5, 0.0, 1.0, 1.5, 2.75, 3.5] (12.0 h), Ausfälle 3 %, fährt 83 % der Dauer, pendelt 30 min [#20782062529]
- **# Belastungswochen am Stück** – Startwert 20782062796: AMBITIOUS, GRAVEL GOOD_TIME 243 km/5346 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.5, 1.5] (6.0 h), Ausfälle 5 %, unter der Woche +33 %, fährt 87 % der Dauer, Reisewoche 6 [#20782062796]
- **harte Einheit am Tag nach # min geplant** – Startwert 20782064018: HOBBY, ULTRA FINISH 544 km/7616 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 0.75, 1.75, 1.5, 1.75, 2.75] (10.8 h), Ausfälle 10 %, fährt 105 % der Dauer, pendelt 50 min, Schichtdienst [#20782064018]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20782063281: AMBITIOUS, GRAN_FONDO FINISH 193 km/579 hm in 5 Wochen, Zeit Mo–So [1.0, 1.75, 0.0, 2.0, 1.25, 5.0, 4.75] (15.8 h), Ausfälle 6 %, fährt 91 % der Dauer, Rennen Woche 2 [#20782063281]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20782077764: PERFORMANCE, GRAVEL COMPETE 53 km/1378 hm in 5 Wochen, Zeit Mo–So [2.5, 1.75, 2.0, 1.5, 2.25, 3.0, 5.25] (18.3 h), Ausfälle 17 %, fährt 80 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 5 (6 Tage) [#20782077764]
- **Fitness steigt um # CTL** – Startwert 20782068051: AMBITIOUS, GRAN_FONDO COMPETE 111 km/2886 hm in 4 Wochen, Zeit Mo–So [2.0, 1.5, 2.25, 2.25, 1.5, 2.5, 5.75] (17.8 h), Ausfälle 17 %, fährt 127 % der Dauer, pendelt 80 min [#20782068051]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20782062413: AMBITIOUS, GRAN_FONDO COMPETE 159 km/2385 hm in 2 Wochen, Zeit Mo–So [1.5, 2.0, 1.25, 1.75, 1.75, 4.75, 2.25] (15.3 h), Ausfälle 1 %, unter der Woche +54 %, fährt 78 % der Dauer, Schichtdienst [#20782062413]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20782063370: AMBITIOUS, GRAN_FONDO GOOD_TIME 208 km/2704 hm in 2 Wochen, Zeit Mo–So [0.75, 1.0, 2.0, 0.0, 0.0, 1.25, 1.0] (6.0 h), Ausfälle 4 %, fährt 101 % der Dauer, pendelt 90 min [#20782063370]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20782072831: AMBITIOUS, GRAN_FONDO GOOD_TIME 183 km/3477 hm in 5 Wochen, Zeit Mo–So [0.75, 0.0, 1.75, 2.0, 1.5, 5.0, 2.5] (13.5 h), Ausfälle 8 %, unter der Woche +47 %, fährt 111 % der Dauer, krank Woche 4 (7 Tage), Reisewoche 2 [#20782072831]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20782062487: AMBITIOUS, GRAN_FONDO COMPETE 181 km/3620 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.25, 2.5] (5.8 h), Ausfälle 20 %, fährt 99 % der Dauer, krank Woche 1 (11 Tage), Schichtdienst [#20782062487]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20782079374: BEGINNER, ROAD_RACE FINISH 127 km/2667 hm in 2 Wochen, Zeit Mo–So [1.5, 0.75, 0.75, 1.0, 1.0, 2.25, 3.5] (10.8 h), Ausfälle 15 %, fährt 79 % der Dauer, pendelt 70 min [#20782079374]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20782147978: PERFORMANCE, BIKEPACKING COMPETE 1552 km/10864 hm in 9 Wochen, Zeit Mo–So [3.0, 0.0, 1.75, 1.0, 2.75, 0.0, 2.0] (10.5 h), Ausfälle 22 %, fährt 109 % der Dauer, Reisewoche 2, Schichtdienst [#20782147978]
