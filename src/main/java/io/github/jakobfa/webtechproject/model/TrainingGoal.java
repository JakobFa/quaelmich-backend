package io.github.jakobfa.webtechproject.model;

/**
 * Worauf trainiert wird: auf ein Event hin (mit Datum, Aufbau, Spitze und Tapering) oder auf ein Ziel ohne
 * Termin – dann läuft der Plan in wiederkehrenden 8-Wochen-Zyklen weiter.
 */
public enum TrainingGoal {

	/** auf ein bestimmtes Event hin */
	EVENT,
	/** FTP steigern: Sweet Spot und Schwelle, später VO2max */
	FTP,
	/** Ausdauer: lange Ausfahrten mit Tempo, wenig Intensität */
	ENDURANCE,
	/** fit bleiben: ausgewogen und eher locker */
	FITNESS,
	/** schneller am Berg: lange Schwellen- und Over-Under-Intervalle */
	CLIMBING;

	public boolean needsEvent() {
		return this == EVENT;
	}

}
