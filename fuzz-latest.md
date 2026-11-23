# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1302 s (Startwert-Basis 20780 = 2026-11-23). 5 (0.0 %) mit harten Befunden, 75872 (75.9 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 5 | 0.0 % | 7 | Startwert 20780094992: Woche ab 2027-03-01: Frische bis -65 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 43069 | 43.1 % | 120791 | Startwert 20780064308: Woche ab 2026-11-09: Frische bis -39 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27321 | 27.3 % | 103586 | Startwert 20780065251: Woche ab 2026-11-16: geplant 114 TSS, 1.7-mal so viel wie zuletzt gefahren (65/Woche, beste Woche 77) |
| # × … – eintönig | 24626 | 24.6 % | 103874 | Startwert 20780062876: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 30 min" – eintönig |
| # Belastungswochen am Stück | 20830 | 20.8 % | 22584 | Startwert 20780062496: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4477 | 4.5 % | 26674 | Startwert 20780082040: 2026-11-09: harte Einheit (Schwelle 1 × 20) am Tag nach 270 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4086 | 4.1 % | 4086 | Startwert 20780063586: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3037 | 3.0 % | 3037 | Startwert 20780064124: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 2964 | 3.0 % | 5659 | Startwert 20780068117: Woche ab 2026-11-16: Fitness steigt um 11.9 CTL (Grenze 8) |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2737 | 2.7 % | 2737 | Startwert 20780070071: Event-Tag: Frische -6 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2577 | 2.6 % | 2577 | Startwert 20780065173: Event-Tag: Frische -7 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2195 | 2.2 % | 2195 | Startwert 20780063337: Woche ab 2026-11-30: nach 7 Tagen Krankheit 320 min geplant, mehr als davor (269) |
| Erholungswoche direkt vor dem Tapering | 578 | 0.6 % | 578 | Startwert 20780063198: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 528 | 0.5 % | 528 | Startwert 20780067919: Event-Tag: Frische -10 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 121 | 0.1 % | 121 | Startwert 20780117860: Woche ab 2026-12-07: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20780094992: PERFORMANCE, ULTRA GOOD_TIME 1231 km/12310 hm in 23 Wochen, Zeit Mo–So [3.5, 1.5, 3.0, 3.5, 1.5, 7.0, 5.75] (25.8 h), Ausfälle 8 %, fährt 104 % der Dauer [#20780094992]
- **Frische bis # TSB gefallen** – Startwert 20780064308: HOBBY, ULTRA COMPETE 1334 km/32016 hm in 2 Wochen, Zeit Mo–So [1.75, 1.0, 1.0, 1.25, 1.75, 2.25, 3.75] (12.8 h), Ausfälle 8 %, fährt 123 % der Dauer [#20780064308]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20780065251: BEGINNER, TIME_TRIAL FINISH 78 km/312 hm in 2 Wochen, Zeit Mo–So [0.0, 1.5, 0.5, 0.0, 1.0, 0.0, 0.0] (3.0 h), Ausfälle 5 %, fährt 127 % der Dauer, Schichtdienst [#20780065251]
- **# × … – eintönig** – Startwert 20780062876: HOBBY, GRAVEL GOOD_TIME 220 km/4400 hm in 2 Wochen, Zeit Mo–So [1.0, 1.0, 0.0, 1.0, 1.0, 1.0, 2.5] (7.5 h), Ausfälle 6 %, fährt 113 % der Dauer, pendelt 40 min [#20780062876]
- **# Belastungswochen am Stück** – Startwert 20780062496: BEGINNER, CRITERIUM GOOD_TIME 57 km/456 hm in 6 Wochen, Zeit Mo–So [1.25, 0.5, 0.75, 0.75, 1.25, 3.25, 2.5] (10.3 h), Ausfälle 23 %, fährt 123 % der Dauer [#20780062496]
- **harte Einheit am Tag nach # min geplant** – Startwert 20780082040: AMBITIOUS, BIKEPACKING FINISH 574 km/1148 hm in 2 Wochen, Zeit Mo–So [2.0, 1.75, 2.0, 1.75, 2.25, 2.0, 4.0] (15.8 h), Ausfälle 22 %, unter der Woche +41 %, fährt 120 % der Dauer, Schichtdienst [#20780082040]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20780063586: HOBBY, GRAN_FONDO GOOD_TIME 81 km/1863 hm in 5 Wochen, Zeit Mo–So [0.0, 1.75, 1.0, 2.25, 1.25, 0.0, 1.0] (7.3 h), Ausfälle 8 %, unter der Woche +50 %, fährt 95 % der Dauer, Rennen Woche 2 [#20780063586]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20780064124: HOBBY, CRITERIUM GOOD_TIME 58 km/348 hm in 5 Wochen, Zeit Mo–So [0.75, 1.25, 1.0, 0.75, 0.0, 2.0, 2.25] (8.0 h), Ausfälle 12 %, fährt 111 % der Dauer, Urlaub Woche 2 (1 Wo.) [#20780064124]
- **Fitness steigt um # CTL** – Startwert 20780068117: HOBBY, GRAVEL COMPETE 180 km/540 hm in 4 Wochen, Zeit Mo–So [1.5, 1.25, 1.5, 1.5, 1.75, 4.0, 4.5] (16.0 h), Ausfälle 7 %, fährt 117 % der Dauer, pendelt 20 min [#20780068117]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20780070071: HOBBY, GRAVEL COMPETE 138 km/1656 hm in 2 Wochen, Zeit Mo–So [0.0, 0.75, 0.5, 2.25, 1.25, 0.75, 2.25] (7.8 h), Ausfälle 18 %, fährt 107 % der Dauer, pendelt 60 min, Schichtdienst [#20780070071]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20780065173: BEGINNER, ULTRA GOOD_TIME 773 km/17006 hm in 2 Wochen, Zeit Mo–So [0.5, 0.75, 0.5, 0.25, 0.5, 1.0, 1.5] (5.0 h), Ausfälle 7 %, unter der Woche +31 %, fährt 82 % der Dauer, pendelt 70 min [#20780065173]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20780063337: HOBBY, ULTRA FINISH 434 km/8680 hm in 5 Wochen, Zeit Mo–So [1.75, 1.75, 0.0, 2.25, 1.0, 1.0, 1.5] (9.3 h), Ausfälle 1 %, fährt 94 % der Dauer, krank Woche 4 (7 Tage) [#20780063337]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20780063198: HOBBY, TIME_TRIAL GOOD_TIME 88 km/1936 hm in 4 Wochen, Zeit Mo–So [1.5, 1.75, 1.75, 1.5, 1.25, 3.0, 2.5] (13.3 h), Ausfälle 24 %, fährt 106 % der Dauer, pendelt 40 min, krank Woche 1 (13 Tage) [#20780063198]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20780067919: BEGINNER, ULTRA FINISH 1061 km/15915 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.5, 1.75] (4.3 h), Ausfälle 4 %, fährt 78 % der Dauer, pendelt 90 min [#20780067919]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20780117860: AMBITIOUS, BIKEPACKING FINISH 757 km/19682 hm in 10 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.0, 0.0, 2.25, 5.0] (7.3 h), Ausfälle 25 %, fährt 94 % der Dauer, pendelt 70 min, krank Woche 1 (11 Tage) [#20780117860]
