# Plan-Optimierung (Leistungsmodell, erfundene Fahrer)

Sucht Stellschrauben, mit denen planfolgende Zufallsfahrer laut Fitness-Ermüdungs-Modell am Ziel-Tag in besserer Form sind – ohne mehr Befunde. **Vorschlag, keine Änderung:** übernehmen nur nach sportlicher Prüfung. Das Modell ist eine Annahme, keine Messung an echten Menschen.

1064 Parameter-Sätze bewertet in 253 min (Trainings-Satz 600 Fahrer, Prüf-Satz 2000).

**Ergebnis: Vorschlag ist auch auf dem Prüf-Satz besser.**

Änderungen: loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 60, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1

| | App-Werte | Vorschlag |
|---|---|---|
| Gesamtwert Trainings-Satz | 9.30 | 17.20 |
| Gesamtwert Prüf-Satz | 4.59 | 8.66 |
| Form-Zuwachs im Mittel (Prüf-Satz) | 13.82 | 16.18 |
| schwächste 10 % (Prüf-Satz) | -14.18 | -10.78 |
| Überlastung im Mittel (Prüf-Satz) | 0.26 | 0.00 |
| Warnungen je Fahrer (Prüf-Satz) | 1.88 | 2.13 |
| harte Befunde je Fahrer (alle Zufallsfahrer) | 0.00 | 0.00 |
| Warnungen je Fahrer (alle Zufallsfahrer) | 4.53 | 4.19 |

## Was jede Stellschraube bewirkt (Prüf-Satz, ausgehend von den App-Werten)

| Stellschraube | App-Wert | kleiner | Gesamtwert | größer | Gesamtwert |
|---|---|---|---|---|---|
| loadWeeksPerBlock | 3 | 2 | +0.49 | 4 | -0.21 |
| volumeStep | 0.05 | 0.03 | +0.41 | 0.07 | -0.11 |
| recoveryVolume | 0.6 | 0.5 | -0.19 | 0.7 | +0.07 |
| recoveryAcuteRatio | 0.75 | 0.65 | +0.05 | 0.85 | +0.15 |
| acuteChronicRatio | 1.18 | 1.16 | +0.37 | 1.2 | -0.54 |
| restedBlockRatio | 0.9 | 0.8 | +0.30 | 1 | -0.63 |
| spikeRatio | 1.35 | 1.25 | +0.03 | 1.45 | -0.04 |
| spikeMinExtraMinutes | 90 | 60 | +0.12 | 120 | +0.11 |
| maxLoadWeeksWithoutRecovery | 6 | 4 | -0.46 | 7 | -0.29 |
| memoryWeeklyDecay | 0.95 | 0.93 | +0.23 | 0.97 | -0.20 |
| taperWeeks | 2 | 1 | -3.70 | 3 | +0.86 |

## Verlauf der Suche

- Start beim besten Satz bisher: 16.81 – loadWeeksPerBlock 3 → 4, recoveryVolume 0.6 → 0.75, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 9: 16.97 – loadWeeksPerBlock 3 → 4, recoveryVolume 0.6 → 0.8, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 150, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
- 751: 17.20 – loadWeeksPerBlock 3 → 4, volumeStep 0.05 → 0.02, recoveryAcuteRatio 0.75 → 0.6, acuteChronicRatio 1.18 → 1.05, restedBlockRatio 0.9 → 0.8, spikeRatio 1.35 → 1.2, spikeMinExtraMinutes 90 → 60, maxLoadWeeksWithoutRecovery 6 → 5, memoryWeeklyDecay 0.95 → 0.85, taperWeeks 2 → 1
