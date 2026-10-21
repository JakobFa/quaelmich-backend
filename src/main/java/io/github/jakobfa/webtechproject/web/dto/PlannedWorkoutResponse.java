package io.github.jakobfa.webtechproject.web.dto;

import java.time.LocalDate;

import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Ein eingeplantes Workout, wie es ans Frontend geht: mit vollständigen Workout-Daten aus der Bibliothek.
 */
public record PlannedWorkoutResponse(Long id, LocalDate date, Phase phase, boolean completed, Integer rating,
		Workout workout, Actual actual, Adjustment adjustment) {

	/**
	 * Tatsächlich gefahrene Werte, null solange keine Fahrt verknüpft ist.
	 * confirmed: null = noch nicht bestätigt, true = war das geplante Training, false = anderes Training
	 * stravaUrl: Link "View on Strava", nur bei Fahrten aus Strava
	 */
	public record Actual(Integer durationMinutes, Double distanceKm, Integer tss, Assessment assessment,
			Boolean confirmed, String stravaUrl) {
	}

	/** Gesetzt, wenn quaelmich dieses Workout wegen einer härteren Einheit ersetzt hat */
	public record Adjustment(String note, String originalTitle) {
	}

}
