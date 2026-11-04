package io.github.jakobfa.webtechproject.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Was in den vergangenen Wochen wirklich passiert ist – der Plan richtet sich danach, nicht nur nach dem Kalender.
 *
 * @param thisMonday     Montag der laufenden Woche; ab hier wird (neu) geplant, davor zählt, was gefahren wurde
 * @param weeks          vergangene Wochen (Schlüssel: Montag) mit geplanten und gefahrenen Minuten
 * @param reentryMonday  Montag der Woche, in der nach einer längeren Krankheit wieder trainiert wird (oder null)
 * @param reentryWeeks   so viele Wochen ab da sind lockerer Wiedereinstieg (ohne harte Einheiten)
 */
public record PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday,
		int reentryWeeks) {

	/** Ohne Wissen über vergangene Wochen: der Plan richtet sich allein nach dem Kalender */
	public static final PlanHistory NONE = new PlanHistory(null, Map.of(), null, 0);

	/** Wurde die Belastungswoche davor zu weniger als diesem Anteil gefahren, entfällt die Erholungswoche */
	static final double MIN_FULFILMENT = 0.7;

	/** Eine vergangene Woche: geplante und gefahrene Minuten, Tage mit Krankheit/Verletzung/Pause */
	public record Week(int plannedMinutes, int actualMinutes, int sickDays) {

		/** Anteil des Geplanten, der gefahren wurde (1 = alles; eine Woche ohne Plan zählt als erfüllt) */
		public double fulfilment() {
			return plannedMinutes <= 0 ? 1.0 : (double) actualMinutes / plannedMinutes;
		}
	}

	public boolean isEmpty() {
		return thisMonday == null;
	}

	/** Ist diese Woche (noch zu planen) ein Wiedereinstiegs-Woche nach Krankheit? */
	boolean isReentryWeek(LocalDate monday) {
		return reentryMonday != null && !monday.isBefore(reentryMonday)
				&& monday.isBefore(reentryMonday.plusWeeks(reentryWeeks));
	}

	/** Gilt die Anpassung an die Ist-Werte für diese Woche? Nur für laufende und kommende Wochen. */
	boolean applies(LocalDate monday) {
		return !isEmpty() && !monday.isBefore(thisMonday);
	}

	/** Die vergangene Woche vor diesem Montag, falls bekannt */
	Week before(LocalDate monday) {
		return weeks.get(monday.minusWeeks(1));
	}
}
