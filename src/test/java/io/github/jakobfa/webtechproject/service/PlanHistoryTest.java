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
	void afterIllnessTheVolumeBuildsOnWhatWasActuallyRidden() {
		LocalDate thisMonday = START.plusWeeks(6);
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		// die Wochen davor wurden nur zu einem Drittel gefahren
		var weeks = new TreeMap<LocalDate, PlanHistory.Week>();
		for (int i = 1; i <= 6; i++) {
			LocalDate monday = START.plusWeeks(6 - i);
			weeks.put(monday, week(minutes(plain.get(monday)), 0.33, 0));
		}
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(), history(thisMonday, weeks, null, 0)));

		// die laufende Woche darf nicht auf dem hohen Plan-Stand weitermachen
		assertThat(minutes(adapted.get(thisMonday))).isLessThan(minutes(plain.get(thisMonday)));
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

		// die Woche davor: Minuten wie geplant, aber 40 % mehr Belastung (zu hart gefahren)
		var weeks = Map.of(thisMonday.minusWeeks(1), new PlanHistory.Week(minutes(plain.get(thisMonday.minusWeeks(1))),
				minutes(plain.get(thisMonday.minusWeeks(1))), 0, 300, 420));
		var adapted = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, thisMonday, weeks, new PlanHistory.Week(0, 0, 0, 0, 0))));

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

	@Test
	void whatIsAlreadyRiddenThisWeekReducesTheRestOfTheWeek() {
		LocalDate thisMonday = START.plusWeeks(2);
		LocalDate today = thisMonday; // Wochenbeginn: gefahren wurde nur ein Zusatztraining
		var plain = byWeek(GENERATOR.generate(athlete(), START, Map.of()));
		var weeks = Map.of(thisMonday.minusWeeks(1), new PlanHistory.Week(minutes(plain.get(thisMonday.minusWeeks(1))),
				minutes(plain.get(thisMonday.minusWeeks(1))), 0, 300, 300));

		var untouched = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, today, weeks, new PlanHistory.Week(500, 0, 0, 300, 0))));
		var withExtra = byWeek(GENERATOR.generate(athlete(), START, Map.of(),
				withCurrentWeek(thisMonday, today, weeks, new PlanHistory.Week(500, 300, 0, 300, 200))));

		assertThat(minutes(withExtra.get(thisMonday))).isLessThan(minutes(untouched.get(thisMonday)));
	}
}
