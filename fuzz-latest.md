# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1305 s (Startwert-Basis 20784 = 2026-11-27). 7 (0.0 %) mit harten Befunden, 75894 (75.9 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 7 | 0.0 % | 7 | Startwert 20784127215: Woche ab 2026-12-14: Frische bis -62 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42599 | 42.6 % | 119423 | Startwert 20784062775: Woche ab 2026-11-02: Frische bis -57 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27370 | 27.4 % | 104676 | Startwert 20784141685: Woche ab 2026-11-16: geplant 120 TSS, 2.3-mal so viel wie zuletzt gefahren (53/Woche, beste Woche 84) |
| # × … – eintönig | 24831 | 24.8 % | 104057 | Startwert 20784062366: Woche ab 2026-11-09: 5 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20488 | 20.5 % | 22334 | Startwert 20784063570: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4383 | 4.4 % | 26302 | Startwert 20784065720: 2026-11-16: harte Einheit (Schwelle 2 × 15) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4002 | 4.0 % | 4002 | Startwert 20784063889: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3037 | 3.0 % | 3037 | Startwert 20784065529: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2824 | 2.8 % | 5389 | Startwert 20784063612: Woche ab 2026-11-16: Fitness steigt um 10.1 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2654 | 2.7 % | 2654 | Startwert 20784062775: Event-Tag: Frische 37 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2629 | 2.6 % | 2629 | Startwert 20784063284: Event-Tag: Frische 36 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2334 | 2.3 % | 2334 | Startwert 20784090521: Woche ab 2026-11-30: nach 7 Tagen Krankheit 225 min geplant, mehr als davor (217) |
| Erholungswoche direkt vor dem Tapering | 676 | 0.7 % | 676 | Startwert 20784063121: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 509 | 0.5 % | 509 | Startwert 20784067980: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 108 | 0.1 % | 109 | Startwert 20784095683: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20784127215: PERFORMANCE, GRAVEL COMPETE 223 km/5129 hm in 9 Wochen, Zeit Mo–So [0.0, 4.25, 1.25, 4.5, 0.0, 4.5, 0.0] (14.5 h), Ausfälle 2 %, fährt 98 % der Dauer, pendelt 40 min [#20784127215]
- **Frische bis # TSB gefallen** – Startwert 20784062775: PERFORMANCE, GRAN_FONDO COMPETE 197 km/1182 hm in 2 Wochen, Zeit Mo–So [3.0, 1.75, 3.5, 1.75, 2.75, 7.75, 6.75] (27.3 h), Ausfälle 16 %, fährt 77 % der Dauer, pendelt 80 min [#20784062775]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20784141685: BEGINNER, GRAVEL COMPETE 127 km/1524 hm in 2 Wochen, Zeit Mo–So [1.75, 0.0, 2.25, 0.0, 1.75, 0.0, 0.0] (5.8 h), Ausfälle 9 %, unter der Woche +41 %, fährt 99 % der Dauer [#20784141685]
- **# × … – eintönig** – Startwert 20784062366: PERFORMANCE, CRITERIUM COMPETE 41 km/164 hm in 2 Wochen, Zeit Mo–So [2.75, 1.5, 1.5, 3.0, 2.5, 5.0, 4.25] (20.5 h), Ausfälle 2 %, fährt 108 % der Dauer [#20784062366]
- **# Belastungswochen am Stück** – Startwert 20784063570: AMBITIOUS, GRAVEL FINISH 162 km/3402 hm in 6 Wochen, Zeit Mo–So [1.25, 0.5, 0.0, 0.0, 0.5, 2.25, 2.5] (7.0 h), Ausfälle 21 %, fährt 107 % der Dauer [#20784063570]
- **harte Einheit am Tag nach # min geplant** – Startwert 20784065720: PERFORMANCE, ULTRA FINISH 794 km/1588 hm in 2 Wochen, Zeit Mo–So [3.0, 3.5, 3.5, 2.0, 1.5, 5.25, 7.25] (26.0 h), Ausfälle 13 %, fährt 77 % der Dauer [#20784065720]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20784063889: AMBITIOUS, CRITERIUM COMPETE 50 km/1150 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 5.25, 4.5] (9.8 h), Ausfälle 24 %, fährt 116 % der Dauer, pendelt 90 min, Rennen Woche 2, krank Woche 4 (14 Tage), Schichtdienst [#20784063889]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20784065529: PERFORMANCE, TIME_TRIAL GOOD_TIME 37 km/481 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 5.0, 2.5] (7.5 h), Ausfälle 17 %, fährt 83 % der Dauer, pendelt 80 min, Urlaub Woche 2 (1 Wo.), krank Woche 1 (5 Tage) [#20784065529]
- **Fitness steigt um # CTL** – Startwert 20784063612: AMBITIOUS, GRAVEL COMPETE 122 km/1952 hm in 4 Wochen, Zeit Mo–So [1.25, 2.0, 1.5, 1.0, 1.0, 4.5, 5.5] (16.8 h), Ausfälle 8 %, fährt 118 % der Dauer, pendelt 50 min [#20784063612]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20784062775: PERFORMANCE, GRAN_FONDO COMPETE 197 km/1182 hm in 2 Wochen, Zeit Mo–So [3.0, 1.75, 3.5, 1.75, 2.75, 7.75, 6.75] (27.3 h), Ausfälle 16 %, fährt 77 % der Dauer, pendelt 80 min [#20784062775]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20784063284: PERFORMANCE, CRITERIUM GOOD_TIME 38 km/950 hm in 2 Wochen, Zeit Mo–So [3.0, 2.75, 2.0, 1.5, 3.25, 6.75, 6.25] (25.5 h), Ausfälle 15 %, fährt 119 % der Dauer, Schichtdienst [#20784063284]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20784090521: HOBBY, BIKEPACKING FINISH 277 km/6371 hm in 6 Wochen, Zeit Mo–So [0.5, 0.5, 1.75, 0.0, 0.0, 1.0, 0.0] (3.8 h), Ausfälle 13 %, fährt 81 % der Dauer, pendelt 90 min, krank Woche 4 (7 Tage) [#20784090521]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20784063121: AMBITIOUS, GRAN_FONDO COMPETE 205 km/1435 hm in 4 Wochen, Zeit Mo–So [1.5, 0.0, 1.0, 1.25, 0.75, 3.75, 1.0] (9.3 h), Ausfälle 5 %, fährt 76 % der Dauer, Urlaub Woche 2 (3 Wo.), Rennen Woche 2, krank Woche 1 (12 Tage), Schichtdienst [#20784063121]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20784067980: BEGINNER, ROAD_RACE FINISH 191 km/2101 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 1.5, 2.25, 0.0, 0.0] (3.8 h), Ausfälle 4 %, fährt 97 % der Dauer, pendelt 90 min, Schichtdienst [#20784067980]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20784095683: AMBITIOUS, ULTRA GOOD_TIME 770 km/18480 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.5, 4.75] (9.3 h), Ausfälle 6 %, fährt 111 % der Dauer, pendelt 30 min, Rennen Woche 2, Reisewoche 1 [#20784095683]
