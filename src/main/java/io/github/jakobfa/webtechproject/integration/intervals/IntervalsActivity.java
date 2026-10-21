package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.jakobfa.webtechproject.integration.RecordedRide;

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

	private static final Set<String> RIDE_TYPES = Set.of("Ride", "VirtualRide", "GravelRide", "MountainBikeRide");

	/** Nur echte Radfahrten mit Daten – keine Läufe, keine Strava-Platzhalter */
	public boolean isUsableRide() {
		return type != null && RIDE_TYPES.contains(type) && movingTimeSeconds != null && movingTimeSeconds > 0
				&& startDateLocal != null && !"STRAVA".equalsIgnoreCase(source);
	}

	public LocalDate date() {
		return LocalDate.parse(startDateLocal.substring(0, 10));
	}

	public RecordedRide toRecordedRide() {
		return new RecordedRide(id, LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance,
				trainingLoad, false);
	}

}
