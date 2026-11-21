# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1361 s (Startwert-Basis 20778 = 2026-11-21). 2 (0.0 %) mit harten Befunden, 75964 (76.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 2 | 0.0 % | 2 | Startwert 20778160307: Woche ab 2026-12-14: Frische bis -61 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42865 | 42.9 % | 120032 | Startwert 20778067130: Woche ab 2026-11-09: Frische bis -45 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27398 | 27.4 % | 105668 | Startwert 20778101025: Woche ab 2026-11-16: geplant 99 TSS, 1.5-mal so viel wie zuletzt gefahren (64/Woche, beste Woche 77) |
| # × … – eintönig | 24435 | 24.4 % | 103590 | Startwert 20778062506: Woche ab 2026-11-09: 5 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20828 | 20.8 % | 22578 | Startwert 20778062500: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4421 | 4.4 % | 27571 | Startwert 20778066974: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4012 | 4.0 % | 4012 | Startwert 20778063366: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3045 | 3.0 % | 3045 | Startwert 20778067290: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3022 | 3.0 % | 5532 | Startwert 20778076084: Woche ab 2026-11-16: Fitness steigt um 9.7 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2751 | 2.8 % | 2751 | Startwert 20778062506: Event-Tag: Frische 30 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2701 | 2.7 % | 2701 | Startwert 20778063343: Event-Tag: Frische 26 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2311 | 2.3 % | 2311 | Startwert 20778103442: Woche ab 2026-11-30: nach 7 Tagen Krankheit 311 min geplant, mehr als davor (249) |
| Erholungswoche direkt vor dem Tapering | 587 | 0.6 % | 587 | Startwert 20778064658: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 504 | 0.5 % | 504 | Startwert 20778072910: Event-Tag: Frische -10 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 113 | 0.1 % | 113 | Startwert 20778078672: Woche ab 2026-12-07: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20778160307: PERFORMANCE, BIKEPACKING COMPETE 798 km/6384 hm in 8 Wochen, Zeit Mo–So [3.25, 1.5, 3.25, 2.0, 2.0, 4.25, 6.5] (22.8 h), Ausfälle 4 %, fährt 109 % der Dauer [#20778160307]
- **Frische bis # TSB gefallen** – Startwert 20778067130: AMBITIOUS, ULTRA GOOD_TIME 531 km/8496 hm in 2 Wochen, Zeit Mo–So [1.0, 0.75, 0.0, 0.0, 2.0, 1.75, 5.0] (10.5 h), Ausfälle 10 %, unter der Woche +29 %, fährt 96 % der Dauer, pendelt 70 min, Schichtdienst [#20778067130]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20778101025: BEGINNER, GRAVEL COMPETE 229 km/1832 hm in 2 Wochen, Zeit Mo–So [0.5, 1.25, 0.5, 1.25, 0.5, 0.0, 0.0] (4.0 h), Ausfälle 8 %, unter der Woche +29 %, fährt 114 % der Dauer [#20778101025]
- **# × … – eintönig** – Startwert 20778062506: PERFORMANCE, TIME_TRIAL COMPETE 27 km/594 hm in 2 Wochen, Zeit Mo–So [2.0, 2.75, 1.75, 2.25, 3.25, 6.0, 7.25] (25.3 h), Ausfälle 24 %, fährt 110 % der Dauer [#20778062506]
- **# Belastungswochen am Stück** – Startwert 20778062500: PERFORMANCE, ULTRA FINISH 785 km/3140 hm in 6 Wochen, Zeit Mo–So [3.0, 4.0, 0.0, 0.0, 2.5, 0.0, 0.0] (9.5 h), Ausfälle 9 %, fährt 116 % der Dauer, pendelt 20 min, Schichtdienst [#20778062500]
- **harte Einheit am Tag nach # min geplant** – Startwert 20778066974: AMBITIOUS, BIKEPACKING GOOD_TIME 211 km/1899 hm in 2 Wochen, Zeit Mo–So [1.75, 0.0, 0.0, 0.0, 0.0, 0.0, 3.25] (5.0 h), Ausfälle 13 %, fährt 87 % der Dauer, pendelt 90 min [#20778066974]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20778063366: AMBITIOUS, ULTRA FINISH 1104 km/11040 hm in 5 Wochen, Zeit Mo–So [0.5, 0.0, 1.75, 0.0, 1.0, 4.25, 4.75] (12.3 h), Ausfälle 24 %, unter der Woche +33 %, fährt 81 % der Dauer, Rennen Woche 2 [#20778063366]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20778067290: AMBITIOUS, TIME_TRIAL FINISH 20 km/380 hm in 5 Wochen, Zeit Mo–So [0.0, 3.0, 2.75, 1.25, 0.0, 1.5, 0.0] (8.5 h), Ausfälle 6 %, fährt 114 % der Dauer, Urlaub Woche 2 (1 Wo.), Schichtdienst [#20778067290]
- **Fitness steigt um # CTL** – Startwert 20778076084: PERFORMANCE, BIKEPACKING FINISH 1614 km/25824 hm in 4 Wochen, Zeit Mo–So [0.75, 0.0, 1.75, 2.75, 0.75, 2.75, 6.75] (15.5 h), Ausfälle 13 %, fährt 116 % der Dauer, pendelt 80 min [#20778076084]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20778062506: PERFORMANCE, TIME_TRIAL COMPETE 27 km/594 hm in 2 Wochen, Zeit Mo–So [2.0, 2.75, 1.75, 2.25, 3.25, 6.0, 7.25] (25.3 h), Ausfälle 24 %, fährt 110 % der Dauer [#20778062506]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20778063343: PERFORMANCE, CRITERIUM GOOD_TIME 65 km/910 hm in 2 Wochen, Zeit Mo–So [0.0, 3.0, 3.5, 2.0, 1.5, 0.0, 3.25] (13.3 h), Ausfälle 22 %, fährt 85 % der Dauer [#20778063343]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20778103442: BEGINNER, GRAVEL COMPETE 116 km/1044 hm in 5 Wochen, Zeit Mo–So [1.0, 0.75, 1.0, 1.0, 0.75, 1.5, 1.5] (7.5 h), Ausfälle 23 %, fährt 125 % der Dauer, krank Woche 4 (7 Tage), Schichtdienst [#20778103442]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20778064658: PERFORMANCE, ULTRA FINISH 1293 km/23274 hm in 4 Wochen, Zeit Mo–So [4.0, 0.0, 0.0, 2.25, 0.0, 4.25, 3.75] (14.3 h), Ausfälle 20 %, fährt 87 % der Dauer, Rennen Woche 3, krank Woche 1 (11 Tage) [#20778064658]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20778072910: HOBBY, ROAD_RACE FINISH 204 km/2652 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 1.5, 2.0, 1.25, 4.0, 3.5] (14.5 h), Ausfälle 7 %, fährt 129 % der Dauer, pendelt 80 min [#20778072910]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20778078672: HOBBY, CRITERIUM COMPETE 65 km/195 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 3.0, 1.5] (4.5 h), Ausfälle 16 %, fährt 75 % der Dauer, pendelt 40 min, Rennen Woche 4, krank Woche 2 (7 Tage) [#20778078672]
