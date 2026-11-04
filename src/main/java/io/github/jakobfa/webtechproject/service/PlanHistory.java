package io.github.jakobfa.webtechproject.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Was in den vergangenen Wochen wirklich passiert ist – der Plan richtet sich danach, nicht nur nach dem Kalender.
 *
 * @param thisMonday     Montag der laufenden Woche; ab hier wird (neu) geplant, davor zählt, was gefahren wurde
 * @param weeks          vergangene Wochen (Schlüssel: Montag) mit geplanten und gefahrenen Minuten und Belastung
 * @param reentryMonday  Montag der Woche, in der nach einer längeren Krankheit wieder trainiert wird (oder null)
 * @param reentryWeeks   so viele Wochen ab da sind lockerer Wiedereinstieg (ohne harte Einheiten)
 * @param today          heute; die laufende Woche wird nur noch ab hier geplant (null = keine Angabe)
 * @param currentWeek    was in der laufenden Woche schon gefahren wurde (actual*) und was die Woche insgesamt
 *                       vorsieht (planned*); null = keine Angabe
 */
public record PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday,
		int reentryWeeks, LocalDate today, Week currentWeek) {

	/** Ohne Wissen über vergangene Wochen: der Plan richtet sich allein nach dem Kalender */
	public static final PlanHistory NONE = new PlanHistory(null, Map.of(), null, 0, null, null);

	/** Wurde die Belastungswoche davor zu weniger als diesem Anteil gefahren, entfällt die Erholungswoche */
	static final double MIN_FULFILMENT = 0.7;

	/**
	 * Wurde in der Woche davor deutlich mehr Belastung (TSS) gefahren als geplant, wächst der Umfang nicht weiter
	 * und es gibt eine harte Einheit weniger – auch wenn die Minuten gepasst haben (zu hart gefahren).
	 */
	static final double OVERREACH_TSS_RATIO = 1.25;
	/** Unter dieser geplanten Belastung sind Abweichungen Rauschen (z. B. fast leere Woche) */
	static final int MIN_PLANNED_TSS_FOR_RATIO = 100;

	/**
	 * Eine Woche: geplante und gefahrene Minuten, geplante und gefahrene Belastung (TSS) und Tage mit
	 * Krankheit/Verletzung.
	 */
	public record Week(int plannedMinutes, int actualMinutes, int sickDays, int plannedTss, int actualTss) {

		public Week(int plannedMinutes, int actualMinutes, int sickDays) {
			this(plannedMinutes, actualMinutes, sickDays, 0, 0);
		}

		/** Anteil des Geplanten, der gefahren wurde (1 = alles; eine Woche ohne Plan zählt als erfüllt) */
		public double fulfilment() {
			return plannedMinutes <= 0 ? 1.0 : (double) actualMinutes / plannedMinutes;
		}

		/** Gefahrene im Verhältnis zur geplanten Belastung; 1 ohne verlässliche Angaben */
		public double tssRatio() {
			return plannedTss < MIN_PLANNED_TSS_FOR_RATIO ? 1.0 : (double) actualTss / plannedTss;
		}

		/** War die Woche deutlich härter als geplant? */
		public boolean overreached() {
			return tssRatio() > OVERREACH_TSS_RATIO;
		}
	}

	public PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday, int reentryWeeks) {
		this(thisMonday, weeks, reentryMonday, reentryWeeks, null, null);
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

	/** Ist das die laufende Woche, für die Ist-Werte bis heute vorliegen? */
	boolean isCurrentWeek(LocalDate monday) {
		return !isEmpty() && today != null && currentWeek != null && monday.equals(thisMonday);
	}
}
