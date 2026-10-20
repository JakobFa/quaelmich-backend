package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

class PlanGeneratorTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final PlanGenerator generator = new PlanGenerator(library);

	/** Di 1,5 h · Do 1,5 h · Sa 3 h · So 2 h */
	private static Athlete athlete(Level level, LocalDate eventDate) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setTuesday(1.5);
		availability.setThursday(1.5);
		availability.setSaturday(3);
		availability.setSunday(2);

		Event event = new Event();
		event.setName("Testevent");
		event.setDate(eventDate);
		event.setDistanceKm(200.0);
		event.setElevationMeters(3000);

		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setLevel(level);
		athlete.setEvent(event);
		athlete.setAvailability(availability);
		return athlete;
	}

	private Workout workoutOf(PlannedWorkout planned) {
		return library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

	private static boolean isHard(Workout workout) {
		return !Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE).contains(workout.getType());
	}

	@Test
	void trainsOnlyOnAvailableDaysAndNeverLongerThanAvailable() {
		Athlete athlete = athlete(Level.AMBITIOUS, MONDAY.plusWeeks(16).plusDays(6));

		List<PlannedWorkout> plan = generator.generate(athlete, MONDAY);

		assertThat(plan).isNotEmpty().allSatisfy(planned -> {
			double hours = athlete.getAvailability().hoursOn(planned.getDate().getDayOfWeek());
			assertThat(hours).isPositive();
			assertThat(workoutOf(planned).getDurationInMinutes()).isLessThanOrEqualTo((int) (hours * 60));
		});
	}

	@Test
	void startsAtStartDateAndRestsOnEventDayAndTheDayBefore() {
		LocalDate eventSunday = MONDAY.plusWeeks(8).plusDays(6);
		LocalDate startWednesday = MONDAY.plusDays(2);

		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, eventSunday), startWednesday);

		assertThat(plan).extracting(PlannedWorkout::getDate)
				.allSatisfy(date -> {
					assertThat(date).isAfterOrEqualTo(startWednesday);
					assertThat(date).isBefore(eventSunday.minusDays(1));
				});
	}

	@Test
	void usesTheDayWithMostTimeForTheLongRide() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, MONDAY.plusWeeks(20)), MONDAY);

		PlannedWorkout saturday = plan.stream()
				.filter(p -> p.getDate().equals(MONDAY.plusDays(5)))
				.findFirst().orElseThrow();
		assertThat(workoutOf(saturday).getType()).isEqualTo(WorkoutType.LONG_RIDE);
		assertThat(workoutOf(saturday).getDurationInMinutes()).isEqualTo(180);
	}

	@Test
	void neverPlansHardWorkoutsOnConsecutiveDays() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.PERFORMANCE, MONDAY.plusWeeks(20)), MONDAY);

		List<LocalDate> hardDates = plan.stream().filter(p -> isHard(workoutOf(p))).map(PlannedWorkout::getDate).toList();
		for (int i = 1; i < hardDates.size(); i++) {
			assertThat(ChronoUnit.DAYS.between(hardDates.get(i - 1), hardDates.get(i))).isGreaterThan(1);
		}
	}

	@Test
	void noHardWorkoutRightBeforeOrAfterTheLongRide() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.PERFORMANCE, MONDAY.plusWeeks(20)), MONDAY);

		List<LocalDate> longRides = plan.stream()
				.filter(p -> workoutOf(p).getType() == WorkoutType.LONG_RIDE).map(PlannedWorkout::getDate).toList();
		assertThat(plan).filteredOn(p -> isHard(workoutOf(p))).allSatisfy(hard -> assertThat(longRides)
				.noneMatch(longRide -> Math.abs(ChronoUnit.DAYS.between(longRide, hard.getDate())) <= 1));
	}

	@Test
	void beginnersGetFewerHardSessionsThanPerformanceAthletes() {
		LocalDate event = MONDAY.plusWeeks(20);
		long beginner = generator.generate(athlete(Level.BEGINNER, event), MONDAY).stream()
				.filter(p -> isHard(workoutOf(p))).count();
		long performance = generator.generate(athlete(Level.PERFORMANCE, event), MONDAY).stream()
				.filter(p -> isHard(workoutOf(p))).count();

		assertThat(beginner).isLessThan(performance);
	}

	@Test
	void longPlanStartsWithFtpTest() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, MONDAY.plusWeeks(12)), MONDAY);

		List<WorkoutType> firstWeek = plan.stream()
				.filter(p -> p.getDate().isBefore(MONDAY.plusWeeks(1)))
				.map(p -> workoutOf(p).getType())
				.toList();
		assertThat(firstWeek).contains(WorkoutType.FTP_TEST);
	}

	@Test
	void phasesFollowPeriodisationWithRecoveryWeeks() {
		LocalDate eventSunday = MONDAY.plusWeeks(15).plusDays(6);
		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, eventSunday), MONDAY);

		Map<Long, Phase> phasePerWeek = plan.stream().collect(Collectors.toMap(
				p -> ChronoUnit.WEEKS.between(MONDAY, p.getDate().with(DayOfWeek.MONDAY)),
				PlannedWorkout::getPhase, (a, b) -> a));

		assertThat(phasePerWeek.get(0L)).isEqualTo(Phase.BASE);
		assertThat(phasePerWeek.get(3L)).isEqualTo(Phase.RECOVERY);
		assertThat(phasePerWeek.get(6L)).isEqualTo(Phase.BUILD);
		assertThat(phasePerWeek.get(12L)).isEqualTo(Phase.PEAK);
		assertThat(phasePerWeek.get(14L)).isEqualTo(Phase.TAPER);
		assertThat(phasePerWeek.get(15L)).isEqualTo(Phase.TAPER);
	}

	@Test
	void hardWorkoutsGetHarderWithinAPhase() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.AMBITIOUS, MONDAY.plusWeeks(30)), MONDAY);

		List<Integer> sweetSpotTss = plan.stream()
				.map(this::workoutOf)
				.filter(w -> w.getType() == WorkoutType.SWEET_SPOT)
				.map(Workout::getTss)
				.toList();
		assertThat(sweetSpotTss).hasSizeGreaterThan(2);
		assertThat(sweetSpotTss.getLast()).isGreaterThan(sweetSpotTss.getFirst());
	}

	@Test
	void beginnersStayOnEasierVariants() {
		assertThat(PlanGenerator.progressionStep(Level.BEGINNER, Phase.BASE, 10)).isEqualTo(1);
		assertThat(PlanGenerator.progressionStep(Level.PERFORMANCE, Phase.BASE, 10)).isEqualTo(3);
		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 0)).isZero();
	}

	@Test
	void taperWeeksHaveLessVolumeThanBuildWeeks() {
		LocalDate eventSunday = MONDAY.plusWeeks(15).plusDays(6);
		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, eventSunday), MONDAY);

		Map<Phase, Double> avgMinutes = plan.stream().collect(Collectors.groupingBy(PlannedWorkout::getPhase,
				Collectors.averagingInt(p -> workoutOf(p).getDurationInMinutes())));

		assertThat(avgMinutes.get(Phase.TAPER)).isLessThan(avgMinutes.get(Phase.BUILD));
	}

}
