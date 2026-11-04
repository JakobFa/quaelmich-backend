package io.github.jakobfa.webtechproject.service;

import java.util.List;

/**
 * Wie ein erfundener Fahrer mit seinem Plan umgeht – erfundene Verhaltensmuster, keine echten Nutzerdaten. Jeder Typ
 * wird mit jedem Profil aus {@link PlanReport#profiles()} kombiniert.
 *
 * @param missRate          Anteil geplanter Einheiten, die ohne Grund ausfallen
 * @param weekdayMissRate   zusätzlich unter der Woche (Mo–Fr) ausgefallen
 * @param compliance        im Schnitt so viel der geplanten Dauer gefahren (1 = genau)
 * @param commuteMinutes    Zusatzfahrten (locker) an jedem Werktag
 * @param preHistory        Minuten pro Woche in den 6 Wochen vor dem Plan, als Anteil des Üblichen (älteste zuerst)
 * @param vacationWeek      ab dieser Planwoche Urlaub mit viel lockerem Fahren (-1 = nie)
 * @param vacationWeeks     so viele Wochen
 * @param raceWeek          in dieser Planwoche samstags ein langes Rennen statt des Plans (-1 = nie)
 * @param illnessWeek       ab Montag dieser Planwoche krank (-1 = nur zufällig kurz, wie bisher)
 * @param illnessDays       so viele Tage
 * @param travelWeek        in dieser Planwoche keine Zeit (-1 = nie)
 * @param shiftWork         jede zweite Woche nur halb so viel Zeit, die anderen etwas mehr
 */
record RiderType(String name, double missRate, double weekdayMissRate, double compliance, int commuteMinutes,
		double[] preHistory, int vacationWeek, int vacationWeeks, int raceWeek, int illnessWeek, int illnessDays,
		int travelWeek, boolean shiftWork) {

	private static final double[] USUAL = { 1, 1, 1, 1, 1, 1 };

	static RiderType baseline() {
		return new RiderType("planmäßig", 0.07, 0, 0.97, 0, USUAL, -1, 0, -1, -1, 0, -1, false);
	}

	static List<RiderType> all() {
		RiderType b = baseline();
		return List.of(b,
				new RiderType("Übererfüller (+25 %)", 0.03, 0, 1.25, 0, USUAL, -1, 0, -1, -1, 0, -1, false),
				new RiderType("Pendler (2 × 35 min an Werktagen)", 0.07, 0, 0.97, 70, USUAL, -1, 0, -1, -1, 0, -1,
						false),
				new RiderType("Wochenend-Fahrer (unter der Woche oft nicht)", 0.05, 0.45, 0.95, 0, USUAL, -1, 0, -1,
						-1, 0, -1, false),
				new RiderType("Urlaub mit viel Fahren (Woche 6–7)", 0.07, 0, 0.97, 0, USUAL, 5, 2, -1, -1, 0, -1,
						false),
				new RiderType("Rennen mittendrin (Woche 8, 10 h)", 0.07, 0, 0.97, 0, USUAL, -1, 0, 7, -1, 0, -1,
						false),
				new RiderType("Start nach Off-Season", 0.07, 0, 0.97, 0, new double[] { 1.1, 1.1, 1, 0.35, 0.3, 0.3 },
						-1, 0, -1, -1, 0, -1, false),
				new RiderType("Lange Krankheit (Woche 7, 12 Tage)", 0.07, 0, 0.97, 0, USUAL, -1, 0, -1, 6, 12, -1,
						false),
				new RiderType("Reisewoche ohne Zeit (Woche 5)", 0.07, 0, 0.97, 0, USUAL, -1, 0, -1, -1, 0, 4, false),
				new RiderType("Schichtdienst (Zeit wechselt wöchentlich)", 0.08, 0, 0.95, 0, USUAL, -1, 0, -1, -1, 0,
						-1, true));
	}

	boolean isVacation(int week) {
		return vacationWeek >= 0 && week >= vacationWeek && week < vacationWeek + vacationWeeks;
	}
}
