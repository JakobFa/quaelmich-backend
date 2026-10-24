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
		String source,
		/** false = ohne Wattmesser aufgezeichnet; dann rechnet intervals.icu die Last aus dem Puls */
		@JsonProperty("device_watts") Boolean deviceWatts,
		@JsonProperty("icu_weighted_avg_watts") Integer weightedAverageWatts) {

	public IntervalsActivity(String id, String startDateLocal, String type, Integer movingTimeSeconds,
			Double distance, Integer trainingLoad, String source) {
		this(id, startDateLocal, type, movingTimeSeconds, distance, trainingLoad, source, null, null);
	}

	public IntervalsActivity(String id, String startDateLocal, String type, Integer movingTimeSeconds,
			Double distance, Integer trainingLoad, String source, Boolean deviceWatts) {
		this(id, startDateLocal, type, movingTimeSeconds, distance, trainingLoad, source, deviceWatts, null);
	}

	/** Alle Sportarten mit Dauer und Startzeit – nur keine Strava-Platzhalter (die liefert die API leer) */
	public boolean isUsable() {
		return movingTimeSeconds != null && movingTimeSeconds > 0 && startDateLocal != null
				&& !"STRAVA".equalsIgnoreCase(source);
	}

	public LocalDate date() {
		return LocalDate.parse(startDateLocal.substring(0, 10));
	}

	/**
	 * intervals.icu liefert die Trainingslast für alle Sportarten; fehlt sie, wird sie aus der Dauer geschätzt.
	 * Radfahrt ohne Wattmesser: Die Last stammt aus dem Puls und wird als Schätzung markiert.
	 */
	public RecordedActivity toRecordedActivity() {
		Sport sport = Sport.fromActivityType(type);
		boolean fromHeartRate = sport == Sport.RIDE && trainingLoad != null && Boolean.FALSE.equals(deviceWatts);
		return RecordedActivity.withEstimateIfMissing(id, sport,
				LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance, trainingLoad,
				fromHeartRate, false)
				.withNormalizedPower(Boolean.TRUE.equals(deviceWatts) ? weightedAverageWatts : null);
	}

}
