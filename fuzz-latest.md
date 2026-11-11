# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1178 s (Startwert-Basis 20768 = 2026-11-11). 5 (0.0 %) mit harten Befunden, 75993 (76.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 5 | 0.0 % | 5 | Startwert 20768143473: Woche ab 2026-12-28: Frische bis -63 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42731 | 42.7 % | 120161 | Startwert 20768063765: Woche ab 2026-11-02: Frische bis -47 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27186 | 27.2 % | 103701 | Startwert 20768069198: Woche ab 2026-11-16: geplant 86 TSS, 1.5-mal so viel wie zuletzt gefahren (56/Woche, beste Woche 77) |
| # × … – eintönig | 25023 | 25.0 % | 105792 | Startwert 20768063787: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20909 | 20.9 % | 22696 | Startwert 20768062994: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4471 | 4.5 % | 27275 | Startwert 20768064942: 2026-11-06: harte Einheit (Sweet Spot 3 × 15) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4023 | 4.0 % | 4023 | Startwert 20768062839: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2997 | 3.0 % | 2997 | Startwert 20768067488: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2993 | 3.0 % | 5827 | Startwert 20768066005: Woche ab 2026-11-16: Fitness steigt um 9.1 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2788 | 2.8 % | 2788 | Startwert 20768062899: Event-Tag: Frische -10 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2631 | 2.6 % | 2631 | Startwert 20768064396: Event-Tag: Frische 31 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2172 | 2.2 % | 2172 | Startwert 20768110607: Woche ab 2026-11-30: nach 7 Tagen Krankheit 255 min geplant, mehr als davor (203) |
| Erholungswoche direkt vor dem Tapering | 600 | 0.6 % | 600 | Startwert 20768062856: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 505 | 0.5 % | 505 | Startwert 20768062828: Event-Tag: Frische -14 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 102 | 0.1 % | 102 | Startwert 20768106908: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20768143473: AMBITIOUS, GRAVEL GOOD_TIME 154 km/3850 hm in 9 Wochen, Zeit Mo–So [1.75, 1.25, 2.0, 2.25, 1.5, 3.25, 5.25] (17.3 h), Ausfälle 2 %, fährt 105 % der Dauer, pendelt 40 min, Reisewoche 1 [#20768143473]
- **Frische bis # TSB gefallen** – Startwert 20768063765: PERFORMANCE, ULTRA GOOD_TIME 1149 km/21831 hm in 2 Wochen, Zeit Mo–So [1.5, 1.75, 2.75, 3.5, 2.25, 7.25, 4.25] (23.3 h), Ausfälle 2 %, unter der Woche +37 %, fährt 85 % der Dauer, pendelt 90 min [#20768063765]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20768069198: BEGINNER, GRAN_FONDO GOOD_TIME 94 km/846 hm in 2 Wochen, Zeit Mo–So [1.75, 1.25, 0.0, 0.0, 0.0, 0.0, 0.0] (3.0 h), Ausfälle 6 %, unter der Woche +23 %, fährt 84 % der Dauer [#20768069198]
- **# × … – eintönig** – Startwert 20768063787: PERFORMANCE, GRAVEL FINISH 94 km/1504 hm in 2 Wochen, Zeit Mo–So [3.25, 2.5, 0.0, 0.75, 2.75, 3.25, 3.75] (16.3 h), Ausfälle 24 %, fährt 122 % der Dauer [#20768063787]
- **# Belastungswochen am Stück** – Startwert 20768062994: PERFORMANCE, GRAN_FONDO FINISH 230 km/4600 hm in 6 Wochen, Zeit Mo–So [1.5, 2.25, 1.75, 2.5, 3.0, 7.5, 7.0] (25.5 h), Ausfälle 17 %, fährt 127 % der Dauer, Urlaub Woche 4 (2 Wo.) [#20768062994]
- **harte Einheit am Tag nach # min geplant** – Startwert 20768064942: PERFORMANCE, GRAVEL GOOD_TIME 204 km/1836 hm in 2 Wochen, Zeit Mo–So [2.75, 2.25, 4.25, 3.75, 2.5, 0.0, 0.0] (15.5 h), Ausfälle 1 %, fährt 121 % der Dauer, pendelt 60 min [#20768064942]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20768062839: BEGINNER, CRITERIUM COMPETE 60 km/1500 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.75, 1.25, 0.5, 2.75, 2.75] (8.0 h), Ausfälle 5 %, fährt 97 % der Dauer, Rennen Woche 2 [#20768062839]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20768067488: PERFORMANCE, TIME_TRIAL FINISH 18 km/396 hm in 5 Wochen, Zeit Mo–So [3.25, 4.0, 2.0, 0.0, 1.75, 1.75, 0.0] (12.8 h), Ausfälle 15 %, unter der Woche +30 %, fährt 125 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 3 [#20768067488]
- **Fitness steigt um # CTL** – Startwert 20768066005: BEGINNER, ULTRA COMPETE 540 km/13500 hm in 4 Wochen, Zeit Mo–So [1.5, 1.25, 1.0, 0.75, 0.75, 3.5, 1.75] (10.5 h), Ausfälle 18 %, unter der Woche +44 %, fährt 116 % der Dauer, pendelt 70 min, Reisewoche 2, Schichtdienst [#20768066005]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20768062899: BEGINNER, ROAD_RACE COMPETE 102 km/2550 hm in 2 Wochen, Zeit Mo–So [1.0, 0.0, 0.75, 1.5, 1.75, 0.75, 0.0] (5.8 h), Ausfälle 17 %, unter der Woche +25 %, fährt 115 % der Dauer, pendelt 90 min [#20768062899]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20768064396: PERFORMANCE, CRITERIUM GOOD_TIME 46 km/1012 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 2.0, 0.0, 0.0, 6.75, 5.75] (14.5 h), Ausfälle 6 %, fährt 125 % der Dauer, Schichtdienst [#20768064396]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20768110607: AMBITIOUS, GRAVEL COMPETE 222 km/2442 hm in 5 Wochen, Zeit Mo–So [2.75, 0.0, 2.75, 0.0, 3.25, 0.0, 0.0] (8.8 h), Ausfälle 24 %, fährt 76 % der Dauer, krank Woche 4 (7 Tage) [#20768110607]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20768062856: BEGINNER, BIKEPACKING COMPETE 532 km/4256 hm in 4 Wochen, Zeit Mo–So [1.0, 1.25, 0.0, 0.0, 1.0, 0.5, 0.5] (4.3 h), Ausfälle 4 %, fährt 122 % der Dauer, krank Woche 1 (11 Tage) [#20768062856]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20768062828: BEGINNER, GRAN_FONDO FINISH 172 km/4300 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 1.5, 1.0, 1.5, 1.75, 1.25] (7.0 h), Ausfälle 3 %, fährt 114 % der Dauer, pendelt 60 min [#20768062828]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20768106908: HOBBY, ROAD_RACE GOOD_TIME 181 km/1991 hm in 9 Wochen, Zeit Mo–So [1.75, 1.0, 1.0, 1.5, 1.25, 4.0, 3.0] (13.5 h), Ausfälle 23 %, unter der Woche +46 %, fährt 102 % der Dauer, Rennen Woche 3, krank Woche 1 (9 Tage), Reisewoche 7, Schichtdienst [#20768106908]
