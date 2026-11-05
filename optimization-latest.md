# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

26 Parameter-Sätze bewertet in 7 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Kein belastbarer Vorteil gegenüber den App-Werten.**

Änderungen: volumeStep 0.05 → 0.07, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.25, maxLoadWeeksWithoutRecovery 6 → 7, taperWeeks 2 → 3

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 7.51 | 12.14 |
| Gesamtwert Prüf-Satz | 3.40 | 6.04 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 13.53 | 15.43 |
| schwächste 10 % (Prüf-Satz) | -14.54 | -13.08 |
| Überlastung im Mittel (Prüf-Satz) | 0.31 | 0.25 |
| Warnungen je Fahrer (Prüf-Satz) | 2.55 | 2.45 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.01 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 6.51 | 6.35 |

## Was jede Stellschraube bewirkt (Prüf-Satz, ausgehend von den App-Werten)

| Stellschraube | App-Wert | kleiner | Gesamtwert | größer | Gesamtwert |
|---|---|---|---|---|---|
| loadWeeksPerBlock | 3 | 2 | +0.57 | 4 | +0.15 |
| volumeStep | 0.05 | 0.03 | +0.74 | 0.07 | -0.14 |
| recoveryVolume | 0.6 | 0.5 | +0.16 | 0.7 | +0.32 |
| recoveryAcuteRatio | 0.75 | 0.65 | -0.15 | 0.85 | +0.76 |
| acuteChronicRatio | 1.18 | 1.16 | +0.83 | 1.2 | -0.84 |
| restedBlockRatio | 0.9 | 0.8 | +0.49 | 1 | -0.46 |
| spikeRatio | 1.35 | 1.25 | -0.11 | 1.45 | +0.05 |
| spikeMinExtraMinutes | 90 | 60 | -0.10 | 120 | +0.09 |
| maxLoadWeeksWithoutRecovery | 6 | 4 | -0.95 | 7 | -0.04 |
| memoryWeeklyDecay | 0.95 | 0.93 | +0.39 | 0.97 | -0.38 |
| taperWeeks | 2 | 1 | -7.95 | 3 | +2.11 |

## Verlauf der Suche

- 3: 10.15 – taperWeeks 2 → 3
- 5: 10.51 – volumeStep 0.05 → 0.07, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.96, taperWeeks 2 → 3
- 12: 10.82 – volumeStep 0.05 → 0.07, acuteChronicRatio 1.18 → 1.17, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.96, taperWeeks 2 → 3
- 13: 11.96 – volumeStep 0.05 → 0.07, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.25, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.96, taperWeeks 2 → 3
- 24: 12.14 – volumeStep 0.05 → 0.07, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.25, maxLoadWeeksWithoutRecovery 6 → 7, taperWeeks 2 → 3
