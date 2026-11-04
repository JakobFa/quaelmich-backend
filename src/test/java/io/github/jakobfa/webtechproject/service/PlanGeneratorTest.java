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
	void hardSessionsVaryFromWeekToWeekInsteadOfRepeatingTheSameWorkout() {
		Athlete athlete = athlete(Level.AMBITIOUS, MONDAY.plusWeeks(16).plusDays(6));

		List<Workout> hard = generator.generate(athlete, MONDAY).stream().map(this::workoutOf)
				.filter(PlanGeneratorTest::isHard).filter(w -> w.getType() != WorkoutType.FTP_TEST).toList();

		assertThat(hard).extracting(Workout::getId).as("Abwechslungs-Workouts kommen im Plan vor")
				.anyMatch(id -> id >= 50);
		for (int i = 1; i < hard.size(); i++) {
			assertThat(hard.subList(Math.max(0, i - 2), i)).extracting(Workout::getId)
					.as("nicht dreimal hintereinander " + hard.get(i).getTitle())
					.isNotEqualTo(List.of(hard.get(i).getId(), hard.get(i).getId()));
		}
	}

	@Test
	void hardSessionsGoToTheTightestDayThatFitsAndLongDaysAreKeptForVolume() {
		Athlete athlete = athlete(Level.PERFORMANCE, MONDAY.plusWeeks(20).plusDays(5), 900, 8500, EventType.ULTRA);
		WeeklyAvailability week = new WeeklyAvailability();
		week.setMonday(4);
		week.setTuesday(2);
		week.setThursday(2);
		week.setFriday(4);
		week.setSaturday(4);
		week.setSunday(4.5);
		athlete.setAvailability(week);

		List<PlannedWorkout> first = generator.generate(athlete, MONDAY).stream()
				.filter(planned -> planned.getDate().isBefore(MONDAY.plusWeeks(1))).toList();

		// die langen Tage (Mo, Fr) haben Zeit für Umfang, die kurzen (Di, Do) tragen die harten Einheiten
		assertThat(first).filteredOn(planned -> isHard(workoutOf(planned)))
				.extracting(planned -> planned.getDate().getDayOfWeek())
				.isNotEmpty().doesNotContain(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY)
				.allMatch(day -> day == java.time.DayOfWeek.TUESDAY || day == java.time.DayOfWeek.THURSDAY);
		assertThat(first).filteredOn(planned -> planned.getDate().getDayOfWeek() == java.time.DayOfWeek.MONDAY)
				.singleElement().satisfies(monday -> assertThat(workoutOf(monday).getDurationInMinutes())
						.as("Montag mit 4 h wird für Umfang genutzt").isGreaterThanOrEqualTo(150));
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
		assertThat(phasePerWeek.get(11L)).isEqualTo(Phase.RECOVERY);
		assertThat(phasePerWeek.get(12L)).isEqualTo(Phase.PEAK);
		assertThat(phasePerWeek.get(13L)).isEqualTo(Phase.PEAK);
		assertThat(phasePerWeek.get(14L)).isEqualTo(Phase.TAPER);
		assertThat(phasePerWeek.get(15L)).isEqualTo(Phase.TAPER);
	}

	@Test
	void hardWorkoutsGetHarderWithinAPhase() {
		List<PlannedWorkout> plan = generator.generate(athlete(Level.AMBITIOUS, MONDAY.plusWeeks(30)), MONDAY);

		List<Integer> sweetSpotTss = plan.stream()
				// Erholungswochen haben bewusst die kleinste Stufe
				.filter(planned -> planned.getPhase() != Phase.RECOVERY)
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
		assertThat(PlanGenerator.progressionStep(Level.PERFORMANCE, Phase.BASE, 10)).isEqualTo(5);
		assertThat(PlanGenerator.progressionStep(Level.PERFORMANCE, Phase.BASE, 6)).as("Steigerung geht über Stufe 3 hinaus").isEqualTo(3);
		assertThat(PlanGenerator.progressionStep(Level.AMBITIOUS, Phase.BASE, 20)).isEqualTo(4);
		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 0)).as("Aufbau beginnt nicht bei null").isEqualTo(1);
		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.PEAK, 2)).as("Spitze über der Level-Grenze").isEqualTo(3);
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
	void phasesAreCountedBackwardsAndNeverMoreThanFourLoadWeeksInARow() {
		for (int weeks = 1; weeks <= 30; weeks++) {
			List<Phase> phases = PlanGenerator.phasesFor(weeks);
			assertThat(phases).hasSize(weeks);
			assertThat(phases.getLast()).isEqualTo(Phase.TAPER);
			int run = 0;
			for (Phase phase : phases) {
				run = PlanGenerator.isLoadPhase(phase) ? run + 1 : 0;
				assertThat(run).as("%d Wochen: Belastungswochen am Stück", weeks).isLessThanOrEqualTo(4);
			}
		}
		// 16 Wochen: Grundlage, Aufbau, Spitze, 2 Wochen Tapering – am Ende keine Erholung direkt vor dem Tapering
		List<Phase> sixteen = PlanGenerator.phasesFor(16);
		assertThat(sixteen.subList(14, 16)).containsExactly(Phase.TAPER, Phase.TAPER);
		assertThat(sixteen.get(13)).isNotEqualTo(Phase.RECOVERY);
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
	void goalChangesHowHardThePlanIs() {
		var goodTime = io.github.jakobfa.webtechproject.model.EventGoal.GOOD_TIME;
		var compete = io.github.jakobfa.webtechproject.model.EventGoal.COMPETE;
		var finish = io.github.jakobfa.webtechproject.model.EventGoal.FINISH;

		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, compete))
				.isGreaterThan(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, goodTime));
		assertThat(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, finish))
				.isLessThan(PlanGenerator.progressionStep(Level.HOBBY, Phase.BUILD, 2, goodTime));

		assertThat(PlanGenerator.hardSessionsPerWeek(Level.AMBITIOUS, 8, compete)).isEqualTo(3);
		assertThat(PlanGenerator.hardSessionsPerWeek(Level.AMBITIOUS, 8, finish))
				.as("ankommen: nicht weniger harte Einheiten, nur leichtere").isEqualTo(2);

		var ultraCompete = new EventProfile(1800, EventProfile.Focus.ULTRA, compete);
		var ultraFinish = new EventProfile(1800, EventProfile.Focus.ULTRA, finish);
		assertThat(PlanGenerator.useTempoLongRide(ultraCompete, Phase.BUILD, 1)).isTrue();
		assertThat(PlanGenerator.useTempoLongRide(ultraFinish, Phase.BUILD, 0)).isFalse();
		var ultraGoodTime = new EventProfile(1800, EventProfile.Focus.ULTRA, goodTime);
		assertThat(PlanGenerator.useTempoLongRide(ultraGoodTime, Phase.BUILD, 0))
				.as("Ultra mit guter Zeit: Tempo-Blöcke in der langen Ausfahrt").isTrue();
		assertThat(PlanGenerator.useTempoLongRide(ultraGoodTime, Phase.BASE, 0)).isFalse();
	}

	/** Viel Zeit, langes Wochenende: wie ein Ultra-Fahrer mit 19 h */
	private static Athlete ultraRider() {
		Athlete athlete = athlete(Level.PERFORMANCE, MONDAY.plusWeeks(28).plusDays(4), 900.0, 8500, EventType.ULTRA);
		WeeklyAvailability availability = athlete.getAvailability();
		availability.setMonday(2.0);
		availability.setTuesday(2.0);
		availability.setWednesday(1.0);
		availability.setThursday(2.0);
		availability.setFriday(2.0);
		availability.setSaturday(5.0);
		availability.setSunday(5.0);
		return athlete;
	}

	@Test
	void noHardSessionTheDayAfterABigWeekend() {
		List<PlannedWorkout> plan = generator.generate(ultraRider(), MONDAY);

		assertThat(plan.stream().filter(planned -> isHard(workoutOf(planned)))
				.filter(planned -> planned.getDate().getDayOfWeek() == DayOfWeek.MONDAY))
				.as("montags nach 2 × 5 h ist Dienstag oder Donnerstag besser").isEmpty();
	}

	@Test
	void hardSessionsKeepProgressingForMonths() {
		List<PlannedWorkout> plan = generator.generate(ultraRider(), MONDAY);

		List<Integer> tempo = plan.stream().map(this::workoutOf).filter(w -> w.getType() == WorkoutType.TEMPO)
				.map(PlanGeneratorTest::intervalMinutes).toList();
		assertThat(tempo.stream().mapToInt(Integer::intValue).max().orElseThrow())
				.as("Tempo-Blöcke werden über den Plan deutlich länger").isGreaterThanOrEqualTo(75)
				.isGreaterThan(tempo.getFirst());
	}

	private static int intervalMinutes(Workout workout) {
		return workout.getSteps().stream()
				.filter(step -> step.kind() == io.github.jakobfa.webtechproject.model.StepKind.INTERVAL)
				.mapToInt(io.github.jakobfa.webtechproject.model.WorkoutStep::durationSeconds).sum() / 60;
	}

	@Test
	void hardDaysAreFilledWithEasyRiding() {
		List<PlannedWorkout> plan = generator.generate(ultraRider(), MONDAY);

		List<Workout> hard = plan.stream().filter(planned -> planned.getPhase() == Phase.BUILD)
				.map(this::workoutOf).filter(PlanGeneratorTest::isHard)
				.filter(w -> w.getType() != WorkoutType.FTP_TEST).toList();
		assertThat(hard).isNotEmpty();
		assertThat(hard.stream().filter(w -> w.getDurationInMinutes() >= 105).count())
				.as("2 h am harten Tag: Intervalle plus lockere Grundlage – meistens")
				.isGreaterThan(hard.size() / 2);
		assertThat(hard).allMatch(w -> w.getDurationInMinutes() <= 120);
	}

	@Test
	void fitnessMemoryAfterAnOffSeason() {
		LocalDate monday = LocalDate.of(2026, 11, 9);
		Map<LocalDate, Integer> daily = new java.util.HashMap<>();
		// vier Wochen à ca. 16 h, dann drei Wochen Off-Season à ca. 5 h
		for (LocalDate day = monday.minusDays(49); day.isBefore(monday); day = day.plusDays(1)) {
			daily.put(day, day.isBefore(monday.minusDays(21)) ? 140 : 43);
		}
		LocalDate dataStart = monday.minusDays(49);

		Double recent = PlanGenerator.baselineWeeklyMinutes(daily, Set.of(), dataStart, monday, false);
		Double withMemory = PlanGenerator.baselineWeeklyMinutes(daily, Set.of(), dataStart, monday, true);
		assertThat(withMemory).as("die Fitness vor der Pause zählt (abgeschwächt) mit").isGreaterThan(recent * 1.3);
		assertThat(withMemory).as("aber nicht voll").isLessThan(140 * 7.0);

		var withCap = PlanGenerator.rollingCap(daily, Set.of(), dataStart, monday, Phase.BASE, false, true);
		var withoutCap = PlanGenerator.rollingCap(daily, Set.of(), dataStart, monday, Phase.BASE, false, false);
		assertThat(withCap.weeklyCapMinutes()).isGreaterThan(withoutCap.weeklyCapMinutes());
	}

	@Test
	void noFitnessMemoryAfterIllness() {
		LocalDate monday = LocalDate.of(2026, 11, 9);
		var sick = new PlanHistory(monday, Map.of(monday.minusWeeks(2), new PlanHistory.Week(600, 0, 5)), null, 0);
		var healthy = new PlanHistory(monday, Map.of(monday.minusWeeks(2), new PlanHistory.Week(600, 300, 0)), null, 0);
		assertThat(PlanGenerator.memoryAllowed(sick, monday)).isFalse();
		assertThat(PlanGenerator.memoryAllowed(healthy, monday)).isTrue();
	}

	@Test
	void withoutMoreEventsTheSeasonIsExactlyTheOldPlan() {
		Athlete athlete = athlete(Level.AMBITIOUS, MONDAY.plusWeeks(12).plusDays(5));
		var old = generator.generate(athlete, MONDAY);
		var season = generator.generateSeason(athlete, MONDAY, Map.of(), List.of());

		assertThat(season).extracting(PlannedWorkout::getDate, PlannedWorkout::getWorkoutId,
				PlannedWorkout::getDurationMinutes)
				.containsExactlyElementsOf(old.stream().map(p -> org.assertj.core.groups.Tuple.tuple(p.getDate(),
						p.getWorkoutId(), p.getDurationMinutes())).toList());
	}

	@Test
	void secondEventGetsItsOwnBlockWithRecoveryFirst() {
		LocalDate first = MONDAY.plusWeeks(8).plusDays(5); // Samstag
		LocalDate second = first.plusWeeks(6);
		Athlete athlete = athlete(Level.AMBITIOUS, first);
		Event next = new Event();
		next.setName("Zweites");
		next.setDate(second);
		next.setDistanceKm(150.0);
		next.setElevationMeters(1500);

		var season = generator.generateSeason(athlete, MONDAY, Map.of(), List.of(next));

		assertThat(season).noneMatch(p -> p.getDate().equals(first) || p.getDate().equals(second));
		assertThat(season).anyMatch(p -> p.getDate().isAfter(first) && p.getDate().isBefore(second));
		assertThat(season).noneMatch(p -> p.getDate().isAfter(second));
		assertThat(season).filteredOn(p -> p.getDate().isAfter(first) && !p.getDate().isAfter(first.plusDays(3)))
				.as("die Tage nach dem ersten Event: kurz und locker")
				.allMatch(p -> workoutOf(p).getType() == WorkoutType.RECOVERY && workoutOf(p).getDurationInMinutes() <= 45);
		var beforeSecond = season.stream().filter(p -> p.getDate().isBefore(second)).reduce((a, b) -> b).orElseThrow();
		assertThat(beforeSecond.getDate()).as("Training bis in die Event-Woche").isAfter(second.minusDays(7));
	}

	// ---------- Erholungswoche nur, wenn sich Belastung aufgebaut hat ----------

	/** Minuten je Tag: 4 Wochen mit before, dann 3 Wochen mit after (je Woche, gleichmäßig auf 7 Tage) */
	private static Map<LocalDate, Integer> blocks(LocalDate monday, int beforeWeekly, int afterWeekly) {
		Map<LocalDate, Integer> daily = new java.util.HashMap<>();
		for (LocalDate day = monday.minusDays(49); day.isBefore(monday); day = day.plusDays(1)) {
			daily.put(day, (day.isBefore(monday.minusDays(21)) ? beforeWeekly : afterWeekly) / 7);
		}
		return daily;
	}

	@Test
	void quietWeeksAfterARaceOrHolidayNeedNoRecoveryWeek() {
		LocalDate monday = LocalDate.of(2026, 11, 9);
		// z. B. Urlaub oder Rennen mit 16 h, danach drei ruhigere Wochen mit 10 h
		assertThat(PlanGenerator.builtUp(blocks(monday, 960, 600), Set.of(), monday.minusDays(49), monday)).isFalse();
		// dasselbe auf kleinem Niveau: 5 h, danach 3 h
		assertThat(PlanGenerator.builtUp(blocks(monday, 300, 180), Set.of(), monday.minusDays(49), monday)).isFalse();
	}

	@Test
	void steadyOrGrowingTrainingKeepsTheRecoveryWeek() {
		LocalDate monday = LocalDate.of(2026, 11, 9);
		LocalDate start = monday.minusDays(49);
		assertThat(PlanGenerator.builtUp(blocks(monday, 420, 420), Set.of(), start, monday))
				.as("gleichmäßig trainiert: Ermüdung sammelt sich trotzdem").isTrue();
		assertThat(PlanGenerator.builtUp(blocks(monday, 420, 560), Set.of(), start, monday)).as("Aufbau").isTrue();
		assertThat(PlanGenerator.builtUp(blocks(monday, 420, 399), Set.of(), start, monday))
				.as("nur etwas weniger (95 %)").isTrue();
	}

	@Test
	void withoutEnoughDataTheRecoveryWeekStays() {
		LocalDate monday = LocalDate.of(2026, 11, 9);
		Map<LocalDate, Integer> daily = blocks(monday, 960, 300);
		assertThat(PlanGenerator.builtUp(daily, Set.of(), monday.minusDays(21), monday))
				.as("nichts über die Wochen davor bekannt").isTrue();
		assertThat(PlanGenerator.builtUp(daily, Set.of(), monday.minusDays(10), monday))
				.as("nicht mal 3 Wochen Daten").isTrue();
	}

	@Test
	void recoveryWeeksWithManyDaysGetARestDayAndSprints() {
		List<PlannedWorkout> plan = generator.generate(ultraRider(), MONDAY);
		var recoveryWeeks = plan.stream().filter(planned -> planned.getPhase() == Phase.RECOVERY)
				.collect(Collectors.groupingBy(planned -> planned.getDate().with(DayOfWeek.MONDAY)));
		assertThat(recoveryWeeks).isNotEmpty();
		for (var week : recoveryWeeks.values()) {
			assertThat(week).as("Montag nach dem Belastungs-Wochenende frei")
					.noneMatch(planned -> planned.getDate().getDayOfWeek() == DayOfWeek.MONDAY);
			assertThat(week.stream().map(this::workoutOf).map(Workout::getTitle))
					.as("nicht jeder Tag dieselbe Grundlage").anyMatch(title -> title.contains("Antritte"));
		}
	}

	@Test
	void recoveryWeeksWithFewDaysKeepAllTrainingDays() {
		// Di, Do, Sa, So: kein zusätzlicher freier Tag
		List<PlannedWorkout> plan = generator.generate(athlete(Level.AMBITIOUS, MONDAY.plusWeeks(16).plusDays(6)),
				MONDAY);
		var recoveryWeeks = plan.stream().filter(planned -> planned.getPhase() == Phase.RECOVERY)
				.collect(Collectors.groupingBy(planned -> planned.getDate().with(DayOfWeek.MONDAY)));
		assertThat(recoveryWeeks).isNotEmpty();
		assertThat(recoveryWeeks.values()).allMatch(week -> week.size() == 4);
	}
}
