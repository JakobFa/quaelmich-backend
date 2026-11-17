# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1142 s (Startwert-Basis 20774 = 2026-11-17). 5 (0.0 %) mit harten Befunden, 76111 (76.1 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 5 | 0.0 % | 8 | Startwert 20774094192: Woche ab 2026-11-09: Frische bis -65 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 42899 | 42.9 % | 120813 | Startwert 20774063666: Woche ab 2026-11-02: Frische bis -47 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27260 | 27.3 % | 104054 | Startwert 20774063797: Woche ab 2026-11-16: geplant 251 TSS, 2.0-mal so viel wie zuletzt gefahren (125/Woche, beste Woche 210) |
| # × … – eintönig | 24770 | 24.8 % | 104453 | Startwert 20774062519: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 21085 | 21.1 % | 22835 | Startwert 20774062415: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4503 | 4.5 % | 27638 | Startwert 20774067912: 2026-11-09: harte Einheit (Schwelle 2 × 10) am Tag nach 195 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4043 | 4.0 % | 4043 | Startwert 20774065775: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3137 | 3.1 % | 3137 | Startwert 20774064324: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3072 | 3.1 % | 5795 | Startwert 20774074400: Woche ab 2026-11-16: Fitness steigt um 9.4 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2783 | 2.8 % | 2783 | Startwert 20774062843: Event-Tag: Frische 31 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2700 | 2.7 % | 2700 | Startwert 20774062788: Event-Tag: Frische 25 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2331 | 2.3 % | 2331 | Startwert 20774064474: Woche ab 2026-12-07: nach 10 Tagen Krankheit 150 min geplant, mehr als davor (132) |
| Erholungswoche direkt vor dem Tapering | 623 | 0.6 % | 623 | Startwert 20774064717: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 538 | 0.5 % | 538 | Startwert 20774070718: Event-Tag: Frische -12 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 119 | 0.1 % | 119 | Startwert 20774071422: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20774094192: PERFORMANCE, ULTRA GOOD_TIME 362 km/1810 hm in 2 Wochen, Zeit Mo–So [1.5, 1.75, 2.75, 2.0, 3.25, 4.0, 8.0] (23.3 h), Ausfälle 4 %, fährt 109 % der Dauer, pendelt 40 min [#20774094192]
- **Frische bis # TSB gefallen** – Startwert 20774063666: PERFORMANCE, CRITERIUM FINISH 66 km/1188 hm in 2 Wochen, Zeit Mo–So [1.75, 1.5, 3.25, 3.25, 2.75, 6.75, 4.25] (23.5 h), Ausfälle 14 %, unter der Woche +45 %, fährt 110 % der Dauer, pendelt 80 min [#20774063666]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20774063797: PERFORMANCE, ULTRA FINISH 954 km/14310 hm in 3 Wochen, Zeit Mo–So [1.0, 0.0, 0.0, 0.0, 3.25, 3.75, 0.0] (8.0 h), Ausfälle 12 %, fährt 89 % der Dauer [#20774063797]
- **# × … – eintönig** – Startwert 20774062519: BEGINNER, ULTRA FINISH 1066 km/7462 hm in 2 Wochen, Zeit Mo–So [0.0, 2.0, 0.5, 1.25, 1.0, 0.75, 1.5] (7.0 h), Ausfälle 7 %, fährt 99 % der Dauer, Schichtdienst [#20774062519]
- **# Belastungswochen am Stück** – Startwert 20774062415: BEGINNER, GRAN_FONDO FINISH 243 km/5346 hm in 6 Wochen, Zeit Mo–So [1.75, 0.5, 0.75, 0.0, 0.0, 0.75, 0.5] (4.3 h), Ausfälle 7 %, fährt 91 % der Dauer, Urlaub Woche 4 (2 Wo.), Rennen Woche 5 [#20774062415]
- **harte Einheit am Tag nach # min geplant** – Startwert 20774067912: BEGINNER, GRAVEL FINISH 158 km/1896 hm in 2 Wochen, Zeit Mo–So [1.5, 0.75, 1.5, 1.25, 1.0, 2.75, 2.75] (11.5 h), Ausfälle 19 %, fährt 78 % der Dauer, Schichtdienst [#20774067912]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20774065775: BEGINNER, TIME_TRIAL GOOD_TIME 37 km/703 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.75, 1.25] (4.0 h), Ausfälle 14 %, fährt 85 % der Dauer, Rennen Woche 2 [#20774065775]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20774064324: BEGINNER, CRITERIUM GOOD_TIME 64 km/896 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 3.25] (4.3 h), Ausfälle 20 %, fährt 83 % der Dauer, Urlaub Woche 2 (1 Wo.), Rennen Woche 5, Schichtdienst [#20774064324]
- **Fitness steigt um # CTL** – Startwert 20774074400: AMBITIOUS, ULTRA FINISH 1240 km/26040 hm in 4 Wochen, Zeit Mo–So [1.5, 1.5, 2.0, 1.5, 1.5, 2.5, 3.25] (13.8 h), Ausfälle 18 %, fährt 110 % der Dauer, pendelt 80 min, Schichtdienst [#20774074400]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20774062843: PERFORMANCE, GRAVEL COMPETE 90 km/2340 hm in 2 Wochen, Zeit Mo–So [2.0, 3.0, 3.25, 2.5, 2.75, 5.5, 7.25] (26.3 h), Ausfälle 17 %, fährt 95 % der Dauer [#20774062843]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20774062788: PERFORMANCE, CRITERIUM GOOD_TIME 51 km/561 hm in 2 Wochen, Zeit Mo–So [0.0, 5.0, 0.0, 3.25, 2.5, 3.75, 2.75] (17.3 h), Ausfälle 25 %, unter der Woche +53 %, fährt 121 % der Dauer, pendelt 50 min, Schichtdienst [#20774062788]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20774064474: BEGINNER, BIKEPACKING FINISH 973 km/13622 hm in 6 Wochen, Zeit Mo–So [2.25, 0.0, 0.0, 1.0, 0.0, 0.75, 0.0] (4.0 h), Ausfälle 12 %, fährt 130 % der Dauer, krank Woche 4 (10 Tage) [#20774064474]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20774064717: BEGINNER, ULTRA COMPETE 1089 km/28314 hm in 4 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 1.0, 0.75, 1.5, 2.75] (6.0 h), Ausfälle 16 %, fährt 122 % der Dauer, Urlaub Woche 3 (1 Wo.), krank Woche 1 (11 Tage) [#20774064717]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20774070718: HOBBY, GRAN_FONDO FINISH 227 km/5221 hm in 2 Wochen, Zeit Mo–So [1.5, 0.75, 0.0, 0.0, 1.0, 1.25, 3.5] (8.0 h), Ausfälle 9 %, fährt 100 % der Dauer, pendelt 80 min [#20774070718]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20774071422: BEGINNER, TIME_TRIAL GOOD_TIME 57 km/1482 hm in 9 Wochen, Zeit Mo–So [0.75, 1.0, 0.75, 0.75, 1.25, 2.75, 2.25] (9.5 h), Ausfälle 10 %, fährt 111 % der Dauer, Rennen Woche 3, Reisewoche 1 [#20774071422]
