package io.github.jakobfa.webtechproject.integration.strava;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.TrainingLoad;
import io.github.jakobfa.webtechproject.model.Sport;

/**
 * Eine Aktivität aus der Strava-API (nur die Felder, die quaelmich braucht).
 * Strava liefert keinen TSS – mit Wattmesser rechnen wir ihn aus der gewichteten Leistung und der FTP aus,
 * sonst schätzen wir ihn aus dem Durchschnittspuls und dem Schwellenpuls (hrTSS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StravaActivity(
		Long id,
		@JsonProperty("sport_type") String sportType,
		@JsonProperty("start_date_local") String startDateLocal,
		@JsonProperty("moving_time") Integer movingTimeSeconds,
		Double distance,
		@JsonProperty("weighted_average_watts") Integer weightedAverageWatts,
		@JsonProperty("device_watts") Boolean deviceWatts,
		@JsonProperty("average_heartrate") Double averageHeartRate,
		@JsonProperty("max_heartrate") Double maxHeartRate,
		/** in Strava als "Pendeln" markiert */
		Boolean commute,
		/** auf der Rolle gefahren */
		Boolean trainer) {

	public StravaActivity(Long id, String sportType, String startDateLocal, Integer movingTimeSeconds,
			Double distance, Integer weightedAverageWatts, Boolean deviceWatts, Double averageHeartRate,
			Double maxHeartRate, Boolean commute) {
		this(id, sportType, startDateLocal, movingTimeSeconds, distance, weightedAverageWatts, deviceWatts,
				averageHeartRate, maxHeartRate, commute, null);
	}

	/** Alle Sportarten mit Dauer und Startzeit */
	public boolean isUsable() {
		return movingTimeSeconds != null && movingTimeSeconds > 0 && startDateLocal != null && id != null;
	}

	public Sport sport() {
		return Sport.fromActivityType(sportType);
	}

	/** Watt-TSS, siehe {@link TrainingLoad#powerTss} */
	public Integer tss(Integer ftp) {
		return TrainingLoad.powerTss(movingTimeSeconds, weightedAverageWatts, deviceWatts, ftp);
	}

	/** Puls-TSS, siehe {@link TrainingLoad#heartRateTss} */
	public Integer heartRateTss(Integer thresholdHeartRate) {
		return TrainingLoad.heartRateTss(movingTimeSeconds, averageHeartRate, thresholdHeartRate);
	}

	/**
	 * Watt-TSS (nur Radfahrten mit Wattmesser), sonst Puls-TSS, sonst – außer bei Radfahrten – eine grobe
	 * Schätzung aus Dauer und Sportart.
	 */
	public RecordedActivity toRecordedActivity(Integer ftp, Integer thresholdHeartRate) {
		Sport sport = sport();
		Integer powerTss = sport == Sport.RIDE ? tss(ftp) : null;
		Integer heartRateTss = powerTss == null ? heartRateTss(thresholdHeartRate) : null;
		// Strava hängt an die Ortszeit ein "Z" an, obwohl es keine UTC-Zeit ist
		return RecordedActivity.withEstimateIfMissing(RecordedActivity.STRAVA_PREFIX + id, sport,
				LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance,
				powerTss != null ? powerTss : heartRateTss, heartRateTss != null, Boolean.TRUE.equals(commute))
				.withNormalizedPower(Boolean.TRUE.equals(deviceWatts) ? weightedAverageWatts : null)
				.withIndoor(RecordedActivity.isIndoor(sportType, trainer));
	}

}
