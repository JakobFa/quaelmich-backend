package io.github.jakobfa.webtechproject.model;

/**
 * Trainingsart eines Workouts, angelehnt an die Leistungszonen nach Coggan.
 */
public enum WorkoutType {

	RECOVERY,
	ENDURANCE,
	LONG_RIDE,
	TEMPO,
	SWEET_SPOT,
	THRESHOLD,
	OVER_UNDER,
	VO2MAX,
	ANAEROBIC,
	SPRINT,
	FTP_TEST,
	/** Krafttraining – keine Radeinheit: keine .fit-Datei, kein Abhaken durch Fahrten */
	STRENGTH

}
