# Zufallssuche (erfundene Zufallsfahrer im Wochen-Kreislauf)

100000 zufällige Fahrer in 1415 s (Startwert-Basis 20777 = 2026-11-20). 4 (0.0 %) mit harten Befunden, 76375 (76.4 %) mit Warnungen, 15 Arten von Befunden.

Nachstellen: `./gradlew fuzzSimulation -Pseed=<Startwert>` → build/fuzz-rider.md mit Wochenverlauf.

| Art | Fahrer | Anteil | Vorkommen | kürzestes Beispiel |
|---|---|---|---|---|
| HART: Frische bis # TSB gefallen | 4 | 0.0 % | 5 | Startwert 20777074328: Woche ab 2027-03-22: Frische bis -64 TSB gefallen (Grenze -60) |
| Frische bis # TSB gefallen | 43080 | 43.1 % | 121289 | Startwert 20777065474: Woche ab 2026-11-02: Frische bis -53 TSB gefallen (Warnung ab -35, selbst verursacht) |
| geplant # TSS, #-mal so viel wie zuletzt gefahren | 27564 | 27.6 % | 107658 | Startwert 20777062351: Woche ab 2026-11-16: geplant 177 TSS, 2.1-mal so viel wie zuletzt gefahren (83/Woche, beste Woche 119) |
| # × … – eintönig | 23868 | 23.9 % | 99696 | Startwert 20777062736: Woche ab 2026-11-02: 4 × "Lockeres Ausrollen 45 min" – eintönig |
| # Belastungswochen am Stück | 21474 | 21.5 % | 23312 | Startwert 20777063272: Woche ab 2026-11-30: 5 Belastungswochen am Stück |
| harte Einheit am Tag nach # min geplant | 4715 | 4.7 % | 28978 | Startwert 20777067487: 2026-11-09: harte Einheit (Norwegian Schwelle 6 × 8) am Tag nach 240 min geplant |
| nach dem Rennen keine Entlastung in den # Wochen danach | 4071 | 4.1 % | 4071 | Startwert 20777062903: nach dem Rennen (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Fitness steigt um # CTL | 3200 | 3.2 % | 5944 | Startwert 20777063773: Woche ab 2026-11-16: Fitness steigt um 12.6 CTL (Grenze 8) |
| nach dem Urlaub keine Entlastung in den # Wochen danach | 3017 | 3.0 % | 3017 | Startwert 20777067319: nach dem Urlaub (Woche ab 2026-11-09) keine Entlastung in den 2 Wochen danach |
| Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE | 2745 | 2.7 % | 2745 | Startwert 20777065281: Event-Tag: Frische 29 TSB passt nicht zum Ziel COMPETE |
| Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME | 2619 | 2.6 % | 2619 | Startwert 20777062591: Event-Tag: Frische -7 TSB passt nicht zum Ziel GOOD_TIME |
| nach # Tagen Krankheit # min geplant, mehr als davor | 2194 | 2.2 % | 2194 | Startwert 20777108855: Woche ab 2026-11-30: nach 7 Tagen Krankheit 245 min geplant, mehr als davor (228) |
| Erholungswoche direkt vor dem Tapering | 627 | 0.6 % | 627 | Startwert 20777080836: Woche ab 2026-11-16: Erholungswoche direkt vor dem Tapering |
| Event-Tag: Frische # TSB passt nicht zum Ziel FINISH | 500 | 0.5 % | 500 | Startwert 20777064100: Event-Tag: Frische -10 TSB passt nicht zum Ziel FINISH |
| zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde | 116 | 0.1 % | 116 | Startwert 20777148357: Woche ab 2026-12-21: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde |

## Beispiel-Fahrer je Art

