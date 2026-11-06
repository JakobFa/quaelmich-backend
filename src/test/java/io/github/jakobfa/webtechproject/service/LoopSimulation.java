package io.github.jakobfa.webtechproject.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingGoal;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Simulation im Wochen-Kreislauf wie in der App: Jeden Montag wird der Plan aus dem bisher Gefahrenen neu berechnet
 * (PlanHistory wie in TrainingPlanService), dann fährt der erfundene Fahrer die Woche nach seinem {@link RiderType}.
 * So laufen auch Wochendeckel, Trainingsgedächtnis, entfallende/vorgezogene Erholungswochen, der neu begonnene
 * Rhythmus und der Wiedereinstieg nach Krankheit. Der Zufall ist je Profil und Typ fest: jeden Tag dieselbe Simulation.
 */
final class LoopSimulation {

	/** Harte Grenzen (der Lauf scheitert) */
	static final int HARD_MIN_TSB = PlanSimulation.HARD_MIN_TSB;
	static final int HARD_MAX_LOAD_WEEKS = 6;
	/** Warnungen */
	static final int WARN_TSB = PlanSimulation.WARN_TSB;
	static final double WARN_WEEKLY_CTL_RAMP = PlanSimulation.WARN_WEEKLY_CTL_RAMP;
	static final int WARN_MAX_LOAD_WEEKS = 4;
	static final double WARN_PLANNED_ACWR = 1.5;
	/** Zurück auf eine zuletzt schon gefahrene Woche ist kein Lastsprung: Gewarnt wird erst darüber */
	static final double WARN_ABOVE_BEST_WEEK = 1.1;
	static final int BEST_WEEK_LOOKBACK = 6;
	static final int WARN_SAME_WORKOUT_PER_WEEK = 4;
	static final int BIG_DAY_MINUTES = 180;
	static final int OPEN_WEEKS = 16;
	/** Locker gefahren: TSS pro Minute (IF ca. 0,6); ein Rennen (IF ca. 0,75) */
	static final double EASY_TSS_PER_MINUTE = 0.6;
	static final double RACE_TSS_PER_MINUTE = 0.95;
	static final int RACE_MINUTES = 600;
	static final int VACATION_MINUTES_PER_DAY = 240;
	/** Vorgeschichte: gemischtes Training, etwas mehr als locker */
	static final double PRE_HISTORY_TSS_PER_MINUTE = 0.7;

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);
	private static final PlanAdaptationService ADAPTATION = new PlanAdaptationService(LIBRARY);

	private LoopSimulation() {
	}

	record Week(LocalDate monday, String phase, int plannedMinutes, int actualMinutes, int plannedTss, int actualTss,
			double ctl, double ctlRamp, double minTsb) {
	}

	/**
	 * @param tssByDay  gefahrene Belastung je Tag, mit der Vorgeschichte (für das Leistungsmodell)
	 * @param targetDay der Tag, auf den es ankommt: der Event-Tag, sonst der letzte simulierte Tag
	 * @param firstDay  der erste Plantag
	 */
	record Result(String profile, String type, List<Week> weeks, List<String> hard, List<String> warnings,
			double eventDayTsb, int recoveryWeeks, int maxLoadWeeksInRow, Map<LocalDate, Integer> tssByDay,
			LocalDate firstDay, LocalDate targetDay) {

		String key() {
			return profile + " | " + type;
		}
	}

	static Result run(PlanReport.Profile profile, RiderType type) {
		Athlete athlete = PlanReport.athleteOf(profile);
		Random random = new Random(Objects.hash(profile.name(), type.name()));
		LocalDate start = PlanReport.START;
		boolean event = profile.goal() == TrainingGoal.EVENT;
		int weeks = event ? profile.weeks() + 1 : OPEN_WEEKS;
		LocalDate horizon = start.plusWeeks(OPEN_WEEKS + 4L);
		LocalDate eventDate = event ? athlete.getEvent().getDate() : null;
		WeeklyAvailability usual = athlete.getAvailability();

		// Zeit je Woche: Reisewoche ohne Zeit, Schichtdienst im Wechsel
		Map<LocalDate, WeeklyAvailability> weekAvailability = new HashMap<>();
		for (int w = 0; w < weeks; w++) {
			double factor = w == type.travelWeek() ? 0 : type.shiftWork() ? (w % 2 == 1 ? 0.5 : 1.15) : 1;
			if (factor != 1) {
				weekAvailability.put(start.plusWeeks(w), scaled(usual, factor));
			}
		}

		// Vorgeschichte: 6 Wochen vor dem Plan
		Map<LocalDate, Integer> actual = new TreeMap<>();
		Map<LocalDate, Integer> floor = new HashMap<>();
		double usualWeekly = usual.totalHours() * 60 * 0.65;
		for (int back = 1; back <= 42; back++) {
			int week = 5 - (back - 1) / 7; // 0 = älteste
			actual.put(start.minusDays(back), (int) (usualWeekly * type.preHistory()[week] / 7));
		}
		Map<LocalDate, Integer> actualTssByDay = new HashMap<>();
		actual.forEach((day, minutes) -> actualTssByDay.put(day, (int) (minutes * PRE_HISTORY_TSS_PER_MINUTE)));
		// Pendeln (außerhalb des Plans, wie ExtraRide in der App) auch schon in der Vorgeschichte
		Map<LocalDate, Integer> floorTss = new HashMap<>();
		if (type.commuteMinutes() > 0) {
			for (int back = 1; back <= 42; back++) {
				LocalDate day = start.minusDays(back);
				if (day.getDayOfWeek().getValue() <= 5) {
					// auch in Minuten und Belastung der Vorgeschichte – wie in echten Daten
					actual.merge(day, type.commuteMinutes(), Integer::sum);
					actualTssByDay.merge(day, (int) (type.commuteMinutes() * EASY_TSS_PER_MINUTE), Integer::sum);
				}
			}
		}
		// Fitness und Ermüdung entstehen aus der Vorgeschichte (Start auf Level-Niveau, dann 6 Wochen Training)
		double ctl = PlanSimulation.startFitness(profile.level());
		double atl = ctl;
		for (int back = 42; back >= 1; back--) {
			int tss = actualTssByDay.get(start.minusDays(back));
			ctl += (tss - ctl) / 42.0;
			atl += (tss - atl) / 7.0;
		}
		double ctl0 = ctl;

		Map<LocalDate, PlanHistory.Week> pastWeeks = new TreeMap<>();
		Set<LocalDate> recoveryDays = new HashSet<>();
		Set<LocalDate> sickDays = sickDays(type, start, weeks, random);
		LocalDate reentryMonday = null;
		int reentryWeeks = 0;

		List<String> hard = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<Week> result = new ArrayList<>();
		List<Phase> phaseList = new ArrayList<>();
		Map<LocalDate, Integer> plannedOn = new HashMap<>();
		Set<LocalDate> reentry = new HashSet<>();
		double eventDayTsb = Double.NaN;
		double previousCtl = ctl;
		int sickStreak = 0;

		for (int w = 0; w < weeks; w++) {
			LocalDate monday = start.plusWeeks(w);
			Map<LocalDate, Integer> daily = new HashMap<>(actual);
			floor.forEach((day, minutes) -> daily.merge(day, minutes, Math::max));
			LocalDate dataStart = start.minusDays(42);
			Map<LocalDate, Integer> dailyTss = new HashMap<>(actualTssByDay);
			floorTss.forEach((day, tss) -> dailyTss.merge(day, tss, Math::max));
			PlanHistory history = new PlanHistory(monday, pastWeeks, reentryMonday, reentryWeeks, monday,
					new PlanHistory.Week(0, 0, 0, 0, 0), daily, dataStart, recoveryDays, Map.of())
					.withLoad(new PlanHistory.Load(dailyTss));
			List<PlannedWorkout> plan = GENERATOR.generateSeason(athlete, start, weekAvailability, List.of(), horizon,
					history);
			List<PlannedWorkout> week = new ArrayList<>(plan.stream()
					.filter(p -> !p.getDate().isBefore(monday) && p.getDate().isBefore(monday.plusWeeks(1)))
					.sorted(java.util.Comparator.comparing(PlannedWorkout::getDate)).toList());
			Phase phase = week.isEmpty() ? null : week.getFirst().getPhase();
			phaseList.add(phase);
			if (history.isReentryWeek(monday)) {
				reentry.add(monday);
			}

			// Prüfungen am Plan dieser Woche (vor dem Fahren)
			WeeklyAvailability availability = weekAvailability.getOrDefault(monday, usual);
			Map<String, Integer> sameTitle = new HashMap<>();
			int plannedMinutes = 0;
			int plannedTss = 0;
			for (PlannedWorkout planned : week) {
				Workout workout = resolve(planned);
				plannedMinutes += workout.getDurationInMinutes();
				plannedTss += workout.getTss();
				int available = (int) Math.round(availability.hoursOn(planned.getDate().getDayOfWeek()) * 60);
				if (workout.getDurationInMinutes() > available) {
					hard.add("%s: %d min geplant, aber nur %d min Zeit (%s)".formatted(planned.getDate(),
							workout.getDurationInMinutes(), available, workout.getTitle()));
				}
				sameTitle.merge(workout.getTitle(), 1, Integer::sum);
			}
			sameTitle.forEach((title, count) -> {
				if (count >= WARN_SAME_WORKOUT_PER_WEEK) {
					warnings.add("Woche ab %s: %d × \"%s\" – eintönig".formatted(monday, count, title));
				}
			});
			double recentActualTss = averageWeeklyTss(actualTssByDay, monday, 4);
			int bestRecentWeek = bestWeeklyTss(actualTssByDay, monday, BEST_WEEK_LOOKBACK);
			if (w >= 2 && recentActualTss > 50 && plannedTss > recentActualTss * WARN_PLANNED_ACWR
					&& plannedTss > bestRecentWeek * WARN_ABOVE_BEST_WEEK && !history.isReentryWeek(monday)) {
				warnings.add("Woche ab %s: geplant %d TSS, %.1f-mal so viel wie zuletzt gefahren (%.0f/Woche, beste Woche %d)"
						.formatted(monday, plannedTss, plannedTss / recentActualTss, recentActualTss, bestRecentWeek));
			}

			// Die Woche fahren
			int actualMinutes = 0;
			int actualTss = 0;
			int weekSickDays = 0;
			double minTsb = 999;
			int previousDayPlanned = plannedOn.getOrDefault(monday.minusDays(1), 0);
			for (int d = 0; d < 7; d++) {
				LocalDate day = monday.plusDays(d);
				PlannedWorkout planned = week.stream().filter(p -> p.getDate().equals(day)).findFirst().orElse(null);
				Workout workout = planned == null ? null : resolve(planned);
				boolean weekday = day.getDayOfWeek().getValue() <= 5;
				int minutes = 0;
				int tss = 0;
				if (planned != null && isHard(workout) && previousDayPlanned >= BIG_DAY_MINUTES) {
					warnings.add("%s: harte Einheit (%s) am Tag nach %d min geplant".formatted(day, workout.getTitle(),
							previousDayPlanned));
				}
				previousDayPlanned = workout == null ? 0 : workout.getDurationInMinutes();
				plannedOn.put(day, previousDayPlanned);
				if (sickDays.contains(day)) {
					weekSickDays += planned != null ? 1 : 0;
					sickStreak++;
				} else {
					if (sickStreak >= AbsenceService.REENTRY_AFTER_DAYS) {
						int reWeeks = sickStreak >= AbsenceService.LONG_ILLNESS_DAYS ? 2 : 1;
						if (day.getDayOfWeek().getValue() >= DayOfWeek.FRIDAY.getValue()) {
							reWeeks++;
						}
						reentryMonday = monday;
						reentryWeeks = reWeeks;
					}
					sickStreak = 0;
					if (w == type.raceWeek() && day.getDayOfWeek() == DayOfWeek.SATURDAY) {
						minutes = RACE_MINUTES;
						tss = (int) (RACE_MINUTES * RACE_TSS_PER_MINUTE);
					} else if (type.isVacation(w)) {
						minutes = VACATION_MINUTES_PER_DAY;
						tss = (int) (minutes * EASY_TSS_PER_MINUTE);
					} else if (planned != null) {
						boolean missed = random.nextDouble() < type.missRate()
								|| weekday && random.nextDouble() < type.weekdayMissRate();
						if (!missed) {
							double compliance = Math.clamp(type.compliance() + 0.12 * random.nextGaussian(), 0.5,
									1.5);
							minutes = (int) Math.round(workout.getDurationInMinutes() * compliance);
							tss = (int) Math.round(workout.getTss() * compliance * (0.9 + 0.25 * random.nextDouble()));
							planned.setCompleted(true);
							planned.setActivityId("sim-" + day);
							planned.setActualDurationMinutes(minutes);
							planned.setActualTss(tss);
							List<PlannedWorkout> upcoming = week.stream().filter(p -> p.getDate().isAfter(day))
									.toList();
							ADAPTATION.adaptAfter(planned, upcoming);
						} else {
							floor.put(day, (int) Math.min(workout.getDurationInMinutes() * 0.7, 140));
							floorTss.put(day, (int) Math.min(workout.getTss() * 0.7, 110));
						}
					}
					if (weekday && type.commuteMinutes() > 0 && w != type.travelWeek()) {
						minutes += type.commuteMinutes();
						tss += (int) (type.commuteMinutes() * EASY_TSS_PER_MINUTE);
					}
				}
				actual.put(day, minutes);
				actualTssByDay.put(day, tss);
				actualMinutes += minutes;
				actualTss += tss;
				double tsb = ctl - atl;
				minTsb = Math.min(minTsb, tsb);
				if (day.equals(eventDate)) {
					eventDayTsb = tsb;
				}
				ctl += (tss - ctl) / 42.0;
				atl += (tss - atl) / 7.0;
			}
			pastWeeks.put(monday, new PlanHistory.Week(plannedMinutes, actualMinutes, weekSickDays, plannedTss,
					actualTss));
			if (phase == Phase.RECOVERY) {
				monday.datesUntil(monday.plusWeeks(1)).forEach(recoveryDays::add);
			}
			double ramp = ctl - previousCtl;
			previousCtl = ctl;
			result.add(new Week(monday, phase == null ? "–" : phase.name(), plannedMinutes, actualMinutes, plannedTss,
					actualTss, ctl, ramp, minTsb > 900 ? 0 : minTsb));
			if (w >= 2 && ramp > WARN_WEEKLY_CTL_RAMP && !type.isVacation(w) && w != type.raceWeek()) {
				warnings.add("Woche ab %s: Fitness steigt um %.1f CTL (Grenze %.0f)".formatted(monday, ramp,
						WARN_WEEKLY_CTL_RAMP));
			}
			// selbst verursacht: Urlaub, Rennen (und die Woche danach) oder deutlich mehr als geplant gefahren
			boolean riderCaused = type.isVacation(w) || type.isVacation(w - 1) || w == type.raceWeek()
					|| w == type.raceWeek() + 1 || type.compliance() > 1.1 || type.commuteMinutes() >= 60;
			if (minTsb < HARD_MIN_TSB && !riderCaused) {
				hard.add("Woche ab %s: Frische bis %.0f TSB gefallen (Grenze %d)".formatted(monday, minTsb,
						HARD_MIN_TSB));
			} else if (minTsb < WARN_TSB) {
				warnings.add("Woche ab %s: Frische bis %.0f TSB gefallen (Warnung ab %d%s)".formatted(monday, minTsb,
						WARN_TSB, riderCaused ? ", selbst verursacht" : ""));
			}
		}

		int maxInRow = checkRhythm(phaseList, result, reentry, start, hard, warnings);
		checkEventDay(athlete, eventDayTsb, warnings);
		checkType(type, result, start, warnings);
		int recoveries = (int) phaseList.stream().filter(phase -> phase == Phase.RECOVERY).count();
		LocalDate targetDay = eventDate != null ? eventDate : start.plusWeeks(weeks).minusDays(1);
		return new Result(profile.name(), type.name(), result, hard, warnings, eventDayTsb, recoveries, maxInRow,
				new TreeMap<>(actualTssByDay), start, targetDay);
	}

	/**
	 * Belastungswochen am Stück, zwei Erholungswochen hintereinander, Erholung direkt vor dem Tapering. Eine Woche,
	 * die tatsächlich so ruhig war wie eine Erholungswoche (höchstens 75 % der 3 Wochen davor), zählt wie eine – so
	 * wertet es auch der Plan.
	 */
	private static int checkRhythm(List<Phase> phases, List<Week> weeks, Set<LocalDate> reentry, LocalDate start,
			List<String> hard, List<String> warnings) {
		int inRow = 0;
		int max = 0;
		for (int i = 0; i < phases.size(); i++) {
			Phase phase = phases.get(i);
			LocalDate monday = start.plusWeeks(i);
			boolean load = phase == Phase.BASE || phase == Phase.BUILD || phase == Phase.PEAK;
			if (load && i >= 1) {
				double before = weeks.subList(Math.max(0, i - 3), i).stream().mapToInt(Week::actualMinutes).average()
						.orElse(0);
				load = weeks.get(i).actualMinutes() > before * 0.75;
			}
			if (load) {
				inRow++;
				max = Math.max(max, inRow);
				if (inRow == HARD_MAX_LOAD_WEEKS + 1) {
					hard.add("Woche ab %s: %d Belastungswochen am Stück".formatted(monday, inRow));
				} else if (inRow == WARN_MAX_LOAD_WEEKS + 1) {
					warnings.add("Woche ab %s: %d Belastungswochen am Stück".formatted(monday, inRow));
				}
			} else {
				inRow = 0;
			}
			// drei Erholungswochen am Stück nie; zwei nur, wenn die erste nicht ruhig gefahren wurde (Warnung zum Prüfen).
			// Der lockere Wiedereinstieg nach Krankheit darf mehrere ruhige Wochen haben.
			boolean afterIllness = reentry.contains(monday) || reentry.contains(monday.minusWeeks(1))
					|| reentry.contains(monday.minusWeeks(2));
			if (afterIllness) {
				// nichts zu prüfen
			} else if (i > 1 && phase == Phase.RECOVERY && phases.get(i - 1) == Phase.RECOVERY
					&& phases.get(i - 2) == Phase.RECOVERY) {
				hard.add("Woche ab %s: drei Erholungswochen hintereinander".formatted(monday));
			} else if (i > 3 && phase == Phase.RECOVERY && phases.get(i - 1) == Phase.RECOVERY
					&& weeks.get(i - 1).actualMinutes() <= weeks.subList(i - 4, i - 1).stream()
							.mapToInt(Week::actualMinutes).average().orElse(0) * 0.8) {
				warnings.add("Woche ab %s: zwei Erholungswochen hintereinander, obwohl die erste ruhig gefahren wurde"
						.formatted(monday));
			}
			if (i + 1 < phases.size() && phase == Phase.RECOVERY && phases.get(i + 1) == Phase.TAPER) {
				warnings.add("Woche ab %s: Erholungswoche direkt vor dem Tapering".formatted(monday));
			}
		}
		return max;
	}

	/** Frische am Event-Tag passend zum Ziel */
	private static void checkEventDay(Athlete athlete, double tsb, List<String> warnings) {
		if (athlete.getEvent() == null || Double.isNaN(tsb)) {
			return;
		}
		EventGoal goal = athlete.getEvent().getGoal() == null ? EventGoal.GOOD_TIME : athlete.getEvent().getGoal();
		boolean ok = goal == EventGoal.FINISH ? tsb >= -10 : tsb >= -5 && tsb <= 25;
		if (!ok) {
			warnings.add("Event-Tag: Frische %.0f TSB passt nicht zum Ziel %s".formatted(tsb, goal));
		}
	}

	/** Was nach dem jeweiligen Ereignis zu erwarten ist */
	private static void checkType(RiderType type, List<Week> weeks, LocalDate start, List<String> warnings) {
		if (type.raceWeek() >= 0 && type.raceWeek() + 2 < weeks.size()) {
			expectRelief(weeks, type.raceWeek(), "nach dem Rennen", warnings);
		}
		if (type.vacationWeek() >= 0 && type.vacationWeek() + type.vacationWeeks() + 1 < weeks.size()) {
			expectRelief(weeks, type.vacationWeek() + type.vacationWeeks() - 1, "nach dem Urlaub", warnings);
		}
		// nach einer Krankheit, die einen Wiedereinstieg auslöst, und mit Wochen davor zum Vergleichen
		if (type.illnessWeek() >= 3 && type.illnessDays() >= AbsenceService.REENTRY_AFTER_DAYS) {
			int back = type.illnessWeek() + (type.illnessDays() + 6) / 7;
			if (back < weeks.size()) {
				Week week = weeks.get(back);
				int before = averagePlanned(weeks, type.illnessWeek());
				if (week.plannedMinutes() > before) {
					warnings.add("Woche ab %s: nach %d Tagen Krankheit %d min geplant, mehr als davor (%d)"
							.formatted(week.monday(), type.illnessDays(), week.plannedMinutes(), before));
				}
			}
		}
	}

	/** In den 2 Wochen nach Woche index: eine Erholungswoche oder höchstens 85 % des Geplanten davor */
	private static void expectRelief(List<Week> weeks, int index, String what, List<String> warnings) {
		int before = averagePlanned(weeks, index);
		boolean relief = false;
		for (int i = index + 1; i <= index + 2 && i < weeks.size(); i++) {
			Week week = weeks.get(i);
			relief |= week.phase().equals(Phase.RECOVERY.name()) || week.phase().equals(Phase.TAPER.name())
					|| week.plannedMinutes() <= before * 0.85;
		}
		if (!relief) {
			warnings.add("%s (Woche ab %s) keine Entlastung in den 2 Wochen danach".formatted(what,
					weeks.get(index).monday()));
		}
	}

	private static int averagePlanned(List<Week> weeks, int index) {
		return (int) weeks.subList(Math.max(0, index - 3), index).stream().mapToInt(Week::plannedMinutes).average()
				.orElse(0);
	}

	private static double averageWeeklyTss(Map<LocalDate, Integer> tssByDay, LocalDate monday, int weeks) {
		int sum = 0;
		for (int back = 1; back <= weeks * 7; back++) {
			sum += tssByDay.getOrDefault(monday.minusDays(back), 0);
		}
		return sum / (double) weeks;
	}

	/** Die stärkste der letzten Wochen vor monday */
	private static int bestWeeklyTss(Map<LocalDate, Integer> tssByDay, LocalDate monday, int weeks) {
		int best = 0;
		for (int week = 0; week < weeks; week++) {
			int sum = 0;
			for (int back = 1; back <= 7; back++) {
				sum += tssByDay.getOrDefault(monday.minusDays(week * 7L + back), 0);
			}
			best = Math.max(best, sum);
		}
		return best;
	}

	private static Set<LocalDate> sickDays(RiderType type, LocalDate start, int weeks, Random random) {
		Set<LocalDate> sick = new HashSet<>();
		if (type.illnessWeek() >= 0) {
			LocalDate from = start.plusWeeks(type.illnessWeek());
			for (int i = 0; i < type.illnessDays(); i++) {
				sick.add(from.plusDays(i));
			}
			return sick;
		}
		// wie bisher: ab und zu ein paar Tage krank
		for (LocalDate day = start; day.isBefore(start.plusWeeks(weeks)); day = day.plusDays(1)) {
			if (!sick.contains(day) && random.nextDouble() < 0.004) {
				int days = PlanSimulation.ILLNESS_DAYS_MIN
						+ random.nextInt(PlanSimulation.ILLNESS_DAYS_MAX - PlanSimulation.ILLNESS_DAYS_MIN + 1);
				for (int i = 0; i < days; i++) {
					sick.add(day.plusDays(i));
				}
				day = day.plusDays(days);
			}
		}
		return sick;
	}

	private static WeeklyAvailability scaled(WeeklyAvailability usual, double factor) {
		WeeklyAvailability copy = new WeeklyAvailability();
		// in Viertelstunden, wie man es in der App einträgt
		copy.setMonday(quarter(usual.hoursOn(DayOfWeek.MONDAY) * factor));
		copy.setTuesday(quarter(usual.hoursOn(DayOfWeek.TUESDAY) * factor));
		copy.setWednesday(quarter(usual.hoursOn(DayOfWeek.WEDNESDAY) * factor));
		copy.setThursday(quarter(usual.hoursOn(DayOfWeek.THURSDAY) * factor));
		copy.setFriday(quarter(usual.hoursOn(DayOfWeek.FRIDAY) * factor));
		copy.setSaturday(quarter(usual.hoursOn(DayOfWeek.SATURDAY) * factor));
		copy.setSunday(quarter(usual.hoursOn(DayOfWeek.SUNDAY) * factor));
		return copy;
	}

	private static double quarter(double hours) {
		return Math.round(hours * 4) / 4.0;
	}

	private static Workout resolve(PlannedWorkout planned) {
		return LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

	private static boolean isHard(Workout workout) {
		return !Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE)
				.contains(workout.getType());
	}
}
