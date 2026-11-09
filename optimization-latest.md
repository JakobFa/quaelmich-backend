# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

1326 Parameter-Sätze bewertet in 252 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Vorschlag ist auch auf dem Prüf-Satz besser.**

Änderungen: loadWeeksPerBlock 3 → 4, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 11.78 | 18.89 |
| Gesamtwert Prüf-Satz | 6.71 | 10.50 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 14.81 | 17.23 |
| schwächste 10 % (Prüf-Satz) | -12.31 | -9.86 |
| Überlastung im Mittel (Prüf-Satz) | 0.21 | 0.00 |
| Warnungen je Fahrer (Prüf-Satz) | 1.74 | 1.81 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.00 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 4.38 | 3.90 |

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
- 225: 17.55 – loadWeeksPerBlock 3 → 2, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 135, maxLoadWeeksWithoutRecovery 6 → 4, memoryWeeklyDecay 0.95 → 0.88, taperWeeks 2 → 1
- 250: 17.70 – loadWeeksPerBlock 3 → 2, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 4, memoryWeeklyDecay 0.95 → 0.88, taperWeeks 2 → 1
- 269: 17.79 – loadWeeksPerBlock 3 → 2, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 4, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 413: 18.03 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 4, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 425: 18.10 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 4, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 468: 18.17 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.65, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.25, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.91, taperWeeks 2 → 1
- 493: 18.23 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.65, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.25, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 510: 18.35 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryAcuteRatio 0.75 → 0.65, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.25, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 514: 18.46 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.7, recoveryAcuteRatio 0.75 → 0.65, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.25, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.87, taperWeeks 2 → 1
- 532: 18.52 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.7, recoveryAcuteRatio 0.75 → 0.65, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.25, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 555: 18.61 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.7, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 592: 18.75 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 712: 18.82 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 741: 18.89 – loadWeeksPerBlock 3 → 4, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
