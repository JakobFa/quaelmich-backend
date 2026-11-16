# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

74400 zufällige Fahrer in 1501 s (Startwert-Basis 20773 = 2026-11-16). 7 (0.0 %) mit harten Befunden, 56380 (75.8 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 7 | 0.0 % | 9 | Startwert 20773080501: Woche ab 2027-02-15: Frische bis -70 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 31933 | 42.9 % | 90473 | Startwert 20773064259: Woche ab 2026-11-02: Frische bis -37 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 20395 | 27.4 % | 78415 | Startwert 20773063131: Woche ab 2026-11-16: geplant 231 TSS, 4.1-mal so viel wie zuletzt gefahren (56/Woche, beste Woche 196) |
| # × … – eintönig | 18120 | 24.4 % | 75664 | Startwert 20773062836: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 15493 | 20.8 % | 16801 | Startwert 20773062683: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3407 | 4.6 % | 21184 | Startwert 20773068435: 2026-11-09: harte Einheit (Schwelle 3 × 10) am Tag nach 360 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3058 | 4.1 % | 3058 | Startwert 20773065027: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2361 | 3.2 % | 4447 | Startwert 20773062658: Woche ab 2026-11-16: Fitness steigt um 8.5 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2260 | 3.0 % | 2260 | Startwert 20773062406: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2061 | 2.8 % | 2061 | Startwert 20773062352: Event-Tag: Frische -7 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1943 | 2.6 % | 1943 | Startwert 20773064101: Event-Tag: Frische -13 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1726 | 2.3 % | 1726 | Startwert 20773102089: Woche ab 2026-11-30: nach 7 Tagen Krankheit 299 min geplant, mehr als davor (248) |
| Erholungswoche direkt vor dem Tapering | 470 | 0.6 % | 470 | Startwert 20773068437: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 373 | 0.5 % | 373 | Startwert 20773062603: Event-Tag: Frische -11 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 77 | 0.1 % | 77 | Startwert 20773071937: Woche ab 2026-12-14: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20773080501: PERFORMANCE, GRAVEL COMPETE 208 km/4160 hm in 21 Wochen, Zeit Mo–So [2.5, 2.25, 1.5, 2.75, 2.75, 4.0, 5.25] (21.0 h), Ausfälle 21 %, fährt 100 % der Dauer, Urlaub Woche 12 (1 Wo.), krank Woche 1 (17 Tage), Reisewoche 5 [#20773080501]
- **Frische bis # TSB gefallen** – Startwert 20773064259: AMBITIOUS, GRAN_FONDO FINISH 104 km/2704 hm in 2 Wochen, Zeit Mo–So [1.25, 1.75, 2.5, 2.0, 1.75, 5.5, 4.0] (18.8 h), Ausfälle 13 %, unter der Woche +58 %, fährt 111 % der Dauer [#20773064259]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20773063131: AMBITIOUS, GRAVEL GOOD_TIME 216 km/4320 hm in 3 Wochen, Zeit Mo–So [0.0, 2.75, 1.0, 0.0, 0.0, 0.0, 3.5] (7.3 h), Ausfälle 19 %, unter der Woche +46 %, fährt 96 % der Dauer [#20773063131]
- **# × … – eintönig** – Startwert 20773062836: AMBITIOUS, CRITERIUM FINISH 53 km/477 hm in 2 Wochen, Zeit Mo–So [3.0, 2.25, 1.25, 0.0, 3.0, 2.25, 2.5] (14.3 h), Ausfälle 13 %, unter der Woche +36 %, fährt 97 % der Dauer, pendelt 70 min [#20773062836]
- **# Belastungswochen am Stück** – Startwert 20773062683: AMBITIOUS, GRAVEL FINISH 248 km/3968 hm in 6 Wochen, Zeit Mo–So [1.75, 1.75, 1.0, 1.5, 1.75, 4.5, 5.25] (17.5 h), Ausfälle 1 %, fährt 82 % der Dauer, Rennen Woche 6 [#20773062683]
- **harte Einheit am Tag nach # min geplant** – Startwert 20773068435: PERFORMANCE, GRAVEL GOOD_TIME 237 km/1185 hm in 2 Wochen, Zeit Mo–So [2.75, 0.0, 0.0, 0.0, 0.75, 5.5, 6.0] (15.0 h), Ausfälle 7 %, fährt 111 % der Dauer [#20773068435]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20773065027: PERFORMANCE, ROAD_RACE FINISH 81 km/1458 hm in 5 Wochen, Zeit Mo–So [0.0, 2.75, 0.0, 1.5, 0.0, 3.0, 6.25] (13.5 h), Ausfälle 19 %, fährt 82 % der Dauer, pendelt 70 min, Rennen Woche 2 [#20773065027]
- **Fitness steigt um # CTL** – Startwert 20773062658: AMBITIOUS, GRAN_FONDO GOOD_TIME 218 km/1744 hm in 4 Wochen, Zeit Mo–So [2.0, 1.75, 1.75, 2.5, 1.25, 4.75, 4.0] (18.0 h), Ausfälle 14 %, fährt 84 % der Dauer, pendelt 80 min, Reisewoche 1 [#20773062658]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20773062406: HOBBY, TIME_TRIAL COMPETE 52 km/1196 hm in 5 Wochen, Zeit Mo–So [1.5, 1.5, 1.0, 1.5, 1.0, 2.5, 3.5] (12.5 h), Ausfälle 10 %, unter der Woche +58 %, fährt 83 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 2 (8 Tage) [#20773062406]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20773062352: HOBBY, ROAD_RACE COMPETE 87 km/435 hm in 2 Wochen, Zeit Mo–So [0.0, 0.5, 0.75, 1.25, 0.0, 3.75, 2.0] (8.3 h), Ausfälle 9 %, fährt 101 % der Dauer, pendelt 90 min [#20773062352]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20773064101: AMBITIOUS, ROAD_RACE GOOD_TIME 151 km/3171 hm in 2 Wochen, Zeit Mo–So [1.5, 1.0, 0.0, 2.0, 1.5, 4.5, 2.25] (12.8 h), Ausfälle 17 %, fährt 130 % der Dauer, pendelt 90 min [#20773064101]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20773102089: AMBITIOUS, GRAN_FONDO FINISH 93 km/465 hm in 5 Wochen, Zeit Mo–So [1.25, 1.75, 2.75, 0.0, 2.5, 0.0, 1.0] (9.3 h), Ausfälle 13 %, unter der Woche +34 %, fährt 113 % der Dauer, pendelt 20 min, Rennen Woche 5, krank Woche 4 (7 Tage) [#20773102089]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20773068437: PERFORMANCE, BIKEPACKING FINISH 461 km/922 hm in 4 Wochen, Zeit Mo–So [2.0, 2.5, 2.0, 1.25, 1.5, 0.0, 3.25] (12.5 h), Ausfälle 20 %, fährt 122 % der Dauer, pendelt 80 min, Urlaub Woche 2 (2 Wo.), Rennen Woche 4, krank Woche 1 (13 Tage) [#20773068437]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20773062603: HOBBY, GRAN_FONDO FINISH 105 km/2310 hm in 2 Wochen, Zeit Mo–So [0.5, 1.25, 0.5, 0.0, 0.0, 3.75, 3.75] (9.8 h), Ausfälle 22 %, fährt 105 % der Dauer, pendelt 90 min [#20773062603]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20773071937: PERFORMANCE, CRITERIUM COMPETE 67 km/1675 hm in 12 Wochen, Zeit Mo–So [3.25, 0.0, 4.0, 0.0, 5.0, 2.75, 0.0] (15.0 h), Ausfälle 5 %, fährt 104 % der Dauer, pendelt 60 min, Rennen Woche 5, Reisewoche 3 [#20773071937]
