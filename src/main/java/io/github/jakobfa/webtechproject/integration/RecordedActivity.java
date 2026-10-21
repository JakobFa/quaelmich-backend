package io.github.jakobfa.webtechproject.integration;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import io.github.jakobfa.webtechproject.model.Sport;

/**
 * Eine gemachte Einheit – Radfahrt, Lauf, Schwimmen … –, egal aus welcher Quelle (intervals.icu oder Strava).
 *
 * @param id               eindeutig über alle Quellen: intervals.icu-IDs wie "i123", Strava-IDs als "strava-456"
 * @param sport            Sportart; nur Radfahrten haken geplante Workouts ab
 * @param start            Startzeit in Ortszeit
 * @param distanceMeters   kann fehlen (z. B. Rolle ohne Distanz, Krafttraining)
 * @param tss              Trainingslast; kann bei Radfahrten ohne Wattmesser und ohne Puls fehlen
 * @param tssFromHeartRate true, wenn der TSS nur aus dem Puls geschätzt ist (hrTSS)
 * @param tssFromDuration  true, wenn der TSS nur grob aus Dauer und Sportart geschätzt ist
 * @param commute          bei Strava als "Pendeln" markiert – hakt nie ein geplantes Workout ab
 */
public record RecordedActivity(String id, Sport sport, LocalDateTime start, int movingSeconds, Double distanceMeters,
		Integer tss, boolean tssFromHeartRate, boolean tssFromDuration, boolean commute) {

	public static final String STRAVA_PREFIX = "strava-";

	/** Zwei Einheiten gelten als dieselbe, wenn sie höchstens so weit auseinander starten */
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

	public boolean isRide() {
		return sport == Sport.RIDE;
	}

	/** Dieselbe Einheit, z. B. vom Wahoo sowohl an intervals.icu als auch an Strava geschickt */
	public boolean isSameActivityAs(RecordedActivity other) {
		return sport == other.sport && Duration.between(start, other.start).abs().compareTo(SAME_RIDE) <= 0;
	}

	/** Startet eine Einheit aus der Dauer geschätzt, wenn sonst keine Trainingslast bekannt ist */
	public static RecordedActivity withEstimateIfMissing(String id, Sport sport, LocalDateTime start, int movingSeconds,
			Double distanceMeters, Integer tss, boolean tssFromHeartRate, boolean commute) {
		if (tss == null && sport.estimateTss(movingSeconds) != null) {
			return new RecordedActivity(id, sport, start, movingSeconds, distanceMeters,
					sport.estimateTss(movingSeconds), false, true, commute);
		}
		return new RecordedActivity(id, sport, start, movingSeconds, distanceMeters, tss, tssFromHeartRate, false,
				commute);
	}

	public static boolean isFromStrava(String id) {
		return id != null && id.startsWith(STRAVA_PREFIX);
	}

	/** Link zur Fahrt auf Strava, null bei anderen Quellen */
	public static String stravaUrl(String id) {
		return isFromStrava(id) ? "https://www.strava.com/activities/" + id.substring(STRAVA_PREFIX.length()) : null;
	}

}
