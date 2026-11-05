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
 * @param dailyMinutes   je vergangenem Tag (vor heute) die Minuten, die für die Belastung zählen: das Gefahrene, bei
 *                       Auslassen ohne Krankheit mindestens das Geplante. Daraus rechnet der Plan mit gleitenden
 *                       Zeitfenstern statt mit Kalenderwochen.
 * @param dataStart      ab diesem Tag gibt es Daten (Plan- oder erste Fahrt); davor ist nichts bekannt
 * @param fixedMinutes   Minuten der schon feststehenden Einheiten ab heute (selbst getauscht oder angepasst, abgehakt,
 *                       ausgefallen): Sie bleiben, wie sie sind, und zählen mit ihren echten Minuten in die Fenster
 * @param doneHard       harte Einheiten, die in der laufenden Woche vor heute schon gefahren wurden: Sie zählen
 *                       zu den harten Einheiten der Woche, und der Tag danach bleibt locker
 * @param recoveryDays   vergangene Tage in Erholungswochen: sie zählen nicht für den Schnitt, sonst bremst jede
 *                       Erholungswoche den Aufbau danach aus
 * @param load           Belastung (TSS) je vergangenem Tag und die Zusatzfahrten (außerhalb des Plans) – für die
 *                       Obergrenze der Belastung und um Platz für regelmäßige Zusatzfahrten (z. B. Pendeln) zu lassen
 */
public record PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday,
		int reentryWeeks, LocalDate today, Week currentWeek, Map<LocalDate, Integer> dailyMinutes,
		LocalDate dataStart, java.util.Set<LocalDate> recoveryDays, Map<LocalDate, Integer> fixedMinutes,
		DoneHard doneHard, Load load) {

	/** Ohne Wissen über vergangene Wochen: der Plan richtet sich allein nach dem Kalender */
	public static final PlanHistory NONE = new PlanHistory(null, Map.of(), null, 0, null, null, Map.of(), null, java.util.Set.of(), Map.of(),
			DoneHard.NONE);

	/**
	 * Belastung je vergangenem Tag (alle Fahrten und Sportarten, bei Auslassen ohne Krankheit mindestens ein Teil des
	 * Geplanten – wie bei den Minuten) und die Zusatzfahrten außerhalb des Plans (Minuten nur Rad, TSS alle Sportarten).
	 */
	public record Load(Map<LocalDate, Integer> dailyTss, Map<LocalDate, Integer> extraMinutes,
			Map<LocalDate, Integer> extraTss) {
		public static final Load NONE = new Load(Map.of(), Map.of(), Map.of());
	}

	public PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday, int reentryWeeks,
			LocalDate today, Week currentWeek, Map<LocalDate, Integer> dailyMinutes, LocalDate dataStart,
			java.util.Set<LocalDate> recoveryDays, Map<LocalDate, Integer> fixedMinutes, DoneHard doneHard) {
		this(thisMonday, weeks, reentryMonday, reentryWeeks, today, currentWeek, dailyMinutes, dataStart, recoveryDays,
				fixedMinutes, doneHard, Load.NONE);
	}

	/** Dieselbe Geschichte mit Belastung und Zusatzfahrten */
	public PlanHistory withLoad(Load load) {
		return new PlanHistory(thisMonday, weeks, reentryMonday, reentryWeeks, today, currentWeek, dailyMinutes,
				dataStart, recoveryDays, fixedMinutes, doneHard, load);
	}

	/** Wurde die Belastungswoche davor zu weniger als diesem Anteil gefahren, entfällt die Erholungswoche */
	static final double MIN_FULFILMENT = 0.7;

	/**
	 * Wurde in der Woche davor deutlich mehr Belastung (TSS) gefahren als geplant, wächst der Umfang nicht weiter
	 * und es gibt eine harte Einheit weniger – auch wenn die Minuten gepasst haben (zu hart gefahren).
	 */
	static final double OVERREACH_TSS_RATIO = 1.25;
	/** Über so viele zurückliegende Wochen wird die Erfüllung gemittelt ... */
	static final int RECENT_WEEKS = 3;
	/** ... und liegt der Schnitt darunter, entfällt die Erholungswoche ebenfalls (man hat nicht voll trainiert) */
	static final double MIN_RECENT_FULFILMENT = 0.85;
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
		this(thisMonday, weeks, reentryMonday, reentryWeeks, null, null, Map.of(), null, java.util.Set.of(), Map.of(),
				DoneHard.NONE);
	}

	public PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday, int reentryWeeks,
			LocalDate today, Week currentWeek) {
		this(thisMonday, weeks, reentryMonday, reentryWeeks, today, currentWeek, Map.of(), null, java.util.Set.of(),
				Map.of(), DoneHard.NONE);
	}

	public PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday, int reentryWeeks,
			LocalDate today, Week currentWeek, Map<LocalDate, Integer> dailyMinutes, LocalDate dataStart) {
		this(thisMonday, weeks, reentryMonday, reentryWeeks, today, currentWeek, dailyMinutes, dataStart,
				java.util.Set.of(), Map.of(), DoneHard.NONE);
	}

	public PlanHistory(LocalDate thisMonday, Map<LocalDate, Week> weeks, LocalDate reentryMonday, int reentryWeeks,
			LocalDate today, Week currentWeek, Map<LocalDate, Integer> dailyMinutes, LocalDate dataStart,
			java.util.Set<LocalDate> recoveryDays, Map<LocalDate, Integer> fixedMinutes) {
		this(thisMonday, weeks, reentryMonday, reentryWeeks, today, currentWeek, dailyMinutes, dataStart, recoveryDays,
				fixedMinutes, DoneHard.NONE);
	}

	/**
	 * Die harten Einheiten dieser Woche vor heute (Tage und Arten) und die schon feststehenden FTP-Tests ab heute
	 * (selbst gewählt): Der Tag vor einem Test bleibt frei.
	 */
	public record DoneHard(java.util.Set<LocalDate> days,
			java.util.List<io.github.jakobfa.webtechproject.model.WorkoutType> types,
			java.util.Set<LocalDate> upcomingFtpTestDays) {
		public DoneHard(java.util.Set<LocalDate> days,
				java.util.List<io.github.jakobfa.webtechproject.model.WorkoutType> types) {
			this(days, types, java.util.Set.of());
		}

		public static final DoneHard NONE = new DoneHard(java.util.Set.of(), java.util.List.of(), java.util.Set.of());
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

	/**
	 * Durchschnittliche Erfüllung der letzten Wochen vor diesem Montag (nur Wochen mit Plan), null ohne Angaben. Die
	 * Startwoche eines Plans zählt nur teilweise mit und wird deshalb nicht mitgerechnet, wenn sie die einzige ist.
	 */
	Double averageFulfilment(LocalDate monday, int weeksBack) {
		var fulfilments = java.util.stream.IntStream.rangeClosed(1, weeksBack)
				.mapToObj(back -> weeks.get(monday.minusWeeks(back)))
				.filter(week -> week != null && week.plannedMinutes() > 0)
				.map(Week::fulfilment).toList();
		return fulfilments.size() < 2 ? null
				: fulfilments.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
	}

	/** Ist das die laufende Woche, für die Ist-Werte bis heute vorliegen? */
	boolean isCurrentWeek(LocalDate monday) {
		return !isEmpty() && today != null && currentWeek != null && monday.equals(thisMonday);
	}
}
