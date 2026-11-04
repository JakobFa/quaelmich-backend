# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

76800 zufällige Fahrer in 1503 s (Startwert-Basis 20761 = 2026-11-04). 4 (0.0 %) mit harten Befunden, 64580 (84.1 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 4 | 0.0 % | 5 | Startwert 20761115496: Woche ab 2027-02-22: Frische bis -63 TSB gefallen (Grenze -60) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 40213 | 52.4 % | 209289 | Startwert 20761089877: Woche ab 2026-11-16: geplant 120 TSS, 1.7-mal so viel wie zuletzt gefahren (69/Woche) |
| Frische bis # TSB gefallen | 33262 | 43.3 % | 94450 | Startwert 20761062750: Woche ab 2026-11-09: Frische bis -38 TSB gefallen (Warnung ab -35, selbst verursacht) |
| # × … – eintönig | 21337 | 27.8 % | 100971 | Startwert 20761063302: Woche ab 2026-11-09: 5 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 15661 | 20.4 % | 17140 | Startwert 20761063004: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3609 | 4.7 % | 22355 | Startwert 20761066579: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3159 | 4.1 % | 3159 | Startwert 20761064104: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2446 | 3.2 % | 4632 | Startwert 20761084865: Woche ab 2026-11-16: Fitness steigt um 8.4 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2392 | 3.1 % | 2392 | Startwert 20761071486: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2213 | 2.9 % | 2213 | Startwert 20761063307: Event-Tag: Frische -16 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2161 | 2.8 % | 2161 | Startwert 20761062564: Event-Tag: Frische -22 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1755 | 2.3 % | 1755 | Startwert 20761088529: Woche ab 2026-11-30: nach 7 Tagen Krankheit 647 min geplant, mehr als davor (507) |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 529 | 0.7 % | 529 | Startwert 20761062524: Event-Tag: Frische -21 TSB passt nicht zum Ziel FINISH |
| Erholungswoche direkt vor dem Tapering | 491 | 0.6 % | 491 | Startwert 20761064450: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 127 | 0.2 % | 128 | Startwert 20761066502: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20761115496: PERFORMANCE, BIKEPACKING GOOD_TIME 1599 km/31980 hm in 20 Wochen, Zeit Mo–So [1.5, 3.0, 3.25, 1.75, 3.25, 6.75, 6.25] (25.8 h), Ausfälle 23 %, fährt 99 % der Dauer, pendelt 50 min, krank Woche 1 (17 Tage) [#20761115496]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20761089877: PERFORMANCE, TIME_TRIAL FINISH 73 km/876 hm in 2 Wochen, Zeit Mo–So [0.75, 1.75, 3.5, 0.0, 0.0, 0.0, 2.25] (8.3 h), Ausfälle 17 %, unter der Woche +54 %, fährt 93 % der Dauer [#20761089877]
- **Frische bis # TSB gefallen** – Startwert 20761062750: BEGINNER, ROAD_RACE FINISH 84 km/1428 hm in 2 Wochen, Zeit Mo–So [0.5, 1.0, 1.0, 1.25, 1.0, 2.0, 3.0] (9.8 h), Ausfälle 5 %, fährt 118 % der Dauer, pendelt 90 min [#20761062750]
- **# × … – eintönig** – Startwert 20761063302: BEGINNER, ROAD_RACE FINISH 189 km/1134 hm in 2 Wochen, Zeit Mo–So [0.75, 1.0, 1.0, 0.75, 0.75, 2.75, 1.75] (8.8 h), Ausfälle 5 %, fährt 80 % der Dauer, Schichtdienst [#20761063302]
- **# Belastungswochen am Stück** – Startwert 20761063004: BEGINNER, GRAN_FONDO FINISH 139 km/3475 hm in 6 Wochen, Zeit Mo–So [0.75, 1.0, 1.0, 1.25, 1.25, 2.0, 2.0] (9.3 h), Ausfälle 24 %, fährt 106 % der Dauer, Rennen Woche 4 [#20761063004]
- **harte Einheit am Tag nach # min geplant** – Startwert 20761066579: AMBITIOUS, GRAN_FONDO GOOD_TIME 142 km/1278 hm in 2 Wochen, Zeit Mo–So [2.0, 1.0, 1.75, 1.0, 1.25, 2.25, 2.75] (12.0 h), Ausfälle 2 %, fährt 91 % der Dauer, Schichtdienst [#20761066579]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20761064104: HOBBY, BIKEPACKING GOOD_TIME 1644 km/3288 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 1.25, 1.0, 0.0, 2.0, 0.75] (5.0 h), Ausfälle 12 %, fährt 80 % der Dauer, pendelt 90 min, Rennen Woche 2, krank Woche 3 (10 Tage) [#20761064104]
- **Fitness steigt um # CTL** – Startwert 20761084865: AMBITIOUS, GRAN_FONDO FINISH 239 km/3824 hm in 3 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.5, 4.75] (9.3 h), Ausfälle 7 %, fährt 124 % der Dauer, pendelt 80 min, Reisewoche 2 [#20761084865]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20761071486: HOBBY, GRAVEL COMPETE 200 km/2600 hm in 5 Wochen, Zeit Mo–So [0.75, 1.5, 0.0, 0.5, 0.0, 2.0, 2.0] (6.8 h), Ausfälle 24 %, fährt 105 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 2 [#20761071486]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20761063307: BEGINNER, ULTRA COMPETE 838 km/15922 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.25, 0.75, 1.0, 1.5, 1.5] (5.0 h), Ausfälle 2 %, fährt 80 % der Dauer, pendelt 60 min [#20761063307]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20761062564: BEGINNER, ROAD_RACE GOOD_TIME 72 km/720 hm in 2 Wochen, Zeit Mo–So [1.75, 2.25, 0.75, 0.0, 0.0, 0.0, 1.75] (6.5 h), Ausfälle 6 %, fährt 77 % der Dauer, pendelt 90 min, Schichtdienst [#20761062564]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20761088529: PERFORMANCE, ULTRA COMPETE 1038 km/12456 hm in 5 Wochen, Zeit Mo–So [3.0, 2.0, 3.0, 3.0, 1.5, 3.75, 6.5] (22.8 h), Ausfälle 12 %, fährt 91 % der Dauer, pendelt 60 min, Rennen Woche 2, krank Woche 4 (7 Tage), Reisewoche 3, Schichtdienst [#20761088529]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20761062524: BEGINNER, GRAVEL FINISH 241 km/5061 hm in 2 Wochen, Zeit Mo–So [1.0, 0.5, 1.0, 1.0, 0.5, 3.0, 2.5] (9.5 h), Ausfälle 3 %, fährt 97 % der Dauer, pendelt 80 min, Schichtdienst [#20761062524]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20761064450: HOBBY, TIME_TRIAL GOOD_TIME 37 km/407 hm in 4 Wochen, Zeit Mo–So [0.5, 0.0, 0.0, 1.25, 0.0, 0.0, 1.0] (2.8 h), Ausfälle 24 %, fährt 87 % der Dauer, krank Woche 1 (11 Tage) [#20761064450]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20761066502: AMBITIOUS, CRITERIUM COMPETE 61 km/976 hm in 10 Wochen, Zeit Mo–So [1.0, 1.5, 1.25, 1.5, 2.5, 2.0, 2.75] (12.5 h), Ausfälle 12 %, fährt 100 % der Dauer, Urlaub Woche 7 (2 Wo.), krank Woche 9 (17 Tage), Reisewoche 1 [#20761066502]
