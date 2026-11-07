# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

76000 zufällige Fahrer in 1505 s (Startwert-Basis 20764 = 2026-11-07). 0 (0.0 %) mit harten Befunden, 56798 (74.7 %) mit Warnungen, 14 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| Frische bis # TSB gefallen | 31961 | 42.1 % | 88458 | Startwert 20764062337: Woche ab 2026-11-02: Frische bis -42 TSB gefallen (Warnung ab -35, selbst verursacht) |
| # × … – eintönig | 19522 | 25.7 % | 87256 | Startwert 20764062655: Woche ab 2026-11-09: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 19139 | 25.2 % | 73154 | Startwert 20764062675: Woche ab 2026-11-16: geplant 169 TSS, 2.1-mal so viel wie zuletzt gefahren (80/Woche, beste Woche 140) |
| # Belastungswochen am Stück | 15314 | 20.2 % | 16640 | Startwert 20764062476: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 3364 | 4.4 % | 19576 | Startwert 20764062553: 2026-11-09: harte Einheit (VO2max 4 × 4) am Tag nach 255 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 3048 | 4.0 % | 3048 | Startwert 20764062577: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 2335 | 3.1 % | 2335 | Startwert 20764069873: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 1827 | 2.4 % | 1827 | Startwert 20764064109: Event-Tag: Frische -6 TSB passt nicht zum Ziel GOOD_TIME |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 1826 | 2.4 % | 1826 | Startwert 20764063007: Event-Tag: Frische -6 TSB passt nicht zum Ziel COMPETE |
| nach # Tagen Krankheit # min geplant, mehr als davor | 1725 | 2.3 % | 1725 | Startwert 20764064378: Woche ab 2026-11-30: nach 7 Tagen Krankheit 167 min geplant, mehr als davor (135) |
| Fitness steigt um # CTL | 1686 | 2.2 % | 3063 | Startwert 20764068738: Woche ab 2026-11-16: Fitness steigt um 9.9 CTL (Grenze 8) |
| Erholungswoche direkt vor dem Tapering | 482 | 0.6 % | 482 | Startwert 20764065387: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 351 | 0.5 % | 351 | Startwert 20764086534: Event-Tag: Frische -13 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 142 | 0.2 % | 143 | Startwert 20764066848: Woche ab 2026-11-30: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **Frische bis # TSB gefallen** – Startwert 20764062337: HOBBY, ROAD_RACE FINISH 215 km/3440 hm in 2 Wochen, Zeit Mo–So [1.0, 2.0, 1.25, 1.75, 2.0, 4.75, 3.5] (16.3 h), Ausfälle 18 %, unter der Woche +32 %, fährt 127 % der Dauer, pendelt 30 min [#20764062337]
- **# × … – eintönig** – Startwert 20764062655: HOBBY, TIME_TRIAL GOOD_TIME 32 km/736 hm in 2 Wochen, Zeit Mo–So [1.0, 1.75, 1.25, 1.75, 1.0, 4.5, 4.75] (16.0 h), Ausfälle 17 %, fährt 118 % der Dauer, pendelt 70 min [#20764062655]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20764062675: HOBBY, GRAVEL FINISH 114 km/1710 hm in 3 Wochen, Zeit Mo–So [0.5, 0.0, 0.0, 1.0, 0.0, 2.0, 2.75] (6.3 h), Ausfälle 1 %, fährt 77 % der Dauer, krank Woche 1 (6 Tage) [#20764062675]
- **# Belastungswochen am Stück** – Startwert 20764062476: HOBBY, ULTRA FINISH 573 km/12606 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 2.0, 0.0, 0.0, 2.25, 0.0] (4.3 h), Ausfälle 8 %, fährt 127 % der Dauer, krank Woche 1 (7 Tage) [#20764062476]
- **harte Einheit am Tag nach # min geplant** – Startwert 20764062553: HOBBY, ROAD_RACE GOOD_TIME 156 km/1404 hm in 2 Wochen, Zeit Mo–So [1.5, 0.0, 0.0, 0.75, 1.25, 0.75, 3.75] (8.0 h), Ausfälle 20 %, fährt 91 % der Dauer, pendelt 40 min, Schichtdienst [#20764062553]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20764062577: HOBBY, TIME_TRIAL GOOD_TIME 16 km/80 hm in 5 Wochen, Zeit Mo–So [0.0, 2.0, 0.0, 0.75, 1.25, 2.0, 0.5] (6.5 h), Ausfälle 6 %, fährt 123 % der Dauer, Rennen Woche 2 [#20764062577]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20764069873: HOBBY, BIKEPACKING GOOD_TIME 652 km/2608 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 0.0, 0.75, 2.75, 1.75, 1.75] (7.0 h), Ausfälle 14 %, unter der Woche +57 %, fährt 86 % der Dauer, Urlaub Woche 2 (1 Wo.), krank Woche 4 (4 Tage), Reisewoche 5 [#20764069873]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20764064109: AMBITIOUS, ROAD_RACE GOOD_TIME 153 km/1836 hm in 2 Wochen, Zeit Mo–So [2.25, 0.0, 3.5, 0.0, 1.25, 0.0, 0.0] (7.0 h), Ausfälle 11 %, fährt 99 % der Dauer, pendelt 80 min [#20764064109]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20764063007: HOBBY, ULTRA COMPETE 666 km/2664 hm in 2 Wochen, Zeit Mo–So [1.0, 1.25, 0.75, 1.75, 1.25, 3.75, 3.5] (13.3 h), Ausfälle 21 %, fährt 104 % der Dauer, pendelt 20 min [#20764063007]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20764064378: AMBITIOUS, ULTRA GOOD_TIME 750 km/14250 hm in 6 Wochen, Zeit Mo–So [0.0, 0.0, 2.5, 0.0, 0.0, 0.0, 1.75] (4.3 h), Ausfälle 14 %, fährt 124 % der Dauer, krank Woche 4 (7 Tage) [#20764064378]
- **Fitness steigt um # CTL** – Startwert 20764068738: AMBITIOUS, GRAVEL GOOD_TIME 185 km/4810 hm in 4 Wochen, Zeit Mo–So [1.5, 1.0, 1.5, 2.5, 1.25, 2.5, 4.75] (15.0 h), Ausfälle 8 %, fährt 111 % der Dauer, pendelt 90 min, Schichtdienst [#20764068738]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20764065387: HOBBY, GRAVEL FINISH 85 km/1530 hm in 4 Wochen, Zeit Mo–So [0.5, 0.0, 1.75, 1.0, 0.5, 2.0, 0.0] (5.8 h), Ausfälle 8 %, fährt 104 % der Dauer, pendelt 80 min, Rennen Woche 3, krank Woche 1 (11 Tage) [#20764065387]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20764086534: HOBBY, BIKEPACKING FINISH 1269 km/20304 hm in 2 Wochen, Zeit Mo–So [0.0, 0.0, 2.0, 0.0, 0.75, 3.0, 0.0] (5.8 h), Ausfälle 10 %, fährt 95 % der Dauer, pendelt 80 min [#20764086534]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20764066848: HOBBY, GRAN_FONDO COMPETE 180 km/3060 hm in 9 Wochen, Zeit Mo–So [1.25, 1.25, 2.0, 1.25, 0.75, 4.75, 4.0] (15.3 h), Ausfälle 25 %, fährt 124 % der Dauer, Reisewoche 2 [#20764066848]
