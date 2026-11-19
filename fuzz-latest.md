# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1095 s (Startwert-Basis 20776 = 2026-11-19). 9 (0.0 %) mit harten Befunden, 75960 (76.0 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 9 | 0.0 % | 10 | Startwert 20776065462: Woche ab 2027-02-01: Frische bis -62 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42850 | 42.9 % | 120313 | Startwert 20776063805: Woche ab 2026-11-09: Frische bis -45 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27287 | 27.3 % | 103798 | Startwert 20776062846: Woche ab 2026-11-16: geplant 139 TSS, 2.3-mal so viel wie zuletzt gefahren (60/Woche, beste Woche 119) |
| # × … – eintönig | 24854 | 24.9 % | 105611 | Startwert 20776062364: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 21006 | 21.0 % | 22748 | Startwert 20776062672: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4327 | 4.3 % | 26792 | Startwert 20776094839: 2026-11-09: harte Einheit (Schwelle 2 × 10) am Tag nach 225 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3936 | 3.9 % | 3936 | Startwert 20776064048: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3008 | 3.0 % | 3008 | Startwert 20776068541: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2976 | 3.0 % | 5669 | Startwert 20776070781: Woche ab 2026-11-16: Fitness steigt um 12.2 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2750 | 2.8 % | 2750 | Startwert 20776065863: Event-Tag: Frische 28 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2642 | 2.6 % | 2642 | Startwert 20776066544: Event-Tag: Frische 26 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2327 | 2.3 % | 2327 | Startwert 20776097619: Woche ab 2026-11-30: nach 7 Tagen Krankheit 215 min geplant, mehr als davor (202) |
| Erholungswoche direkt vor dem Tapering | 665 | 0.7 % | 665 | Startwert 20776066106: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 469 | 0.5 % | 469 | Startwert 20776086027: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 112 | 0.1 % | 112 | Startwert 20776097059: Woche ab 2026-12-21: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20776065462: PERFORMANCE, GRAVEL FINISH 245 km/1715 hm in 16 Wochen, Zeit Mo–So [2.25, 2.25, 3.0, 2.5, 3.5, 7.75, 8.0] (29.3 h), Ausfälle 2 %, fährt 100 % der Dauer [#20776065462]
- **Frische bis # TSB gefallen** – Startwert 20776063805: AMBITIOUS, BIKEPACKING GOOD_TIME 435 km/2610 hm in 2 Wochen, Zeit Mo–So [1.0, 2.0, 1.25, 2.0, 1.25, 3.25, 4.25] (15.0 h), Ausfälle 25 %, fährt 124 % der Dauer, pendelt 40 min [#20776063805]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20776062846: HOBBY, ULTRA GOOD_TIME 599 km/2995 hm in 3 Wochen, Zeit Mo–So [0.0, 0.5, 1.0, 1.75, 0.0, 0.5, 0.75] (4.5 h), Ausfälle 4 %, fährt 121 % der Dauer, krank Woche 3 (6 Tage), Schichtdienst [#20776062846]
- **# × … – eintönig** – Startwert 20776062364: HOBBY, CRITERIUM COMPETE 63 km/315 hm in 2 Wochen, Zeit Mo–So [1.75, 1.75, 1.75, 1.75, 1.25, 2.0, 3.75] (14.0 h), Ausfälle 12 %, fährt 98 % der Dauer [#20776062364]
- **# Belastungswochen am Stück** – Startwert 20776062672: HOBBY, ULTRA COMPETE 1036 km/12432 hm in 6 Wochen, Zeit Mo–So [1.75, 1.25, 1.25, 1.25, 1.0, 3.25, 4.0] (13.8 h), Ausfälle 10 %, unter der Woche +26 %, fährt 89 % der Dauer [#20776062672]
- **harte Einheit am Tag nach # min geplant** – Startwert 20776094839: BEGINNER, GRAVEL FINISH 133 km/3059 hm in 2 Wochen, Zeit Mo–So [1.25, 1.25, 0.5, 0.5, 1.25, 1.75, 3.25] (9.8 h), Ausfälle 24 %, fährt 79 % der Dauer, Schichtdienst [#20776094839]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20776064048: AMBITIOUS, GRAVEL GOOD_TIME 161 km/322 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.75, 0.0, 0.0, 3.5, 3.0] (7.3 h), Ausfälle 10 %, fährt 89 % der Dauer, Rennen Woche 2, Reisewoche 2 [#20776064048]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20776068541: HOBBY, ULTRA FINISH 934 km/1868 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.75, 2.5] (5.3 h), Ausfälle 22 %, fährt 98 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 3 (15 Tage) [#20776068541]
- **Fitness steigt um # CTL** – Startwert 20776070781: AMBITIOUS, ULTRA COMPETE 624 km/14976 hm in 4 Wochen, Zeit Mo–So [1.0, 2.5, 1.0, 1.25, 2.5, 5.25, 4.25] (17.8 h), Ausfälle 4 %, fährt 118 % der Dauer, pendelt 70 min, Schichtdienst [#20776070781]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20776065863: PERFORMANCE, TIME_TRIAL COMPETE 51 km/867 hm in 2 Wochen, Zeit Mo–So [2.0, 2.0, 3.0, 2.5, 2.75, 5.5, 8.0] (25.8 h), Ausfälle 9 %, fährt 81 % der Dauer [#20776065863]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20776066544: PERFORMANCE, GRAVEL GOOD_TIME 105 km/210 hm in 2 Wochen, Zeit Mo–So [2.25, 0.0, 2.25, 0.0, 0.0, 4.0, 2.75] (11.3 h), Ausfälle 19 %, unter der Woche +36 %, fährt 108 % der Dauer [#20776066544]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20776097619: HOBBY, BIKEPACKING FINISH 780 km/7800 hm in 5 Wochen, Zeit Mo–So [1.5, 0.0, 0.0, 2.25, 0.0, 0.0, 2.5] (6.3 h), Ausfälle 21 %, fährt 120 % der Dauer, Urlaub Woche 3 (3 Wo.), krank Woche 4 (7 Tage) [#20776097619]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20776066106: AMBITIOUS, ULTRA GOOD_TIME 1145 km/9160 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 5.0, 4.0] (9.0 h), Ausfälle 18 %, fährt 112 % der Dauer, krank Woche 1 (13 Tage) [#20776066106]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20776086027: BEGINNER, GRAVEL FINISH 67 km/268 hm in 2 Wochen, Zeit Mo–So [1.75, 2.0, 0.5, 2.0, 1.75, 0.0, 0.75] (8.8 h), Ausfälle 9 %, fährt 90 % der Dauer, pendelt 80 min [#20776086027]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20776097059: HOBBY, GRAVEL FINISH 237 km/2370 hm in 13 Wochen, Zeit Mo–So [0.0, 0.0, 1.25, 0.0, 0.0, 3.25, 1.5] (6.0 h), Ausfälle 12 %, fährt 129 % der Dauer, Rennen Woche 6, krank Woche 4 (9 Tage), Reisewoche 13 [#20776097059]
