# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 939 s (Startwert-Basis 20769 = 2026-11-12). 3 (0.0 %) mit harten Befunden, 75909 (75.9 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 3 | 0.0 % | 3 | Startwert 20769113958: Woche ab 2026-12-14: Frische bis -67 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42706 | 42.7 % | 119519 | Startwert 20769062710: Woche ab 2026-11-02: Frische bis -49 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27105 | 27.1 % | 104666 | Startwert 20769068042: Woche ab 2026-11-16: geplant 293 TSS, 3.3-mal so viel wie zuletzt gefahren (89/Woche, beste Woche 266) |
| # × … – eintönig | 25307 | 25.3 % | 108897 | Startwert 20769062311: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 20511 | 20.5 % | 22221 | Startwert 20769062726: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4472 | 4.5 % | 27703 | Startwert 20769063508: 2026-11-09: harte Einheit (VO2max 6 × 2) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3986 | 4.0 % | 3986 | Startwert 20769063753: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3028 | 3.0 % | 3028 | Startwert 20769064472: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2942 | 2.9 % | 5532 | Startwert 20769117893: Woche ab 2026-11-16: Fitness steigt um 8.1 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2771 | 2.8 % | 2771 | Startwert 20769066897: Event-Tag: Frische 30 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2550 | 2.6 % | 2550 | Startwert 20769062311: Event-Tag: Frische 31 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2298 | 2.3 % | 2298 | Startwert 20769080919: Woche ab 2026-11-30: nach 7 Tagen Krankheit 246 min geplant, mehr als davor (245) |
| Erholungswoche direkt vor dem Tapering | 649 | 0.6 % | 649 | Startwert 20769064408: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 524 | 0.5 % | 524 | Startwert 20769070649: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 117 | 0.1 % | 117 | Startwert 20769104271: Woche ab 2026-12-21: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20769113958: PERFORMANCE, ULTRA COMPETE 606 km/5454 hm in 10 Wochen, Zeit Mo–So [2.5, 3.25, 2.75, 3.25, 3.25, 4.0, 6.0] (25.0 h), Ausfälle 3 %, fährt 103 % der Dauer [#20769113958]
- **Frische bis # TSB gefallen** – Startwert 20769062710: PERFORMANCE, GRAVEL GOOD_TIME 185 km/925 hm in 2 Wochen, Zeit Mo–So [1.25, 2.0, 0.0, 1.75, 1.25, 6.0, 1.75] (14.0 h), Ausfälle 6 %, fährt 125 % der Dauer, pendelt 60 min [#20769062710]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20769068042: PERFORMANCE, BIKEPACKING COMPETE 873 km/5238 hm in 3 Wochen, Zeit Mo–So [0.0, 0.0, 2.0, 1.75, 3.5, 2.75, 0.0] (10.0 h), Ausfälle 17 %, fährt 90 % der Dauer, krank Woche 2 (7 Tage) [#20769068042]
- **# × … – eintönig** – Startwert 20769062311: PERFORMANCE, TIME_TRIAL GOOD_TIME 28 km/112 hm in 2 Wochen, Zeit Mo–So [5.25, 0.0, 5.25, 3.25, 0.0, 4.0, 1.75] (19.5 h), Ausfälle 22 %, fährt 111 % der Dauer, pendelt 30 min [#20769062311]
- **# Belastungswochen am Stück** – Startwert 20769062726: PERFORMANCE, TIME_TRIAL GOOD_TIME 46 km/1150 hm in 6 Wochen, Zeit Mo–So [3.0, 0.0, 2.5, 0.0, 3.75, 0.0, 2.75] (12.0 h), Ausfälle 5 %, fährt 116 % der Dauer, pendelt 90 min [#20769062726]
- **harte Einheit am Tag nach # min geplant** – Startwert 20769063508: AMBITIOUS, CRITERIUM FINISH 67 km/134 hm in 2 Wochen, Zeit Mo–So [2.0, 0.0, 0.0, 1.75, 2.75, 1.5, 2.75] (10.8 h), Ausfälle 8 %, fährt 93 % der Dauer, pendelt 40 min, Schichtdienst [#20769063508]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20769063753: AMBITIOUS, GRAVEL FINISH 243 km/3645 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 4.5, 3.25] (7.8 h), Ausfälle 22 %, fährt 97 % der Dauer, Rennen Woche 2 [#20769063753]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20769064472: AMBITIOUS, CRITERIUM COMPETE 42 km/462 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.75, 0.5, 1.0, 0.75, 2.5] (5.5 h), Ausfälle 8 %, fährt 100 % der Dauer, Urlaub Woche 2 (1 Wo.) [#20769064472]
- **Fitness steigt um # CTL** – Startwert 20769117893: HOBBY, BIKEPACKING COMPETE 1151 km/2302 hm in 3 Wochen, Zeit Mo–So [1.75, 1.25, 1.75, 1.0, 1.0, 4.5, 2.75] (14.0 h), Ausfälle 1 %, fährt 118 % der Dauer, Reisewoche 2, Schichtdienst [#20769117893]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20769066897: PERFORMANCE, ROAD_RACE COMPETE 199 km/1194 hm in 2 Wochen, Zeit Mo–So [1.25, 1.0, 0.75, 0.0, 2.25, 7.0, 3.0] (15.3 h), Ausfälle 17 %, fährt 106 % der Dauer, Schichtdienst [#20769066897]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20769062311: PERFORMANCE, TIME_TRIAL GOOD_TIME 28 km/112 hm in 2 Wochen, Zeit Mo–So [5.25, 0.0, 5.25, 3.25, 0.0, 4.0, 1.75] (19.5 h), Ausfälle 22 %, fährt 111 % der Dauer, pendelt 30 min [#20769062311]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20769080919: PERFORMANCE, ROAD_RACE GOOD_TIME 75 km/1200 hm in 5 Wochen, Zeit Mo–So [0.0, 1.5, 0.0, 1.5, 0.75, 2.75, 1.75] (8.3 h), Ausfälle 0 %, fährt 100 % der Dauer, Rennen Woche 4, krank Woche 4 (7 Tage) [#20769080919]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20769064408: AMBITIOUS, GRAVEL FINISH 125 km/750 hm in 4 Wochen, Zeit Mo–So [3.0, 0.0, 0.0, 2.75, 2.75, 0.0, 0.0] (8.5 h), Ausfälle 10 %, fährt 104 % der Dauer, pendelt 70 min, krank Woche 1 (13 Tage) [#20769064408]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20769070649: BEGINNER, GRAN_FONDO FINISH 108 km/756 hm in 2 Wochen, Zeit Mo–So [1.5, 0.75, 1.5, 1.25, 1.0, 3.0, 1.75] (10.8 h), Ausfälle 8 %, fährt 126 % der Dauer, pendelt 70 min [#20769070649]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20769104271: PERFORMANCE, GRAVEL GOOD_TIME 245 km/4410 hm in 12 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 6.0, 4.5] (10.5 h), Ausfälle 4 %, fährt 102 % der Dauer, pendelt 40 min [#20769104271]
