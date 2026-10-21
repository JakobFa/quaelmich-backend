package io.github.jakobfa.webtechproject.model;

import java.util.Set;

/**
 * Sportart einer gemachten Einheit. Nur Radfahrten haken geplante Workouts ab,
 * alle anderen zählen als zusätzliche Belastung.
 */
public enum Sport {
	RIDE(0), RUN(60), SWIM(45), WALK(20), HIKE(35), STRENGTH(35), OTHER(30);

	/** Grobe Trainingslast pro Stunde, wenn weder TSS noch Puls bekannt sind (bei Radfahrten keine Schätzung) */
	private final int estimatedTssPerHour;

	Sport(int estimatedTssPerHour) {
		this.estimatedTssPerHour = estimatedTssPerHour;
	}

	private static final Set<String> RIDES = Set.of("Ride", "VirtualRide", "GravelRide", "MountainBikeRide");
	private static final Set<String> RUNS = Set.of("Run", "TrailRun", "VirtualRun");
	private static final Set<String> STRENGTH_TYPES = Set.of("WeightTraining", "Crossfit", "Workout",
			"HighIntensityIntervalTraining");

	/** Für Hinweistexte, z. B. "beim Laufen" */
	public String inGerman() {
		return switch (this) {
			case RIDE -> "auf dem Rad";
			case RUN -> "beim Laufen";
			case SWIM -> "beim Schwimmen";
			case WALK -> "beim Gehen";
			case HIKE -> "beim Wandern";
			case STRENGTH -> "beim Krafttraining";
			case OTHER -> "beim Sport";
		};
	}

	/** Aus dem Aktivitätstyp von Strava bzw. intervals.icu (beide nutzen dieselben Namen) */
	public static Sport fromActivityType(String type) {
		if (type == null) {
			return OTHER;
		}
		if (RIDES.contains(type)) {
			return RIDE;
		}
		if (RUNS.contains(type)) {
			return RUN;
		}
		if (STRENGTH_TYPES.contains(type)) {
			return STRENGTH;
		}
		return switch (type) {
			case "Swim" -> SWIM;
			case "Walk" -> WALK;
			case "Hike" -> HIKE;
			default -> OTHER; // z. B. E-Bike, Rudern, Ski, Yoga
		};
	}

	/** Geschätzte Trainingslast aus der Dauer; null bei Radfahrten (dort zählt die Dauer allein nicht als TSS) */
	public Integer estimateTss(int movingSeconds) {
		if (this == RIDE) {
			return null;
		}
		return (int) Math.round(movingSeconds / 3600.0 * estimatedTssPerHour);
	}

}
