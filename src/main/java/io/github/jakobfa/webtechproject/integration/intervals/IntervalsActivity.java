package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.model.Sport;

/**
 * Eine Aktivität aus intervals.icu (nur die Felder, die quaelmich braucht).
 * Aktivitäten, die aus Strava stammen, liefert die API wegen der Strava-Bedingungen nur als leeren Platzhalter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntervalsActivity(
		String id,
		@JsonProperty("start_date_local") String startDateLocal,
		String type,
		@JsonProperty("moving_time") Integer movingTimeSeconds,
		Double distance,
		@JsonProperty("icu_training_load") Integer trainingLoad,
		String source) {

	/** Alle Sportarten mit Dauer und Startzeit – nur keine Strava-Platzhalter (die liefert die API leer) */
	public boolean isUsable() {
		return movingTimeSeconds != null && movingTimeSeconds > 0 && startDateLocal != null
				&& !"STRAVA".equalsIgnoreCase(source);
	}

	public LocalDate date() {
		return LocalDate.parse(startDateLocal.substring(0, 10));
	}

	/** intervals.icu liefert die Trainingslast für alle Sportarten; fehlt sie, wird sie aus der Dauer geschätzt */
	public RecordedActivity toRecordedActivity() {
		return RecordedActivity.withEstimateIfMissing(id, Sport.fromActivityType(type),
				LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance, trainingLoad, false,
				false);
	}

}
