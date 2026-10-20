package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;

import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Ein eingeplantes Workout, wie es ans Frontend geht: mit vollständigen Workout-Daten aus der Bibliothek.
 */
public record PlannedWorkoutResponse(Long id, LocalDate date, Phase phase, boolean completed, Integer rating,
		Workout workout, Actual actual) {

	/** Tatsächlich gefahrene Werte, null solange keine Fahrt verknüpft ist */
	public record Actual(Integer durationMinutes, Double distanceKm, Integer tss) {
	}

}
