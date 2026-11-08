# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

759 Parameter-Sätze bewertet in 254 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Vorschlag ist auch auf dem Prüf-Satz besser.**

Änderungen: loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.5, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 11.78 | 17.39 |
| Gesamtwert Prüf-Satz | 6.71 | 9.13 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 14.81 | 17.19 |
| schwächste 10 % (Prüf-Satz) | -12.31 | -11.61 |
| Überlastung im Mittel (Prüf-Satz) | 0.21 | 0.05 |
| Warnungen je Fahrer (Prüf-Satz) | 1.74 | 2.20 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.00 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 4.38 | 4.61 |

## Was jede Stellschraube bewirkt (Prüf-Satz, ausgehend von den App-Werten)

| Stellschraube | App-Wert | kleiner | Gesamtwert | größer | Gesamtwert |
|---|---|---|---|---|---|
| loadWeeksPerBlock | 3 | 2 | -0.03 | 4 | -0.34 |
| volumeStep | 0.05 | 0.03 | +0.20 | 0.07 | -0.29 |
| recoveryVolume | 0.6 | 0.5 | -0.20 | 0.7 | -0.04 |
| recoveryAcuteRatio | 0.75 | 0.65 | +0.02 | 0.85 | +0.25 |
| acuteChronicRatio | 1.18 | 1.16 | +0.27 | 1.2 | -0.51 |
| restedBlockRatio | 0.9 | 0.8 | +0.31 | 1 | -0.67 |
| spikeRatio | 1.35 | 1.25 | -0.00 | 1.45 | +0.01 |
| spikeMinExtraMinutes | 90 | 60 | +0.13 | 120 | +0.10 |
| maxLoadWeeksWithoutRecovery | 6 | 4 | -0.64 | 7 | -0.35 |
| memoryWeeklyDecay | 0.95 | 0.93 | +0.31 | 0.97 | -0.37 |
| taperWeeks | 2 | 1 | -3.83 | 3 | +0.90 |

## Verlauf der Suche

- Start beim besten Satz bisher: 17.39 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.5, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
