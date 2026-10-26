package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingGoal;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/** Ziele ohne Event (FTP, Ausdauer, fit bleiben, Berg): Plan in 8-Wochen-Zyklen ohne Enddatum */
class OpenGoalPlanTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // ein Montag
	private static final int WEEKS = 24; // drei Zyklen
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);
	private static final Set<WorkoutType> EASY = EnumSet.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE,
			WorkoutType.LONG_RIDE);

	private static Athlete athlete(TrainingGoal goal) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setTuesday(1.5);
		availability.setWednesday(1);
		availability.setThursday(1.5);
		availability.setSaturday(3.5);
		availability.setSunday(2);
		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setLevel(Level.AMBITIOUS);
		athlete.setAvailability(availability);
		athlete.setTrainingGoal(goal);
		return athlete;
	}

	private static List<PlannedWorkout> plan(TrainingGoal goal) {
		return GENERATOR.generateSeason(athlete(goal), START, Map.of(), List.of(), START.plusWeeks(WEEKS - 1));
	}

	private static Workout workoutOf(PlannedWorkout planned) {
		return LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

	private static int weekOf(PlannedWorkout planned) {
		return (int) ChronoUnit.WEEKS.between(START, planned.getDate().with(TemporalAdjusters.previousOrSame(
				DayOfWeek.MONDAY)));
	}

	private static List<PlannedWorkout> hard(List<PlannedWorkout> plan) {
		return plan.stream().filter(p -> !EASY.contains(workoutOf(p).getType())).toList();
	}

	@ParameterizedTest
	@EnumSource(value = TrainingGoal.class, names = "EVENT", mode = EnumSource.Mode.EXCLUDE)
	void runsInEightWeekCyclesUntilTheHorizonWithoutTapering(TrainingGoal goal) {
		List<PlannedWorkout> plan = plan(goal);

		assertThat(plan.stream().map(PlannedWorkout::getDate).max(LocalDate::compareTo).orElseThrow())
				.isAfterOrEqualTo(START.plusWeeks(WEEKS - 1));
		assertThat(plan).extracting(PlannedWorkout::getPhase).doesNotContain(Phase.TAPER, Phase.PEAK);
		Map<Integer, Phase> phaseOfWeek = plan.stream()
				.collect(Collectors.toMap(OpenGoalPlanTest::weekOf, PlannedWorkout::getPhase, (a, b) -> a));
		for (int week = 0; week < WEEKS; week++) {
			assertThat(phaseOfWeek.get(week)).as("Woche %d", week)
					.isEqualTo(PlanGenerator.OPEN_CYCLE.get(week % PlanGenerator.OPEN_CYCLE.size()));
		}
	}

	@ParameterizedTest
	@EnumSource(value = TrainingGoal.class, names = "EVENT", mode = EnumSource.Mode.EXCLUDE)
	void ftpIsTestedAtTheStartAndAfterEveryCycle(TrainingGoal goal) {
		List<Integer> testWeeks = plan(goal).stream().filter(p -> workoutOf(p).getType() == WorkoutType.FTP_TEST)
				.map(OpenGoalPlanTest::weekOf).sorted().toList();

		assertThat(testWeeks).containsExactly(0, 8, 16);
	}

	@ParameterizedTest
	@EnumSource(value = TrainingGoal.class, names = "EVENT", mode = EnumSource.Mode.EXCLUDE)
	void neverTwoHardDaysInARow(TrainingGoal goal) {
		List<LocalDate> days = hard(plan(goal)).stream().map(PlannedWorkout::getDate).sorted().toList();
		for (int i = 1; i < days.size(); i++) {
			assertThat(ChronoUnit.DAYS.between(days.get(i - 1), days.get(i))).as("%s / %s", days.get(i - 1),
					days.get(i)).isGreaterThan(1);
		}
	}

	@ParameterizedTest
	@EnumSource(value = TrainingGoal.class, names = "EVENT", mode = EnumSource.Mode.EXCLUDE)
	void laterCyclesAreNotEasierThanTheFirst(TrainingGoal goal) {
		List<PlannedWorkout> plan = plan(goal);
		double first = averageHardTss(plan, 0);
		double third = averageHardTss(plan, 2);

		assertThat(third).as("harte Einheiten im 3. Zyklus").isGreaterThanOrEqualTo(first);
	}

	private static double averageHardTss(List<PlannedWorkout> plan, int cycle) {
		return hard(plan).stream().filter(p -> weekOf(p) / 8 == cycle)
				.filter(p -> workoutOf(p).getType() != WorkoutType.FTP_TEST)
				.mapToInt(p -> workoutOf(p).getTss()).average().orElse(0);
	}

	@Test
	void eachGoalHasItsOwnFocus() {
		Map<TrainingGoal, Set<WorkoutType>> types = Map.of(
				TrainingGoal.FTP, typesOf(plan(TrainingGoal.FTP)),
				TrainingGoal.CLIMBING, typesOf(plan(TrainingGoal.CLIMBING)),
				TrainingGoal.ENDURANCE, typesOf(plan(TrainingGoal.ENDURANCE)));

		assertThat(types.get(TrainingGoal.FTP)).contains(WorkoutType.THRESHOLD);
		assertThat(types.get(TrainingGoal.CLIMBING)).contains(WorkoutType.THRESHOLD);
		assertThat(types.get(TrainingGoal.ENDURANCE)).contains(WorkoutType.TEMPO)
				.doesNotContain(WorkoutType.ANAEROBIC, WorkoutType.SPRINT);
		// wenig Intensität heißt nicht keine: ab und zu ein kurzer VO2max-Reiz, aber selten
		List<PlannedWorkout> endurance = plan(TrainingGoal.ENDURANCE);
		long vo2max = endurance.stream().filter(p -> workoutOf(p).getType() == WorkoutType.VO2MAX).count();
		long weeks = endurance.stream().map(p -> p.getDate().with(java.time.DayOfWeek.MONDAY)).distinct().count();
		assertThat(vo2max).as("VO2max-Einheiten in %d Wochen", weeks).isLessThanOrEqualTo(weeks / 3 + 1);
	}

	@Test
	void enduranceRidesLongestAndFitnessIsGentlest() {
		int enduranceLong = longestRide(plan(TrainingGoal.ENDURANCE));
		int ftpLong = longestRide(plan(TrainingGoal.FTP));
		double fitnessHard = hard(plan(TrainingGoal.FITNESS)).stream().mapToInt(p -> workoutOf(p).getTss())
				.average().orElse(0);
		double ftpHard = hard(plan(TrainingGoal.FTP)).stream().mapToInt(p -> workoutOf(p).getTss()).average()
				.orElse(0);

		assertThat(enduranceLong).isGreaterThan(ftpLong);
		assertThat(fitnessHard).isLessThanOrEqualTo(ftpHard);
	}

	private static Set<WorkoutType> typesOf(List<PlannedWorkout> plan) {
		return plan.stream().map(p -> workoutOf(p).getType()).collect(Collectors.toSet());
	}

	private static int longestRide(List<PlannedWorkout> plan) {
		return plan.stream().mapToInt(p -> workoutOf(p).getDurationInMinutes()).max().orElse(0);
	}

	@Test
	void startingOnASundayStillTestsTheFtpRightAway() {
		LocalDate sunday = START.minusDays(1);
		List<PlannedWorkout> plan = GENERATOR.generateSeason(athlete(TrainingGoal.FTP), sunday, Map.of(),
				List.of(), sunday.plusWeeks(12));

		LocalDate firstTest = plan.stream().filter(p -> workoutOf(p).getType() == WorkoutType.FTP_TEST)
				.map(PlannedWorkout::getDate).min(LocalDate::compareTo).orElseThrow();
		assertThat(firstTest).as("in der ersten vollen Woche, nicht erst nach 8 Wochen").isBefore(sunday.plusDays(8));
	}

	@Test
	void sameStartAndCycleGiveTheSamePlanWhenItGrows() {
		// verlängern darf die vorhandenen Wochen nicht verändern
		List<PlannedWorkout> shorter = GENERATOR.generateSeason(athlete(TrainingGoal.FTP), START, Map.of(),
				List.of(), START.plusWeeks(11));
		List<PlannedWorkout> longer = GENERATOR.generateSeason(athlete(TrainingGoal.FTP), START, Map.of(),
				List.of(), START.plusWeeks(23));

		assertThat(longer.subList(0, shorter.size())).extracting(p -> p.getDate() + " " + p.getWorkoutId())
				.containsExactlyElementsOf(shorter.stream().map(p -> p.getDate() + " " + p.getWorkoutId()).toList());
	}

}