- **HART: Frische bis # TSB gefallen** – Startwert 20777074328: PERFORMANCE, GRAN_FONDO COMPETE 215 km/3010 hm in 21 Wochen, Zeit Mo–So [2.25, 0.0, 1.0, 2.75, 1.75, 5.25, 6.5] (19.5 h), Ausfälle 22 %, fährt 104 % der Dauer, pendelt 50 min, krank Woche 10 (17 Tage) [#20777074328]
- **Frische bis # TSB gefallen** – Startwert 20777065474: AMBITIOUS, ULTRA COMPETE 1022 km/10220 hm in 2 Wochen, Zeit Mo–So [1.5, 1.5, 1.25, 2.5, 1.5, 4.75, 3.5] (16.5 h), Ausfälle 7 %, fährt 127 % der Dauer [#20777065474]
- **geplant # TSS, #-mal so viel wie zuletzt gefahren** – Startwert 20777062351: BEGINNER, BIKEPACKING COMPETE 1423 km/14230 hm in 3 Wochen, Zeit Mo–So [0.75, 0.75, 0.0, 2.0, 1.0, 1.5, 0.0] (6.0 h), Ausfälle 18 %, fährt 80 % der Dauer [#20777062351]
- **# × … – eintönig** – Startwert 20777062736: AMBITIOUS, GRAN_FONDO GOOD_TIME 153 km/1530 hm in 2 Wochen, Zeit Mo–So [0.0, 0.5, 1.75, 1.25, 1.75, 2.0, 1.75] (9.0 h), Ausfälle 18 %, unter der Woche +34 %, fährt 119 % der Dauer [#20777062736]
- **# Belastungswochen am Stück** – Startwert 20777063272: HOBBY, BIKEPACKING GOOD_TIME 649 km/10384 hm in 6 Wochen, Zeit Mo–So [1.25, 1.75, 1.25, 0.75, 0.75, 3.25, 1.75] (10.8 h), Ausfälle 2 %, fährt 84 % der Dauer, Urlaub Woche 4 (3 Wo.), Schichtdienst [#20777063272]
- **harte Einheit am Tag nach # min geplant** – Startwert 20777067487: HOBBY, GRAN_FONDO COMPETE 165 km/2310 hm in 2 Wochen, Zeit Mo–So [1.5, 0.5, 0.0, 0.0, 0.0, 1.75, 4.0] (7.8 h), Ausfälle 8 %, fährt 102 % der Dauer, pendelt 60 min [#20777067487]
- **nach dem Rennen keine Entlastung in den # Wochen danach** – Startwert 20777062903: AMBITIOUS, CRITERIUM GOOD_TIME 69 km/276 hm in 5 Wochen, Zeit Mo–So [1.5, 2.0, 1.5, 1.5, 1.25, 2.25, 5.0] (15.0 h), Ausfälle 25 %, fährt 112 % der Dauer, pendelt 80 min, Rennen Woche 2 [#20777062903]
- **Fitness steigt um # CTL** – Startwert 20777063773: HOBBY, BIKEPACKING COMPETE 1423 km/15653 hm in 4 Wochen, Zeit Mo–So [2.0, 1.0, 1.25, 1.5, 1.5, 4.25, 4.5] (16.0 h), Ausfälle 6 %, fährt 127 % der Dauer, pendelt 80 min, Schichtdienst [#20777063773]
- **nach dem Urlaub keine Entlastung in den # Wochen danach** – Startwert 20777067319: HOBBY, GRAN_FONDO COMPETE 146 km/1022 hm in 5 Wochen, Zeit Mo–So [0.0, 0.0, 1.25, 0.5, 0.0, 1.75, 2.75] (6.3 h), Ausfälle 7 %, fährt 91 % der Dauer, Urlaub Woche 2 (1 Wo.) [#20777067319]
- **Event-Tag: Frische # TSB passt nicht zum Ziel COMPETE** – Startwert 20777065281: AMBITIOUS, GRAVEL COMPETE 67 km/469 hm in 2 Wochen, Zeit Mo–So [1.75, 1.75, 2.25, 1.5, 2.0, 2.5, 5.5] (17.3 h), Ausfälle 13 %, unter der Woche +57 %, fährt 99 % der Dauer [#20777065281]
- **Event-Tag: Frische # TSB passt nicht zum Ziel GOOD_TIME** – Startwert 20777062591: AMBITIOUS, TIME_TRIAL GOOD_TIME 15 km/255 hm in 2 Wochen, Zeit Mo–So [0.0, 3.0, 0.0, 3.0, 3.75, 0.0, 0.0] (9.8 h), Ausfälle 7 %, fährt 111 % der Dauer, pendelt 90 min [#20777062591]
- **nach # Tagen Krankheit # min geplant, mehr als davor** – Startwert 20777108855: HOBBY, GRAN_FONDO FINISH 217 km/1736 hm in 5 Wochen, Zeit Mo–So [0.0, 1.5, 0.0, 1.0, 0.0, 2.25, 2.25] (7.0 h), Ausfälle 9 %, fährt 123 % der Dauer, krank Woche 4 (7 Tage) [#20777108855]
- **Erholungswoche direkt vor dem Tapering** – Startwert 20777080836: PERFORMANCE, GRAN_FONDO COMPETE 96 km/2016 hm in 4 Wochen, Zeit Mo–So [0.0, 3.0, 4.0, 2.0, 2.0, 2.75, 2.75] (16.5 h), Ausfälle 19 %, fährt 88 % der Dauer, krank Woche 1 (13 Tage), Schichtdienst [#20777080836]
- **Event-Tag: Frische # TSB passt nicht zum Ziel FINISH** – Startwert 20777064100: HOBBY, CRITERIUM FINISH 64 km/768 hm in 2 Wochen, Zeit Mo–So [0.0, 1.5, 0.0, 0.0, 1.25, 0.0, 1.0] (3.8 h), Ausfälle 4 %, fährt 91 % der Dauer, pendelt 90 min [#20777064100]
- **zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde** – Startwert 20777148357: AMBITIOUS, CRITERIUM COMPETE 40 km/840 hm in 12 Wochen, Zeit Mo–So [0.0, 1.0, 2.5, 1.0, 0.0, 1.75, 0.0] (6.3 h), Ausfälle 19 %, fährt 113 % der Dauer, pendelt 30 min, Rennen Woche 6, krank Woche 4 (7 Tage), Reisewoche 1 [#20777148357]
