package io.github.jakobfa.webtechproject.model;

/** Warum ein geplantes Workout ausgefallen ist – auch "nichts gemacht" ist eine Rückmeldung. */
public enum SkipReason {
	NO_TIME, SICK, TIRED, INJURED, OTHER,
	/** vom Nutzer pausierter Zeitraum */
	PAUSE;

	/** Krank oder verletzt → das nächste harte Training wird sanfter */
	public boolean needsGentleReturn() {
		return this == SICK || this == INJURED;
	}

	public String inGerman() {
		return switch (this) {
			case NO_TIME -> "keine Zeit";
			case SICK -> "krank";
			case TIRED -> "zu müde";
			case INJURED -> "verletzt";
			case OTHER -> "anderer Grund";
			case PAUSE -> "Pause";
		};
	}
}
