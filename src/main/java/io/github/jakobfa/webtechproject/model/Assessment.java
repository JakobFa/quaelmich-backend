package io.github.jakobfa.webtechproject.model;

/**
 * Einschätzung einer gefahrenen Einheit im Vergleich zum geplanten Workout.
 */
public enum Assessment {

	/** deutlich weniger Belastung als geplant (unter 80 %) */
	EASIER,
	/** im Rahmen (80–120 %) */
	AS_PLANNED,
	/** spürbar mehr (über 120 %) */
	HARDER,
	/** viel mehr (über 150 %) */
	MUCH_HARDER;

	/**
	 * Vergleicht die Trainingslast (TSS). Fehlt der TSS der Fahrt, wird ersatzweise die Dauer verglichen.
	 */
	public static Assessment of(int plannedTss, Integer actualTss, int plannedMinutes, Integer actualMinutes) {
		double ratio;
		if (actualTss != null && plannedTss > 0) {
			ratio = (double) actualTss / plannedTss;
		} else if (actualMinutes != null && plannedMinutes > 0) {
			ratio = (double) actualMinutes / plannedMinutes;
		} else {
			return AS_PLANNED;
		}
		if (ratio > 1.5) {
			return MUCH_HARDER;
		}
		if (ratio > 1.2) {
			return HARDER;
		}
		if (ratio < 0.8) {
			return EASIER;
		}
		return AS_PLANNED;
	}

	public boolean isHarder() {
		return this == HARDER || this == MUCH_HARDER;
	}

}
