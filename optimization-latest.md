# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

735 Parameter-Sätze bewertet in 254 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Kein belastbarer Vorteil gegenüber den App-Werten.**

Änderungen: loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 10.94 | 16.56 |
| Gesamtwert Prüf-Satz | 5.91 | 8.71 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 14.77 | 16.84 |
| schwächste 10 % (Prüf-Satz) | -12.25 | -11.29 |
| Überlastung im Mittel (Prüf-Satz) | 0.21 | 0.07 |
| Warnungen je Fahrer (Prüf-Satz) | 2.52 | 2.41 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.00 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 5.84 | 5.28 |

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

- 3: 11.66 – restedBlockRatio 0.9 → 1, taperWeeks 2 → 3
- 7: 11.85 – volumeStep 0.05 → 0.07, restedBlockRatio 0.9 → 1, taperWeeks 2 → 3
- 9: 12.43 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, restedBlockRatio 0.9 → 1, spikeRatio 1.35 → 1.45, taperWeeks 2 → 3
- 12: 13.90 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.45, taperWeeks 2 → 3
- 26: 14.07 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.45, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3
- 29: 14.20 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 60, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3
- 38: 14.30 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 45, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3
- 53: 14.45 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.15, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 45, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 64: 14.61 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.55, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.15, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 45, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 71: 14.73 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.65, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.15, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 45, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 83: 14.83 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.65, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.15, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 84: 15.09 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.65, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 101: 15.20 – volumeStep 0.05 → 0.07, recoveryVolume 0.6 → 0.65, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 117: 15.26 – volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.65, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 146: 15.65 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 159: 15.86 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.4, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 175: 15.94 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.75, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 180: 16.04 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.55, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.92, taperWeeks 2 → 3
- 183: 16.27 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.6, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3
- 220: 16.56 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.06, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.8, acuteChronicRatio 1.18 → 1.14, restedBlockRatio 0.9 → 0.85, spikeRatio 1.35 → 1.45, spikeMinExtraMinutes 90 → 30, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.94, taperWeeks 2 → 3
