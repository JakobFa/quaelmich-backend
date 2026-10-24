package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.TrainingLoad;
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
		@JsonProperty("icu_weighted_avg_watts") Integer weightedAverageWatts,
		@JsonProperty("average_heartrate") Double averageHeartRate) {

	public IntervalsActivity(String id, String startDateLocal, String type, Integer movingTimeSeconds,
			Double distance, Integer trainingLoad, String source) {
		this(id, startDateLocal, type, movingTimeSeconds, distance, trainingLoad, source, null, null, null);
	}

	public IntervalsActivity(String id, String startDateLocal, String type, Integer movingTimeSeconds,
			Double distance, Integer trainingLoad, String source, Boolean deviceWatts) {
		this(id, startDateLocal, type, movingTimeSeconds, distance, trainingLoad, source, deviceWatts, null, null);
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
	 * Belastung rechnet quaelmich selbst – mit FTP und Schwellenpuls aus dem eigenen Profil, genau wie bei
	 * Strava: Watt-TSS mit Wattmesser, sonst Puls-TSS. Nur wenn beides fehlt, gilt die Last von intervals.icu
	 * (ohne Wattmesser als Schätzung markiert); fehlt auch die, wird sie bei anderen Sportarten aus der Dauer
	 * geschätzt.
	 */
	public RecordedActivity toRecordedActivity(Integer ftp, Integer thresholdHeartRate) {
		Sport sport = Sport.fromActivityType(type);
		Integer powerTss = sport == Sport.RIDE
				? TrainingLoad.powerTss(movingTimeSeconds, weightedAverageWatts, deviceWatts, ftp)
				: null;
		Integer heartRateTss = powerTss == null
				? TrainingLoad.heartRateTss(movingTimeSeconds, averageHeartRate, thresholdHeartRate)
				: null;
		Integer tss;
		boolean estimated;
		if (powerTss != null) {
			tss = powerTss;
			estimated = false;
		} else if (heartRateTss != null) {
			tss = heartRateTss;
			estimated = true;
		} else {
			tss = trainingLoad;
			estimated = sport == Sport.RIDE && trainingLoad != null && Boolean.FALSE.equals(deviceWatts);
		}
		return RecordedActivity.withEstimateIfMissing(id, sport,
				LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance, tss, estimated,
				false)
				.withNormalizedPower(Boolean.TRUE.equals(deviceWatts) ? weightedAverageWatts : null);
	}

}
