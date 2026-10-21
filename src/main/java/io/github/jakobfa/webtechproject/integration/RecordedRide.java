package io.github.jakobfa.webtechproject.integration;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Eine gefahrene Radfahrt, egal aus welcher Quelle (intervals.icu oder Strava).
 *
 * @param id             eindeutig über alle Quellen: intervals.icu-IDs wie "i123", Strava-IDs als "strava-456"
 * @param start          Startzeit in Ortszeit
 * @param distanceMeters kann fehlen (z. B. Rolle ohne Distanz)
 * @param tss            Trainingslast; kann fehlen, wenn ohne Wattmesser gefahren wurde
 */
public record RecordedRide(String id, LocalDateTime start, int movingSeconds, Double distanceMeters, Integer tss) {

	public static final String STRAVA_PREFIX = "strava-";

	/** Zwei Fahrten gelten als dieselbe, wenn sie höchstens so weit auseinander starten */
	private static final Duration SAME_RIDE = Duration.ofMinutes(10);

	public LocalDate date() {
		return start.toLocalDate();
	}

	public int movingMinutes() {
		return Math.round(movingSeconds / 60f);
	}

	/** Distanz in km, auf eine Nachkommastelle gerundet */
	public Double distanceKm() {
		return distanceMeters == null ? null : Math.round(distanceMeters / 100.0) / 10.0;
	}

	/** Dieselbe Fahrt, z. B. vom Wahoo sowohl an intervals.icu als auch an Strava geschickt */
	public boolean isSameRideAs(RecordedRide other) {
		return Duration.between(start, other.start).abs().compareTo(SAME_RIDE) <= 0;
	}

	public static boolean isFromStrava(String id) {
		return id != null && id.startsWith(STRAVA_PREFIX);
	}

	/** Link zur Fahrt auf Strava, null bei anderen Quellen */
	public static String stravaUrl(String id) {
		return isFromStrava(id) ? "https://www.strava.com/activities/" + id.substring(STRAVA_PREFIX.length()) : null;
	}

}
