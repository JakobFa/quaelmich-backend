package io.github.jakobfa.webtechproject.integration.strava;

import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.jakobfa.webtechproject.integration.RecordedRide;

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
		@JsonProperty("average_heartrate") Double averageHeartRate) {

	private static final Set<String> RIDE_TYPES = Set.of("Ride", "VirtualRide", "GravelRide", "MountainBikeRide");

	/** Nur Radfahrten ohne Motor, mit Dauer und Startzeit */
	public boolean isUsableRide() {
		return sportType != null && RIDE_TYPES.contains(sportType) && movingTimeSeconds != null
				&& movingTimeSeconds > 0 && startDateLocal != null && id != null;
	}

	/**
	 * TSS = Stunden × IF² × 100, mit IF = gewichtete Leistung / FTP.
	 * Null ohne echten Wattmesser (geschätzte Watt von Strava zählen nicht) oder ohne FTP.
	 */
	public Integer tss(Integer ftp) {
		if (weightedAverageWatts == null || !Boolean.TRUE.equals(deviceWatts) || ftp == null || ftp <= 0) {
			return null;
		}
		double intensity = (double) weightedAverageWatts / ftp;
		return (int) Math.round(movingTimeSeconds / 3600.0 * intensity * intensity * 100);
	}

	/**
	 * hrTSS = Stunden × (Ø-Puls / Schwellenpuls)² × 100 – dieselbe Formel wie beim Watt-TSS, nur mit Puls.
	 * Mit dem Durchschnitt statt Pulszonen wird ein Intervalltraining eher unterschätzt; fürs Pendeln
	 * oder lockere Fahrten passt es gut. Null ohne Pulsdaten oder ohne Schwellenpuls.
	 */
	public Integer heartRateTss(Integer thresholdHeartRate) {
		if (averageHeartRate == null || averageHeartRate <= 0 || thresholdHeartRate == null
				|| thresholdHeartRate <= 0) {
			return null;
		}
		double intensity = averageHeartRate / thresholdHeartRate;
		return (int) Math.round(movingTimeSeconds / 3600.0 * intensity * intensity * 100);
	}

	/** Watt-TSS, wenn möglich – sonst Puls-TSS */
	public RecordedRide toRecordedRide(Integer ftp, Integer thresholdHeartRate) {
		Integer powerTss = tss(ftp);
		Integer tss = powerTss != null ? powerTss : heartRateTss(thresholdHeartRate);
		// Strava hängt an die Ortszeit ein "Z" an, obwohl es keine UTC-Zeit ist
		return new RecordedRide(RecordedRide.STRAVA_PREFIX + id,
				LocalDateTime.parse(startDateLocal.substring(0, 19)), movingTimeSeconds, distance, tss,
				powerTss == null && tss != null);
	}

}
