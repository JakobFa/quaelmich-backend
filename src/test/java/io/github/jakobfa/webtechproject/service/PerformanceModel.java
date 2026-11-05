package io.github.jakobfa.webtechproject.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

/**
 * Fitness-Ermüdungs-Modell (Banister, Impuls-Antwort) für einen erfundenen Fahrer: Jede Belastung (TSS) baut Fitness
 * langsam auf und wieder ab (Zeitkonstante tauFitness) und Ermüdung schnell (tauFatigue). Die Form an einem Tag ist
 * kFitness × Fitness − kFatigue × Ermüdung. Menschen reagieren verschieden: Jeder Fahrer bekommt eigene Werte aus
 * Bereichen, wie sie in der Sportwissenschaft berichtet werden (Fitness ca. 35–50 Tage, Ermüdung ca. 5–12 Tage,
 * Ermüdung wiegt etwa doppelt so schwer).
 *
 * Dazu eine Belastbarkeit: Wer schneller aufbaut, als er verträgt (Fitness-Anstieg pro Woche über maxWeeklyRamp),
 * bekommt einen Abzug – grob für das Risiko von Übertraining, Krankheit und Verletzung, das das reine Modell nicht kennt.
 *
 * Grenze: Das ist ein Modell, keine Messung. Es sagt, welche Pläne unter diesen Annahmen besser sind, nicht, was bei
 * echten Menschen wirkt.
 */
record PerformanceModel(double tauFitness, double tauFatigue, double kFitness, double kFatigue,
		double maxWeeklyRamp) {

	/** Abzug je Fitness-Punkt, um den eine Woche über der Belastbarkeit lag */
	static final double OVERLOAD_PENALTY = 2.0;

	/** Werte für einen Fahrer aus seinem Startwert (reproduzierbar) */
	static PerformanceModel of(long seed) {
		Random r = new Random(seed * 31 + 7);
		return new PerformanceModel(35 + r.nextDouble() * 15, 5 + r.nextDouble() * 7, 1.0, 1.6 + r.nextDouble() * 0.8,
				4 + r.nextDouble() * 4);
	}

	/** Durchschnittliche Werte (z. B. für feste Profile) */
	static PerformanceModel typical() {
		return new PerformanceModel(42, 7, 1.0, 2.0, 6);
	}

	/** Ergebnis für einen Lauf: Form am Ziel-Tag, Zuwachs gegenüber dem Planstart und Abzug für Überlastung */
	record Outcome(double formAtStart, double formAtTarget, double overload) {

		/** Was zählt: Zuwachs an Form bis zum Ziel-Tag, minus Überlastung */
		double score() {
			return formAtTarget - formAtStart - overload;
		}
	}

	Outcome evaluate(Map<LocalDate, Integer> tssByDay, LocalDate firstDay, LocalDate targetDay) {
		LocalDate day = tssByDay.keySet().stream().min(LocalDate::compareTo).orElse(firstDay);
		double fitness = 0;
		double fatigue = 0;
		double formAtStart = Double.NaN;
		double formAtTarget = Double.NaN;
		double overload = 0;
		double weekStartFitness = 0;
		for (; !day.isAfter(targetDay); day = day.plusDays(1)) {
			if (day.equals(firstDay)) {
				formAtStart = kFitness * fitness - kFatigue * fatigue;
				weekStartFitness = fitness;
			}
			if (day.equals(targetDay)) {
				formAtTarget = kFitness * fitness - kFatigue * fatigue; // am Morgen, vor der Fahrt des Tages
			}
			int tss = tssByDay.getOrDefault(day, 0);
			fitness += (tss - fitness) / tauFitness;
			fatigue += (tss - fatigue) / tauFatigue;
			// Wochenende des Plans (jeder 7. Tag ab dem Start): Fitness-Anstieg dieser Woche gegen die Belastbarkeit
			if (!day.isBefore(firstDay) && java.time.temporal.ChronoUnit.DAYS.between(firstDay, day) % 7 == 6) {
				double ramp = fitness - weekStartFitness;
				overload += Math.max(0, ramp - maxWeeklyRamp) * OVERLOAD_PENALTY;
				weekStartFitness = fitness;
			}
		}
		return new Outcome(formAtStart, formAtTarget, overload);
	}
}
