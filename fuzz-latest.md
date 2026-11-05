# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

70000 zufällige Fahrer in 1505 s (Startwert-Basis 20762 = 2026-11-05). 10 (0.0 %) mit harten Befunden, 58789 (84.0 %) mit Warnungen, 16 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 9 | 0.0 % | 11 | Startwert 20762121244: Woche ab 2027-01-04: Frische bis -61 TSB gefallen (Grenze -60) |
| HART: # Belastungswochen am Stück | 1 | 0.0 % | 1 | Startwert 20762091174: Woche ab 2026-12-14: 7 Belastungswochen am Stück |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 36339 | 51.9 % | 189002 | Startwert 20762069593: Woche ab 2026-11-16: geplant 78 TSS, 1.5-mal so viel wie zuletzt gefahren (51/Woche) |
| Frische bis # TSB gefallen | 30192 | 43.1 % | 85633 | Startwert 20762062302: Woche ab 2026-11-09: Frische bis -35 TSB gefallen (Warnung ab -35, selbst verursacht) |
| # × … – eintönig | 19528 | 27.9 % | 92492 | Startwert 20762063640: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 14265 | 20.4 % | 15542 | Startwert 20762062404: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3206 | 4.6 % | 20076 | Startwert 20762071287: 2026-11-09: harte Einheit (VO2max 3 × 13 × 30/15) am Tag nach 180 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 2867 | 4.1 % | 2867 | Startwert 20762062882: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2203 | 3.1 % | 4271 | Startwert 20762080629: Woche ab 2026-11-16: Fitness steigt um 8.1 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2189 | 3.1 % | 2189 | Startwert 20762069148: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2009 | 2.9 % | 2009 | Startwert 20762062997: Event-Tag: Frische -10 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1990 | 2.8 % | 1990 | Startwert 20762062302: Event-Tag: Frische -25 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1536 | 2.2 % | 1536 | Startwert 20762063802: Woche ab 2026-11-30: nach 7 Tagen Krankheit 143 min geplant, mehr als davor (99) |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 523 | 0.7 % | 523 | Startwert 20762064577: Event-Tag: Frische -19 TSB passt nicht zum Ziel FINISH |
| Erholungswoche direkt vor dem Tapering | 464 | 0.7 % | 464 | Startwert 20762064489: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 100 | 0.1 % | 100 | Startwert 20762099517: Woche ab 2026-12-14: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20762121244: PERFORMANCE, GRAVEL GOOD_TIME 182 km/2184 hm in 10 Wochen, Zeit Mo–So [3.0, 2.25, 2.0, 1.5, 3.5, 4.75, 4.0] (21.0 h), Ausfälle 13 %, fährt 103 % der Dauer, pendelt 40 min, Urlaub Woche 8 (1 Wo.), krank Woche 1 (12 Tage) [#20762121244]
- **HART: # Belastungswochen am Stück** – Startwert 20762091174: PERFORMANCE, CRITERIUM GOOD_TIME 48 km/912 hm in 8 Wochen, Zeit Mo–So [2.75, 0.75, 0.0, 1.0, 2.0, 3.5, 1.5] (11.5 h), Ausfälle 3 %, fährt 92 % der Dauer, Rennen Woche 6, krank Woche 2 (10 Tage), Schichtdienst [#20762091174]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20762069593: BEGINNER, GRAVEL COMPETE 143 km/3146 hm in 2 Wochen, Zeit Mo–So [0.0, 2.25, 1.0, 0.0, 0.0, 0.0, 0.0] (3.3 h), Ausfälle 23 %, fährt 76 % der Dauer [#20762069593]
- **Frische bis # TSB gefallen** – Startwert 20762062302: BEGINNER, ULTRA GOOD_TIME 601 km/3005 hm in 2 Wochen, Zeit Mo–So [0.75, 1.25, 0.0, 0.25, 0.0, 1.5, 2.25] (6.0 h), Ausfälle 16 %, fährt 112 % der Dauer, pendelt 90 min [#20762062302]
- **# × … – eintönig** – Startwert 20762063640: BEGINNER, GRAVEL FINISH 183 km/4392 hm in 2 Wochen, Zeit Mo–So [1.5, 1.5, 0.75, 0.75, 0.75, 3.0, 2.0] (10.3 h), Ausfälle 18 %, fährt 105 % der Dauer, Schichtdienst [#20762063640]
- **# Belastungswochen am Stück** – Startwert 20762062404: PERFORMANCE, TIME_TRIAL FINISH 43 km/301 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 5.25, 0.0, 3.5] (8.8 h), Ausfälle 11 %, fährt 116 % der Dauer, Rennen Woche 6 [#20762062404]
- **harte Einheit am Tag nach # min geplant** – Startwert 20762071287: HOBBY, GRAVEL COMPETE 53 km/1219 hm in 2 Wochen, Zeit Mo–So [1.75, 1.75, 1.5, 1.0, 2.0, 1.75, 4.0] (13.8 h), Ausfälle 15 %, fährt 97 % der Dauer, Schichtdienst [#20762071287]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20762062882: HOBBY, BIKEPACKING FINISH 995 km/14925 hm in 5 Wochen, Zeit Mo–So [1.0, 1.0, 1.0, 0.0, 1.25, 3.25, 3.25] (10.8 h), Ausfälle 21 %, fährt 122 % der Dauer, Urlaub Woche 2 (3 Wo.), Rennen Woche 2, krank Woche 3 (8 Tage) [#20762062882]
- **Fitness steigt um # CTL** – Startwert 20762080629: HOBBY, GRAN_FONDO FINISH 120 km/240 hm in 4 Wochen, Zeit Mo–So [1.75, 1.75, 1.25, 1.25, 1.5, 4.0, 4.5] (16.0 h), Ausfälle 3 %, fährt 117 % der Dauer, Reisewoche 2 [#20762080629]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20762069148: BEGINNER, TIME_TRIAL COMPETE 39 km/429 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.75, 0.0, 0.0, 1.5, 0.0] (2.3 h), Ausfälle 22 %, fährt 115 % der Dauer, pendelt 20 min, Urlaub Woche 2 (1 Wo.), Rennen Woche 5, krank Woche 4 (18 Tage) [#20762069148]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20762062997: HOBBY, GRAVEL COMPETE 59 km/1062 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 2.5, 2.5, 0.0, 0.0] (5.0 h), Ausfälle 13 %, fährt 97 % der Dauer, pendelt 50 min [#20762062997]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20762062302: BEGINNER, ULTRA GOOD_TIME 601 km/3005 hm in 2 Wochen, Zeit Mo–So [0.75, 1.25, 0.0, 0.25, 0.0, 1.5, 2.25] (6.0 h), Ausfälle 16 %, fährt 112 % der Dauer, pendelt 90 min [#20762062302]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20762063802: BEGINNER, GRAVEL GOOD_TIME 246 km/2460 hm in 5 Wochen, Zeit Mo–So [0.25, 1.5, 0.0, 0.0, 1.5, 0.0, 0.25] (3.5 h), Ausfälle 7 %, fährt 127 % der Dauer, krank Woche 4 (7 Tage), Reisewoche 2 [#20762063802]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20762064577: BEGINNER, ROAD_RACE FINISH 202 km/4040 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 1.0, 1.25, 1.0, 2.25, 1.75] (9.5 h), Ausfälle 4 %, unter der Woche +53 %, fährt 124 % der Dauer, pendelt 80 min [#20762064577]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20762064489: BEGINNER, ROAD_RACE FINISH 122 km/2806 hm in 4 Wochen, Zeit Mo–So [0.75, 1.0, 1.5, 1.25, 1.0, 2.5, 3.0] (11.0 h), Ausfälle 2 %, fährt 120 % der Dauer, krank Woche 1 (13 Tage) [#20762064489]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20762099517: BEGINNER, GRAN_FONDO COMPETE 159 km/477 hm in 11 Wochen, Zeit Mo–So [1.25, 1.25, 1.5, 1.0, 0.75, 1.5, 2.75] (10.0 h), Ausfälle 24 %, fährt 96 % der Dauer, Rennen Woche 5, krank Woche 3 (9 Tage) [#20762099517]
