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
import io.github.jakobfa.webtechproject.model.EventType;
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

	/** Di 1,5 h · Do 1,5 h · Sa 3 h · So 2 h, Event 200 km / 3000 hm */
	private static Athlete athlete(Level level, LocalDate eventDate) {
		return athlete(level, eventDate, 200.0, 3000, null);
	}

	private static Athlete athlete(Level level, LocalDate eventDate, double km, int meters, EventType type) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setTuesday(1.5);
		availability.setThursday(1.5);
		availability.setSaturday(3);
		availability.setSunday(2);

		Event event = new Event();
		event.setName("Testevent");
		event.setDate(eventDate);
		event.setDistanceKm(km);
		event.setElevationMeters(meters);
		event.setType(type);

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
	void startsAtStartDateAndEndsWithRestDayAndOpener() {
		LocalDate eventSunday = MONDAY.plusWeeks(8).plusDays(6);
		LocalDate startWednesday = MONDAY.plusDays(2);

		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, eventSunday), startWednesday);

		assertThat(plan).extracting(PlannedWorkout::getDate).allSatisfy(date -> {
			assertThat(date).isAfterOrEqualTo(startWednesday);
			assertThat(date).isBefore(eventSunday);
			assertThat(date).as("zwei Tage vorher Ruhe").isNotEqualTo(eventSunday.minusDays(2));
		});
		PlannedWorkout dayBefore = plan.getLast();
		assertThat(dayBefore.getDate()).isEqualTo(eventSunday.minusDays(1));
		assertThat(workoutOf(dayBefore).getTitle()).startsWith("Opener");
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
		assertThat(phasePerWeek.get(8L)).isEqualTo(Phase.BUILD);
		assertThat(phasePerWeek.get(11L)).isEqualTo(Phase.PEAK);
		assertThat(phasePerWeek.get(13L)).isEqualTo(Phase.PEAK);
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


	// ---------- neue Trainingslogik ----------

	@Test
	void phasesAreCountedBackwardsAndNoRecoveryRightBeforeThePeak() {
		for (int weeks = 1; weeks <= 30; weeks++) {
			List<Phase> phases = PlanGenerator.phasesFor(weeks);
			assertThat(phases).hasSize(weeks);
			assertThat(phases.getLast()).isEqualTo(Phase.TAPER);
			int firstPeak = phases.indexOf(Phase.PEAK);
			if (firstPeak > 0) {
				assertThat(phases.get(firstPeak - 1)).as("%d Wochen", weeks).isNotEqualTo(Phase.RECOVERY);
			}
		}
		// 16 Wochen: Grundlage, Aufbau, 3 Wochen Spitze, 2 Wochen Tapering
		assertThat(PlanGenerator.phasesFor(16).subList(11, 16))
				.containsExactly(Phase.PEAK, Phase.PEAK, Phase.PEAK, Phase.TAPER, Phase.TAPER);
	}

	@Test
	void volumeStartsLowAndRises() {
		assertThat(PlanGenerator.volumeFactor(Phase.BASE, 0)).isEqualTo(0.7);
		assertThat(PlanGenerator.volumeFactor(Phase.BASE, 4)).isGreaterThan(0.85);
		assertThat(PlanGenerator.volumeFactor(Phase.BUILD, 20)).isEqualTo(1.0);
		assertThat(PlanGenerator.volumeFactor(Phase.RECOVERY, 4)).isLessThan(PlanGenerator.volumeFactor(Phase.BASE, 3));
	}

	@Test
	void longRideGrowsTowardsTheEventDuration() {
		var oetztaler = new EventProfile(585, EventProfile.Focus.LONG_CLIMBING);

		int first = PlanGenerator.longRideMinutes(oetztaler, Phase.BASE, 0, 12);
		int last = PlanGenerator.longRideMinutes(oetztaler, Phase.PEAK, 11, 12);

		assertThat(first).isEqualTo((int) (585 * 0.4));
		assertThat(last).isEqualTo(PlanGenerator.MAX_LONG_RIDE); // 75 % wären 7:18 h → gedeckelt auf 6 h
		assertThat(PlanGenerator.longRideMinutes(oetztaler, Phase.RECOVERY, 6, 12))
				.isLessThan(PlanGenerator.longRideMinutes(oetztaler, Phase.BASE, 6, 12));
	}

	@Test
	void climbingEventTrainsThresholdAndCriteriumTrainsIntensity() {
		LocalDate event = MONDAY.plusWeeks(16).plusDays(6);
		List<WorkoutType> climbing = hardTypes(generator.generate(
				athlete(Level.AMBITIOUS, event, 227, 5500, EventType.GRAN_FONDO), MONDAY));
		List<WorkoutType> crit = hardTypes(generator.generate(
				athlete(Level.AMBITIOUS, event, 40, 100, EventType.CRITERIUM), MONDAY));

		assertThat(climbing).contains(WorkoutType.SWEET_SPOT, WorkoutType.THRESHOLD)
				.doesNotContain(WorkoutType.ANAEROBIC);
		assertThat(crit).contains(WorkoutType.VO2MAX, WorkoutType.ANAEROBIC);
	}

	@Test
	void longEventsGetTempoBlocksInTheLongRide() {
		List<PlannedWorkout> plan = generator.generate(
				athlete(Level.AMBITIOUS, MONDAY.plusWeeks(16).plusDays(6), 227, 5500, EventType.GRAN_FONDO), MONDAY);

		assertThat(plan).map(this::workoutOf).anySatisfy(w -> assertThat(w.getTitle()).endsWith("mit Tempo"));
	}

	@Test
	void beginnersNeverGetAnaerobicOrSprintIntervals() {
		List<WorkoutType> types = hardTypes(generator.generate(
				athlete(Level.BEGINNER, MONDAY.plusWeeks(16).plusDays(6), 40, 100, EventType.CRITERIUM), MONDAY));

		assertThat(types).doesNotContain(WorkoutType.ANAEROBIC, WorkoutType.OVER_UNDER);
	}

	@Test
	void ftpIsRetestedWhenTheBuildPhaseStarts() {
		LocalDate event = MONDAY.plusWeeks(15).plusDays(6);
		List<PlannedWorkout> plan = generator.generate(athlete(Level.HOBBY, event), MONDAY);

		assertThat(plan).filteredOn(p -> workoutOf(p).getType() == WorkoutType.FTP_TEST).hasSize(2);
	}

	@Test
	void noWeekRepeatsTheSameHardWorkoutType() {
		List<PlannedWorkout> plan = generator.generate(
				athlete(Level.PERFORMANCE, MONDAY.plusWeeks(20), 227, 5500, EventType.GRAN_FONDO), MONDAY);

		Map<LocalDate, List<WorkoutType>> perWeek = plan.stream().filter(p -> isHard(workoutOf(p)))
				.collect(Collectors.groupingBy(p -> p.getDate().with(DayOfWeek.MONDAY),
						Collectors.mapping(p -> workoutOf(p).getType(), Collectors.toList())));
		assertThat(perWeek.values()).allSatisfy(types -> assertThat(types).doesNotHaveDuplicates());
	}

	private List<WorkoutType> hardTypes(List<PlannedWorkout> plan) {
		return plan.stream().map(this::workoutOf).filter(PlanGeneratorTest::isHard).map(Workout::getType).toList();
	}

	@Test
	void ambitiousTargetTrainsHarderRelaxedTargetEasier() {
		var realistic = EventProfile.Ambition.REALISTIC;
		var ambitious = EventProfile.Ambition.AMBITIOUS;
		var relaxed = EventProfile.Ambition.RELAXED;

		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, ambitious))
				.isGreaterThan(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, realistic));
		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, relaxed))
				.isLessThan(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, realistic));

		assertThat(PlanGenerator.hardSessionsPerWeek(Level.AMBITIOUS, 8, ambitious)).isEqualTo(3);
		assertThat(PlanGenerator.hardSessionsPerWeek(Level.AMBITIOUS, 8, relaxed)).isEqualTo(1);

		var ultraFast = new EventProfile(1800, EventProfile.Focus.ULTRA, 1400, ambitious);
		var ultraEasy = new EventProfile(1800, EventProfile.Focus.ULTRA, 2400, relaxed);
		assertThat(PlanGenerator.useTempoLongRide(ultraFast, Phase.BUILD, 1)).isTrue();
		assertThat(PlanGenerator.useTempoLongRide(ultraEasy, Phase.BUILD, 0)).isFalse();
	}

}
