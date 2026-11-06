# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

81600 zufällige Fahrer in 1504 s (Startwert-Basis 20763 = 2026-11-06). 2 (0.0 %) mit harten Befunden, 67089 (82.2 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 2 | 0.0 % | 2 | Startwert 20763138474: Woche ab 2027-01-25: Frische bis -61 TSB gefallen (Grenze -60) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 38795 | 47.5 % | 193541 | Startwert 20763124681: Woche ab 2026-11-16: geplant 116 TSS, 2.2-mal so viel wie zuletzt gefahren (53/Woche) |
| Frische bis # TSB gefallen | 34799 | 42.6 % | 97200 | Startwert 20763062325: Woche ab 2026-11-02: Frische bis -36 TSB gefallen (Warnung ab -35, selbst verursacht) |
| # × … – eintönig | 20314 | 24.9 % | 89699 | Startwert 20763062444: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 16929 | 20.7 % | 18389 | Startwert 20763062313: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3942 | 4.8 % | 24454 | Startwert 20763063053: 2026-11-09: harte Einheit (Schwelle 2 × 10) am Tag nach 330 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3349 | 4.1 % | 3349 | Startwert 20763065913: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2532 | 3.1 % | 2532 | Startwert 20763064424: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2151 | 2.6 % | 2151 | Startwert 20763065251: Event-Tag: Frische 26 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2141 | 2.6 % | 2141 | Startwert 20763064523: Event-Tag: Frische 39 TSB passt nicht zum Ziel GOOD_TIME |
| Fitness steigt um # CTL | 2072 | 2.5 % | 3785 | Startwert 20763101242: Woche ab 2026-11-16: Fitness steigt um 9.4 CTL (Grenze 8) |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1881 | 2.3 % | 1881 | Startwert 20763068546: Woche ab 2026-11-30: nach 7 Tagen Krankheit 300 min geplant, mehr als davor (187) |
| Erholungswoche direkt vor dem Tapering | 542 | 0.7 % | 542 | Startwert 20763064872: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 391 | 0.5 % | 391 | Startwert 20763062996: Event-Tag: Frische -10 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 153 | 0.2 % | 153 | Startwert 20763063318: Woche ab 2026-12-07: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20763138474: PERFORMANCE, ULTRA COMPETE 652 km/10432 hm in 17 Wochen, Zeit Mo–So [0.0, 2.25, 0.0, 2.5, 2.0, 2.75, 6.0] (15.5 h), Ausfälle 19 %, fährt 108 % der Dauer, Rennen Woche 11 [#20763138474]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20763124681: BEGINNER, ULTRA GOOD_TIME 1145 km/25190 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 1.0, 1.0, 0.75, 0.0, 0.5] (5.5 h), Ausfälle 10 %, unter der Woche +46 %, fährt 104 % der Dauer [#20763124681]
- **Frische bis # TSB gefallen** – Startwert 20763062325: AMBITIOUS, BIKEPACKING GOOD_TIME 689 km/11713 hm in 2 Wochen, Zeit Mo–So [3.75, 0.0, 0.0, 2.5, 0.0, 3.75, 2.25] (12.3 h), Ausfälle 11 %, fährt 109 % der Dauer, pendelt 70 min [#20763062325]
- **# × … – eintönig** – Startwert 20763062444: AMBITIOUS, CRITERIUM COMPETE 69 km/1035 hm in 2 Wochen, Zeit Mo–So [1.0, 1.25, 2.25, 2.0, 2.5, 5.5, 5.5] (20.0 h), Ausfälle 20 %, fährt 128 % der Dauer [#20763062444]
- **# Belastungswochen am Stück** – Startwert 20763062313: AMBITIOUS, GRAVEL FINISH 64 km/1280 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.25, 3.0] (5.3 h), Ausfälle 24 %, fährt 129 % der Dauer, Schichtdienst [#20763062313]
- **harte Einheit am Tag nach # min geplant** – Startwert 20763063053: AMBITIOUS, GRAN_FONDO GOOD_TIME 229 km/5267 hm in 2 Wochen, Zeit Mo–So [1.25, 0.0, 1.25, 0.0, 0.0, 3.75, 4.75] (11.0 h), Ausfälle 16 %, fährt 102 % der Dauer, pendelt 60 min, Schichtdienst [#20763063053]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20763065913: AMBITIOUS, CRITERIUM FINISH 69 km/345 hm in 5 Wochen, Zeit Mo–So [1.25, 1.75, 2.25, 0.0, 0.0, 0.0, 1.75] (7.0 h), Ausfälle 2 %, fährt 126 % der Dauer, Rennen Woche 2, krank Woche 4 (19 Tage) [#20763065913]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20763064424: PERFORMANCE, BIKEPACKING COMPETE 1424 km/11392 hm in 5 Wochen, Zeit Mo–So [2.0, 1.0, 3.5, 1.25, 0.0, 4.0, 2.25] (14.0 h), Ausfälle 18 %, fährt 85 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 1 (9 Tage) [#20763064424]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20763065251: PERFORMANCE, BIKEPACKING COMPETE 961 km/15376 hm in 2 Wochen, Zeit Mo–So [4.0, 3.0, 0.0, 1.25, 5.0, 0.0, 4.25] (17.5 h), Ausfälle 14 %, fährt 118 % der Dauer [#20763065251]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20763064523: PERFORMANCE, ULTRA GOOD_TIME 1352 km/2704 hm in 2 Wochen, Zeit Mo–So [2.25, 2.5, 3.25, 2.5, 1.5, 6.5, 7.25] (25.8 h), Ausfälle 11 %, fährt 113 % der Dauer [#20763064523]
- **Fitness steigt um # CTL** – Startwert 20763101242: AMBITIOUS, BIKEPACKING COMPETE 725 km/4350 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.5, 5.25] (9.8 h), Ausfälle 6 %, unter der Woche +43 %, fährt 130 % der Dauer, pendelt 70 min, Reisewoche 2 [#20763101242]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20763068546: AMBITIOUS, ROAD_RACE GOOD_TIME 114 km/1140 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.0, 4.25] (7.3 h), Ausfälle 2 %, fährt 126 % der Dauer, pendelt 50 min, krank Woche 4 (7 Tage) [#20763068546]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20763064872: AMBITIOUS, CRITERIUM FINISH 34 km/612 hm in 4 Wochen, Zeit Mo–So [1.75, 0.0, 1.75, 1.0, 1.0, 3.0, 4.5] (13.0 h), Ausfälle 12 %, fährt 124 % der Dauer, pendelt 60 min, krank Woche 1 (13 Tage), Reisewoche 1 [#20763064872]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20763062996: AMBITIOUS, GRAVEL FINISH 177 km/1770 hm in 2 Wochen, Zeit Mo–So [0.0, 1.5, 0.0, 1.5, 0.5, 2.0, 3.5] (9.0 h), Ausfälle 5 %, fährt 127 % der Dauer, pendelt 80 min [#20763062996]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20763063318: HOBBY, CRITERIUM COMPETE 33 km/198 hm in 11 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.75, 1.25] (4.0 h), Ausfälle 14 %, unter der Woche +33 %, fährt 82 % der Dauer, pendelt 60 min, Rennen Woche 4, krank Woche 1 (12 Tage) [#20763063318]
