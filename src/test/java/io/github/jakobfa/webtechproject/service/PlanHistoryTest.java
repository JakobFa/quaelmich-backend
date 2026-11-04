package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/** Der Plan richtet sich nach dem, was gefahren wurde: schwache Woche, Krankheit, Wiedereinstieg. */
class PlanHistoryTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // Montag
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	private static Athlete athlete() {
		Event event = new Event();
		event.setName("Gran Fondo");
		event.setDate(START.plusWeeks(20).plusDays(5));
		event.setDistanceKm(160.0);
		event.setElevationMeters(2500);
		event.setType(EventType.GRAN_FONDO);
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(1.0);
		availability.setTuesday(1.5);
		availability.setWednesday(1.0);
		availability.setThursday(1.5);
		availability.setFriday(0.0);
		availability.setSaturday(3.0);
		availability.setSunday(2.0);
		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setLevel(Level.AMBITIOUS);
		athlete.setEvent(event);
		athlete.setAvailability(availability);
		return athlete;
	}

	private static Map<LocalDate, List<PlannedWorkout>> byWeek(List<PlannedWorkout> plan) {
		Map<LocalDate, List<PlannedWorkout>> weeks = new TreeMap<>();
		plan.forEach(planned -> weeks.computeIfAbsent(
				planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
				monday -> new java.util.ArrayList<>()).add(planned));
		return weeks;
	}

	private static Phase phaseOf(List<PlannedWorkout> week) {
		return week.getFirst().getPhase();
	}

	private static int minutes(List<PlannedWorkout> week) {
		return week.stream().mapToInt(planned -> LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
				.orElseThrow().getDurationInMinutes()).sum();
	}

	/** Gefahren wurde nur ein Teil des Geplanten */
	private static PlanHistory.Week week(int planned, double share, int sickDays) {
		return new PlanHistory.Week(planned, (int) (planned * share), sickDays);
	}

	private static PlanHistory history(LocalDate thisMonday, Map<LocalDate, PlanHistory.Week> weeks, LocalDate reentry,
			int reentryWeeks) {
		return new PlanHistory(thisMonday, weeks, reentry, reentryWeeks);
	}

	@Test
	void withoutHistoryThePlanIsUnchanged() {
		var plain = GENERATOR.generate(athlete(), START, Map.of());
		var withNone = GENERATOR.generate(athlete(), START, Map.of(), PlanHistory.NONE);
		assertThat(withNone).usingRecursiveComparison().isEqualTo(plain);
	}

	@Test
	void aWeakLoadWeekIsNotFollowedByARecoveryWeek() {
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		LocalDate recovery = plain.entrySet().stream().filter(entry -> phaseOf(entry.getValue()) == Phase.RECOVERY)
				.map(Map.Entry::getKey).findFirst().orElseThrow();

		// die Woche davor wurde nur zu 50 % gefahren – die Erholungswoche fällt weg
		var history = history(recovery, Map.of(recovery.minusWeeks(1), week(400, 0.5, 0)), null, 0);
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history));

		assertThat(phaseOf(adapted.get(recovery))).isNotEqualTo(Phase.RECOVERY);
		assertThat(phaseOf(adapted.get(recovery))).isEqualTo(phaseOf(plain.get(recovery.minusWeeks(1))));
	}

	@Test
	void aSolidLoadWeekStillGetsItsRecoveryWeek() {
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		LocalDate recovery = plain.entrySet().stream().filter(entry -> phaseOf(entry.getValue()) == Phase.RECOVERY)
				.map(Map.Entry::getKey).findFirst().orElseThrow();

		var history = history(recovery, Map.of(recovery.minusWeeks(1), week(400, 0.9, 0)), null, 0);
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history));

		assertThat(phaseOf(adapted.get(recovery))).isEqualTo(Phase.RECOVERY);
	}

	@Test
	void pastWeeksAreNeverChanged() {
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		LocalDate recovery = plain.entrySet().stream().filter(entry -> phaseOf(entry.getValue()) == Phase.RECOVERY)
				.map(Map.Entry::getKey).findFirst().orElseThrow();
		// die laufende Woche ist schon die nach der Erholungswoche: die Erholungswoche selbst bleibt, wie sie war
		var history = history(recovery.plusWeeks(1), Map.of(recovery.minusWeeks(1), week(400, 0.3, 0)), null, 0);
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history));
		assertThat(phaseOf(adapted.get(recovery))).isEqualTo(Phase.RECOVERY);
	}

	@Test
	void afterLongIllnessTheFirstWeekIsEasyWithoutHardSessions() {
		LocalDate back = START.plusWeeks(5);
		var history = history(back, Map.of(back.minusWeeks(1), week(400, 0.0, 7)), back, 1);
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history));

		List<PlannedWorkout> week = adapted.get(back);
		assertThat(phaseOf(week)).isEqualTo(Phase.RECOVERY);
		assertThat(week).allSatisfy(planned -> assertThat(
				LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType())
				.isIn(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE));
		// danach geht es wieder los: Belastungswoche mit harten Einheiten
		assertThat(phaseOf(adapted.get(back.plusWeeks(1)))).isNotEqualTo(Phase.RECOVERY);
	}

	@Test
	void aScheduledRecoveryWeekRightAfterTheReentryWeekIsDropped() {
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		LocalDate recovery = plain.entrySet().stream().filter(entry -> phaseOf(entry.getValue()) == Phase.RECOVERY)
				.map(Map.Entry::getKey).findFirst().orElseThrow();
		LocalDate back = recovery.minusWeeks(1);

		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history(back, Map.of(), back, 1)));

		assertThat(phaseOf(adapted.get(back))).isEqualTo(Phase.RECOVERY); // der Wiedereinstieg
		assertThat(phaseOf(adapted.get(recovery))).isNotEqualTo(Phase.RECOVERY); // nicht noch eine Schonwoche
	}

	@Test
	void theRecoveryWeekIsAlsoDroppedWhenTheLastThreeWeeksWereOnlyAbout80Percent() {
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		LocalDate recovery = plain.entrySet().stream().filter(entry -> phaseOf(entry.getValue()) == Phase.RECOVERY)
				.map(Map.Entry::getKey).findFirst().orElseThrow();
		var weeks = new TreeMap<LocalDate, PlanHistory.Week>();
		weeks.put(recovery.minusWeeks(3), week(400, 0.6, 0));
		weeks.put(recovery.minusWeeks(2), week(400, 0.9, 0));
		weeks.put(recovery.minusWeeks(1), week(400, 0.8, 0)); // einzeln über 70 %, im Schnitt aber nur 77 %
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history(recovery, weeks, null, 0)));

		assertThat(phaseOf(adapted.get(recovery))).isNotEqualTo(Phase.RECOVERY);

		// dagegen: im Schnitt 93 % – die Erholungswoche bleibt
		weeks.put(recovery.minusWeeks(3), week(400, 1.0, 0));
		weeks.put(recovery.minusWeeks(2), week(400, 1.0, 0));
		weeks.put(recovery.minusWeeks(1), week(400, 0.8, 0));
		var kept = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history(recovery, weeks, null, 0)));
		assertThat(phaseOf(kept.get(recovery))).isEqualTo(Phase.RECOVERY);
	}

	@Test
	void whenTheWeekIsTightTheLongRideKeepsItsLengthAndTheSideDaysAreCut() {
		LocalDate thisMonday = START.plusWeeks(2);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		var weeks = Map.of(thisMonday.minusWeeks(1), new PlanHistory.Week(minutes(plain.get(thisMonday.minusWeeks(1))),
				minutes(plain.get(thisMonday.minusWeeks(1))), 0, 300, 300));
		// schon viel gefahren: für den Rest der Woche bleibt wenig
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, thisMonday.plusDays(2), weeks, new PlanHistory.Week(600, 260, 0, 300, 150))));

		var week = adapted.get(thisMonday);
		var longRide = week.stream().filter(planned -> LIBRARY
				.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType() == WorkoutType.LONG_RIDE
				|| LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getDurationInMinutes()
						== week.stream().mapToInt(p -> LIBRARY.resolve(p.getWorkoutId(), p.getDurationMinutes()).orElseThrow()
								.getDurationInMinutes()).max().orElse(0))
				.findFirst().orElseThrow();
		int longMinutes = LIBRARY.resolve(longRide.getWorkoutId(), longRide.getDurationMinutes()).orElseThrow()
				.getDurationInMinutes();
		// die lange Ausfahrt ist die längste Einheit und mehr als das Minimum der Nebentage
		assertThat(longMinutes).isGreaterThan(PlanGenerator.MIN_CAPPED_RIDE);
		assertThat(longRide.getDate().getDayOfWeek()).isIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
	}

	private static boolean isHard(PlannedWorkout planned) {
		return !java.util.Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE).contains(
				LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType());
	}

	private static PlanHistory withCurrentWeek(LocalDate thisMonday, LocalDate today,
			Map<LocalDate, PlanHistory.Week> weeks, PlanHistory.Week currentWeek) {
		return new PlanHistory(thisMonday, weeks, null, 0, today, currentWeek);
	}

	@Test
	void aMuchHarderWeekMeansNoGrowthAndOneHardSessionLess() {
		LocalDate thisMonday = START.plusWeeks(2);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		long plainHard = plain.get(thisMonday).stream().filter(PlanHistoryTest::isHard).count();
		assertThat(plainHard).isGreaterThanOrEqualTo(2);

		// die Wochen davor wie geplant gefahren; die letzte Woche aber 40 % mehr Belastung (zu hart)
		Map<LocalDate, Integer> daily = new TreeMap<>();
		plain.forEach((monday, week) -> week.forEach(planned -> {
			if (planned.getDate().isBefore(thisMonday)) {
				daily.merge(planned.getDate(), LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
						.orElseThrow().getDurationInMinutes(), Integer::sum);
			}
		}));
		var weeks = Map.of(thisMonday.minusWeeks(1), new PlanHistory.Week(minutes(plain.get(thisMonday.minusWeeks(1))),
				minutes(plain.get(thisMonday.minusWeeks(1))), 0, 300, 420));
		var history = new PlanHistory(thisMonday, weeks, null, 0, thisMonday, new PlanHistory.Week(0, 0, 0, 0, 0), daily,
				START);
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history));

		assertThat(adapted.get(thisMonday).stream().filter(PlanHistoryTest::isHard).count()).isEqualTo(plainHard - 1);
		assertThat(minutes(adapted.get(thisMonday))).isLessThanOrEqualTo(minutes(plain.get(thisMonday)));
	}

	@Test
	void aSlightlyHarderWeekChangesNothing() {
		LocalDate thisMonday = START.plusWeeks(2);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		var weeks = Map.of(thisMonday.minusWeeks(1), new PlanHistory.Week(minutes(plain.get(thisMonday.minusWeeks(1))),
				minutes(plain.get(thisMonday.minusWeeks(1))), 0, 300, 340));
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, thisMonday, weeks, new PlanHistory.Week(0, 0, 0, 0, 0))));

		assertThat(adapted.get(thisMonday).stream().filter(PlanHistoryTest::isHard).count())
				.isEqualTo(plain.get(thisMonday).stream().filter(PlanHistoryTest::isHard).count());
	}

	@Test
	void theRunningWeekIsOnlyPlannedFromTodayOn() {
		LocalDate thisMonday = START.plusWeeks(1);
		LocalDate today = thisMonday.plusDays(3); // Donnerstag
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, today, Map.of(), new PlanHistory.Week(500, 200, 0, 300, 120))));

		assertThat(adapted.get(thisMonday)).isNotEmpty().allMatch(planned -> !planned.getDate().isBefore(today));
	}

	@Test
	void whenTheWholeWeeksLoadIsAlreadyRiddenTheRestStaysEasy() {
		LocalDate thisMonday = START.plusWeeks(1);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		assertThat(plain.get(thisMonday).stream().filter(p -> !p.getDate().isBefore(today)).anyMatch(PlanHistoryTest::isHard))
				.as("ohne Anpassung stehen noch harte Einheiten an").isTrue();

		// schon 320 TSS gefahren bei 300 geplant (z. B. viel Zusätzliches)
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, today, Map.of(), new PlanHistory.Week(500, 480, 0, 300, 320))));

		assertThat(adapted.get(thisMonday)).isNotEmpty().noneMatch(PlanHistoryTest::isHard);
	}


	/** Vergangene Tage mit gleichbleibenden Minuten (für den gleitenden Deckel) */
	private static Map<LocalDate, Integer> dailyMinutesBefore(LocalDate day, int days, int minutes) {
		Map<LocalDate, Integer> daily = new TreeMap<>();
		for (int back = 1; back <= days; back++) {
			daily.put(day.minusDays(back), minutes);
		}
		return daily;
	}

	private static PlanHistory withDaily(LocalDate thisMonday, LocalDate today, Map<LocalDate, Integer> daily) {
		return new PlanHistory(thisMonday, Map.of(), null, 0, today, new PlanHistory.Week(0, 0, 0, 0, 0), daily, START);
	}

	@Test
	void afterIllnessTheVolumeBuildsOnWhatWasActuallyRidden() {
		LocalDate thisMonday = START.plusWeeks(6);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		// die letzten Wochen kaum gefahren (krank): 20 Minuten pro Tag
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withDaily(thisMonday, thisMonday, dailyMinutesBefore(thisMonday, 42, 20))));

		assertThat(minutes(adapted.get(thisMonday))).isLessThan(minutes(plain.get(thisMonday)));
	}

	@Test
	void whenThePlanWasFollowedTheWeekIsNotReduced() {
		LocalDate thisMonday = START.plusWeeks(6);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		Map<LocalDate, Integer> daily = new TreeMap<>();
		plain.values().forEach(week -> week.forEach(planned -> {
			if (planned.getDate().isBefore(thisMonday)) {
				daily.merge(planned.getDate(), LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
						.orElseThrow().getDurationInMinutes(), Integer::sum);
			}
		}));
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), withDaily(thisMonday, thisMonday, daily)));

		assertThat(minutes(adapted.get(thisMonday))).isGreaterThanOrEqualTo((int) (minutes(plain.get(thisMonday)) * 0.9));
	}

	@Test
	void whatIsAlreadyRiddenInTheLast7DaysReducesTheRestOfTheWindow() {
		LocalDate thisMonday = START.plusWeeks(3);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch
		Map<LocalDate, Integer> calm = dailyMinutesBefore(thisMonday, 35, 50); // 350 Minuten pro Woche
		Map<LocalDate, Integer> withBigMonday = new TreeMap<>(calm);
		withBigMonday.put(thisMonday, 270);
		withBigMonday.put(thisMonday.plusDays(1), 70);

		int restCalm = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(), withDaily(thisMonday, today, calm)))
				.get(thisMonday));
		int restBusy = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withDaily(thisMonday, today, withBigMonday))).get(thisMonday));

		assertThat(restBusy).isLessThan(restCalm);
	}

	@Test
	void theRollingLimitIsHigherForLoadThanForRecoveryAndMissingForTaperOrNoData() {
		Map<LocalDate, Integer> daily = dailyMinutesBefore(START, 28, 60); // 420 Minuten pro Woche
		LocalDate dataStart = START.minusDays(28);

		var load = PlanGenerator.rollingCap(daily, java.util.Set.of(), dataStart, START, Phase.BASE, false);
		var recovery = PlanGenerator.rollingCap(daily, java.util.Set.of(), dataStart, START, Phase.RECOVERY, false);
		var overreached = PlanGenerator.rollingCap(daily, java.util.Set.of(), dataStart, START, Phase.BASE, true);

		assertThat(load.weeklyCapMinutes()).isEqualTo(495); // 420 * 1,18
		assertThat(recovery.weeklyCapMinutes()).isEqualTo(315); // 420 * 0,75
		assertThat(overreached.weeklyCapMinutes()).isEqualTo(450); // kein Zuwachs, aber mindestens +30 min
		assertThat(PlanGenerator.rollingCap(daily, java.util.Set.of(), dataStart, START, Phase.TAPER, false)).isNull();
		assertThat(PlanGenerator.rollingCap(Map.of(), java.util.Set.of(), START.minusDays(1), START, Phase.BASE, false)).isNull();
	}

	@Test
	void aSingleUltraDayDoesNotLiftTheLimitForTheFollowingWeeks() {
		Map<LocalDate, Integer> daily = new java.util.HashMap<>(dailyMinutesBefore(START, 28, 60));
		daily.put(START.minusDays(10), 722); // z. B. ein 12-Stunden-Rennen
		LocalDate dataStart = START.minusDays(28);

		// der Tag zählt höchstens als 6 Stunden: (27 * 60 + 360) * 7 / 28 = 495 statt 585 Minuten pro Woche
		assertThat(PlanGenerator.baselineWeeklyMinutes(daily, java.util.Set.of(), dataStart, START)).isEqualTo(495.0);
		var cap = PlanGenerator.rollingCap(daily, java.util.Set.of(), dataStart, START, Phase.BASE, false);
		assertThat(cap.weeklyCapMinutes()).isEqualTo(584); // 495 * 1,18, nicht 585 * 1,18 = 690
	}

	@Test
	void aShortStartWeekIsExtrapolatedNotTreatedAsAWeakWeek() {
		Map<LocalDate, Integer> daily = Map.of(START.minusDays(1), 60, START.minusDays(2), 60, START.minusDays(3), 60);
		var cap = PlanGenerator.rollingCap(daily, java.util.Set.of(), START.minusDays(3), START, Phase.BASE, false);
		// mit erst gut einer halben Woche Daten ist der erlaubte Zuwachs klein (ca. +7 %), nicht der volle
		assertThat(cap.weeklyCapMinutes()).isBetween(420 + 30, 495);
	}

	@Test
	void theAllowedGrowthIsSmallWithLittleDataAndReachesTheFullRatioAfterAboutThreeWeeks() {
		assertThat(PlanGenerator.steadyGrowthRatio(1)).isBetween(1.09, 1.11);
		assertThat(PlanGenerator.steadyGrowthRatio(2)).isBetween(1.14, 1.17);
		assertThat(PlanGenerator.steadyGrowthRatio(4)).isGreaterThan(PlanGenerator.ACUTE_CHRONIC_RATIO);
	}

	@Test
	void ridesBeforeThePlanStartAreKnownToThePlan() {
		LocalDate thisMonday = START.plusWeeks(1);
		Map<LocalDate, Integer> before = dailyMinutesBefore(START, 42, 120); // 14 Stunden pro Woche vor dem Plan
		var high = new PlanHistory(thisMonday, Map.of(), null, 0, thisMonday, new PlanHistory.Week(0, 0, 0, 0, 0),
				before, START.minusDays(42));
		var low = withDaily(thisMonday, thisMonday, Map.of());

		int withForm = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(), high)).get(thisMonday));
		int withoutForm = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(), low)).get(thisMonday));

		assertThat(withForm).isGreaterThanOrEqualTo(withoutForm);
	}

	@Test
	void shortFixedWorkoutsDoNotUseUpTheWindowWithTheirGeneratedLength() {
		LocalDate thisMonday = START.plusWeeks(3);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch
		Map<LocalDate, Integer> daily = dailyMinutesBefore(thisMonday, 35, 70); // 490 Minuten pro Woche
		var generatedLength = new PlanHistory(thisMonday, Map.of(), null, 0, today,
				new PlanHistory.Week(0, 0, 0, 0, 0), daily, START);
		// Mittwoch bis Freitag stehen schon fest und sind kurz (selbst getauscht oder angepasst)
		Map<LocalDate, Integer> fixed = Map.of(today, 36, today.plusDays(1), 36, today.plusDays(2), 36);
		var withFixed = new PlanHistory(thisMonday, Map.of(), null, 0, today, new PlanHistory.Week(0, 0, 0, 0, 0),
				daily, START, java.util.Set.of(), fixed);

		int generated = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(), generatedLength)).get(thisMonday));
		int shortFixed = minutes(byWeek(GENERATOR.generate(athlete(), START, Map.of(), withFixed)).get(thisMonday));

		// die kurzen Einheiten lassen mehr Platz für den Rest der Woche (der Wochenendtag wird nicht unnötig gekürzt)
		assertThat(shortFixed).isGreaterThanOrEqualTo(generated);
	}

	@Test
	void aHardWorkoutAlreadyRiddenThisWeekIsNotPlannedAgainTheNextDay() {
		LocalDate thisMonday = START.plusWeeks(5);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch; gestern (Dienstag) war eine harte Einheit
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		var plainHard = plain.get(thisMonday).stream().filter(PlanHistoryTest::isHard)
				.map(planned -> LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType())
				.toList();
		assertThat(plainHard).hasSizeGreaterThanOrEqualTo(2);

		// Dienstag: die erste harte Einheit der Woche wurde gefahren
		var doneHard = new PlanHistory.DoneHard(java.util.Set.of(today.minusDays(1)), List.of(plainHard.getFirst()));
		var history = new PlanHistory(thisMonday, Map.of(), null, 0, today, new PlanHistory.Week(0, 0, 0, 0, 0),
				dailyMinutesBefore(thisMonday, 35, 60), START, java.util.Set.of(), Map.of(), doneHard);
		var week = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history)).get(thisMonday);

		long hardLeft = week.stream().filter(PlanHistoryTest::isHard).count();
		assertThat(hardLeft).isEqualTo(plainHard.size() - 1);
		// der Tag nach der harten Einheit (heute) ist nicht hart, und die gefahrene Art kommt nicht noch einmal vor
		assertThat(week.stream().filter(planned -> planned.getDate().equals(today))).noneMatch(PlanHistoryTest::isHard);
		assertThat(week.stream().filter(PlanHistoryTest::isHard)
				.map(planned -> LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType()))
				.doesNotContain(plainHard.getFirst());
	}

	@Test
	void doneHardWorkoutsAreRemovedFromTheWeeksList() {
		var list = List.of(WorkoutType.TEMPO, WorkoutType.VO2MAX);
		assertThat(PlanGenerator.withoutDoneHard(list, List.of(WorkoutType.TEMPO))).containsExactly(WorkoutType.VO2MAX);
		// eine andere Art gefahren: es fällt trotzdem eine weg
		assertThat(PlanGenerator.withoutDoneHard(list, List.of(WorkoutType.THRESHOLD))).hasSize(1);
		assertThat(PlanGenerator.withoutDoneHard(list, List.of())).isEqualTo(list);
	}

	@Test
	void aRunningWeekWithOnlyTrainingDaysSoFarGetsARestDayAmongTheRest() {
		LocalDate thisMonday = START.plusWeeks(5);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch; Montag und Dienstag wurden gefahren
		Map<LocalDate, Integer> daily = new TreeMap<>(dailyMinutesBefore(thisMonday, 35, 60));
		daily.put(thisMonday, 200);
		daily.put(thisMonday.plusDays(1), 70);
		var history = new PlanHistory(thisMonday, Map.of(), null, 0, today, new PlanHistory.Week(0, 0, 0, 0, 0), daily,
				START);
		WeeklyAvailability everyDay = new WeeklyAvailability();
		everyDay.setMonday(2.0); everyDay.setTuesday(2.0); everyDay.setWednesday(2.0); everyDay.setThursday(2.0);
		everyDay.setFriday(2.0); everyDay.setSaturday(4.0); everyDay.setSunday(4.0);
		Athlete athlete = athlete();
		athlete.setAvailability(everyDay);

		var week = byWeek(GENERATOR.generate(athlete, START, Map.of(), history)).get(thisMonday);

		// Mo und Di waren Trainingstage; von Mi bis So darf nicht jeder Tag belegt sein
		assertThat(week.stream().map(PlannedWorkout::getDate).distinct().count()).isLessThan(5);
	}

	@Test
	void theDayBeforeASelfChosenFtpTestStaysFree() {
		LocalDate thisMonday = START.plusWeeks(5);
		LocalDate today = thisMonday.plusDays(2); // Mittwoch; der Test ist morgen (selbst gewählt)
		var doneHard = new PlanHistory.DoneHard(java.util.Set.of(), List.of(), java.util.Set.of(today.plusDays(1)));
		var history = new PlanHistory(thisMonday, Map.of(), null, 0, today, new PlanHistory.Week(0, 0, 0, 0, 0),
				dailyMinutesBefore(thisMonday, 35, 60), START, java.util.Set.of(), Map.of(today.plusDays(1), 36),
				doneHard);

		var week = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history)).get(thisMonday);

		// heute (der Tag vor dem Test) gibt es kein Training
		assertThat(week).noneMatch(planned -> planned.getDate().equals(today));
		// und auch am Tag danach ist nichts Hartes vor dem Test verplant
		assertThat(week.stream().filter(planned -> planned.getDate().equals(today.minusDays(1)))).isEmpty();
	}
}

