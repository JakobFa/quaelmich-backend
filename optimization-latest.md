# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

958 Parameter-Sätze bewertet in 253 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Vorschlag ist auch auf dem Prüf-Satz besser.**

Änderungen: loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.5, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 10.94 | 16.70 |
| Gesamtwert Prüf-Satz | 5.91 | 8.65 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 14.77 | 17.21 |
| schwächste 10 % (Prüf-Satz) | -12.25 | -11.11 |
| Überlastung im Mittel (Prüf-Satz) | 0.21 | 0.05 |
| Warnungen je Fahrer (Prüf-Satz) | 2.52 | 2.95 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.00 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 5.84 | 5.94 |

## Was jede Stellschraube bewirkt (Prüf-Satz, ausgehend von den App-Werten)

| Stellschraube | App-Wert | kleiner | Gesamtwert | größer | Gesamtwert |
|---|---|---|---|---|---|
| loadWeeksPerBlock | 3 | 2 | -0.03 | 4 | -0.24 |
| volumeStep | 0.05 | 0.03 | +0.26 | 0.07 | -0.21 |
| recoveryVolume | 0.6 | 0.5 | -0.33 | 0.7 | +0.07 |
| recoveryAcuteRatio | 0.75 | 0.65 | -0.15 | 0.85 | +0.32 |
| acuteChronicRatio | 1.18 | 1.16 | +0.43 | 1.2 | -0.63 |
| restedBlockRatio | 0.9 | 0.8 | +0.26 | 1 | -0.68 |
| spikeRatio | 1.35 | 1.25 | -0.01 | 1.45 | -0.00 |
| spikeMinExtraMinutes | 90 | 60 | +0.08 | 120 | +0.03 |
| maxLoadWeeksWithoutRecovery | 6 | 4 | -0.46 | 7 | -0.36 |
| memoryWeeklyDecay | 0.95 | 0.93 | +0.33 | 0.97 | -0.38 |
| taperWeeks | 2 | 1 | -3.98 | 3 | +0.88 |

## Verlauf der Suche

- 2: 11.20 – maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93
- 6: 11.33 – recoveryVolume 0.6 → 0.5, spikeRatio 1.35 → 1.3, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93
- 10: 11.43 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.5, spikeRatio 1.35 → 1.3, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93
- 14: 13.56 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.5, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 18: 13.78 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 19: 13.84 – volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 22: 14.12 – volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 105, maxLoadWeeksWithoutRecovery 6 → 7, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 34: 14.56 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 105, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 41: 15.14 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.16, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 105, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 43: 15.33 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.16, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 120, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 53: 15.80 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.3, spikeMinExtraMinutes 90 → 120, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 60: 15.91 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 120, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 81: 15.98 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.85, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 120, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 97: 16.10 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 120, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 139: 16.20 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 5, taperWeeks 2 → 3
- 145: 16.34 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.45, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 5, taperWeeks 2 → 3
- 175: 16.41 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.04, recoveryVolume 0.6 → 0.45, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.55, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 5, taperWeeks 2 → 3
- 183: 16.54 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.55, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 5, taperWeeks 2 → 3
- 185: 16.65 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.55, spikeMinExtraMinutes 90 → 45, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
- 679: 16.70 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryVolume 0.6 → 0.5, recoveryAcuteRatio 0.75 → 0.9, acuteChronicRatio 1.18 → 1.13, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.5, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.93, taperWeeks 2 → 3
