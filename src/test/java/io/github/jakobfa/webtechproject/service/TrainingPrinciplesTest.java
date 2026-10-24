package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Trainingslehre als Tests: Für typische Fahrer werden komplette Pläne erzeugt und gegen Grundregeln geprüft,
 * die unter Trainern unstrittig sind. Bricht eine Änderung am Plan-Generator eine Regel, wird die CI rot.
 */
class TrainingPrinciplesTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // ein Montag
	private static final Set<WorkoutType> EASY = Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE,
			WorkoutType.LONG_RIDE);
	private static final long OPENER = 28L;

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	/** Ein Beispiel-Fahrer mit seinem Plan */
	record Rider(String name, Athlete athlete, List<PlannedWorkout> plan) {

		@Override
		public String toString() {
			return name;
		}

		LocalDate eventDate() {
			return athlete.getEvent().getDate();
		}
	}

	private static Rider rider(String name, Level level, EventGoal goal, EventType type, double km, int meters,
			int weeks, double... hoursMoToSu) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(hoursMoToSu[0]);
		availability.setTuesday(hoursMoToSu[1]);
		availability.setWednesday(hoursMoToSu[2]);
		availability.setThursday(hoursMoToSu[3]);
		availability.setFriday(hoursMoToSu[4]);
		availability.setSaturday(hoursMoToSu[5]);
		availability.setSunday(hoursMoToSu[6]);

		Event event = new Event();
		event.setName(name);
		event.setDate(START.plusWeeks(weeks).with(DayOfWeek.SATURDAY));
		event.setDistanceKm(km);
		event.setElevationMeters(meters);
		event.setType(type);
		event.setGoal(goal);

		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setLevel(level);
		athlete.setEvent(event);
		athlete.setAvailability(availability);
		return new Rider(name, athlete, GENERATOR.generate(athlete, START));
	}

	static Stream<Rider> riders() {
		return Stream.of(
				rider("Einsteigerin, 4 h, erster Radmarathon", Level.BEGINNER, EventGoal.FINISH,
						EventType.GRAN_FONDO, 100, 800, 12, 0, 1, 0, 1, 0, 2, 0),
				rider("Hobby, 6 h, Gran Fondo", Level.HOBBY, EventGoal.GOOD_TIME, EventType.GRAN_FONDO, 160, 2000,
						16, 0, 1, 0, 1.5, 0, 2, 1.5),
				rider("Ambitioniert, 10 h, Ötztaler vorne", Level.AMBITIOUS, EventGoal.COMPETE,
						EventType.GRAN_FONDO, 227, 5500, 20, 0, 1.5, 1, 1.5, 0, 4, 2),
				rider("Leistung, 12 h, Kriterium", Level.PERFORMANCE, EventGoal.COMPETE, EventType.CRITERIUM, 50,
						200, 10, 1, 1.5, 2, 1.5, 0, 3, 3),
				rider("Ambitioniert, 8 h, Ultra ankommen", Level.AMBITIOUS, EventGoal.FINISH, EventType.ULTRA, 600,
						6000, 24, 0, 1, 1, 1, 0, 3, 2),
				rider("Hobby, 7 h, Zeitfahren", Level.HOBBY, EventGoal.GOOD_TIME, EventType.TIME_TRIAL, 40, 300, 8,
						0, 1.5, 0, 1.5, 1, 2, 1));
	}

	// ---------- Hilfen ----------

	private static Workout workoutOf(PlannedWorkout planned) {
		return LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

	private static boolean isHard(PlannedWorkout planned) {
		return planned.getWorkoutId() != OPENER && !EASY.contains(workoutOf(planned).getType());
	}

	/** Montag → Workouts der Woche, ohne die Event-Woche */
	private static Map<LocalDate, List<PlannedWorkout>> trainingWeeks(Rider rider) {
		LocalDate eventMonday = rider.eventDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return rider.plan().stream()
				.filter(planned -> planned.getDate().isBefore(eventMonday))
				.collect(Collectors.groupingBy(
						planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
						TreeMap::new, Collectors.toList()));
	}

	private static int tss(List<PlannedWorkout> workouts) {
		return workouts.stream().mapToInt(planned -> workoutOf(planned).getTss()).sum();
	}

	private static Phase phaseOf(List<PlannedWorkout> week) {
		return week.getFirst().getPhase();
	}

	// ---------- Grundregeln ----------

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void neverMoreThanTheAvailableTime(Rider rider) {
		assertThat(rider.plan()).isNotEmpty().allSatisfy(planned -> assertThat(workoutOf(planned)
				.getDurationInMinutes())
				.isLessThanOrEqualTo((int) (rider.athlete().getAvailability()
						.hoursOn(planned.getDate().getDayOfWeek()) * 60)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void neverTwoHardDaysInARow(Rider rider) {
		List<LocalDate> hardDays = rider.plan().stream().filter(TrainingPrinciplesTest::isHard)
				.map(PlannedWorkout::getDate).sorted().toList();
		for (int i = 1; i < hardDays.size(); i++) {
			assertThat(ChronoUnit.DAYS.between(hardDays.get(i - 1), hardDays.get(i)))
					.as("harte Tage %s und %s", hardDays.get(i - 1), hardDays.get(i)).isGreaterThan(1);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void atMostThreeHardSessionsAWeekAndTwoForBeginners(Rider rider) {
		int max = rider.athlete().getLevel() == Level.BEGINNER ? 2 : 3;
		trainingWeeks(rider).forEach((monday, week) -> assertThat(week.stream().filter(TrainingPrinciplesTest::isHard))
				.as("Woche ab %s", monday).hasSizeLessThanOrEqualTo(max));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void everyWeekHasAerobicVolume(Rider rider) {
		trainingWeeks(rider).forEach((monday, week) -> assertThat(week)
				.as("Woche ab %s: Grundlage gehört in jede Woche", monday)
				.anyMatch(planned -> EASY.contains(workoutOf(planned).getType())));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void loadNeverJumpsFarAboveThePreviousPeak(Rider rider) {
		int peakSoFar = 0;
		for (var entry : trainingWeeks(rider).entrySet()) {
			int weekTss = tss(entry.getValue());
			if (peakSoFar > 0) {
				// höchstens 20 % über allem bisher Geplanten – bei kleinen Umfängen bis +50 TSS
				// (entspricht grob der üblichen Faustregel für die Steigerung der Fitness pro Woche)
				int limit = Math.max((int) (peakSoFar * 1.2), peakSoFar + 50);
				assertThat(weekTss).as("Woche ab %s", entry.getKey()).isLessThanOrEqualTo(limit);
			}
			peakSoFar = Math.max(peakSoFar, weekTss);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void recoveryWeeksAreClearlyEasier(Rider rider) {
		List<List<PlannedWorkout>> weeks = new ArrayList<>(trainingWeeks(rider).values());
		for (int i = 1; i < weeks.size(); i++) {
			if (phaseOf(weeks.get(i)) == Phase.RECOVERY) {
				assertThat(tss(weeks.get(i))).as("Erholungswoche %d", i)
						.isLessThan((int) (tss(weeks.get(i - 1)) * 0.85));
			}
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void taperingReducesLoadButKeepsSomeIntensity(Rider rider) {
		Map<LocalDate, List<PlannedWorkout>> weeks = trainingWeeks(rider);
		int peak = weeks.values().stream().mapToInt(TrainingPrinciplesTest::tss).max().orElseThrow();
		List<PlannedWorkout> lastWeek = weeks.get(rider.eventDate().with(TemporalAdjusters.previousOrSame(
				DayOfWeek.MONDAY)).minusWeeks(1));
		LocalDate eventMonday = rider.eventDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		List<PlannedWorkout> eventWeek = rider.plan().stream().filter(p -> !p.getDate().isBefore(eventMonday))
				.toList();

		assertThat(tss(lastWeek)).as("Woche vor dem Event").isLessThan((int) (peak * 0.85));
		assertThat(tss(eventWeek)).as("Event-Woche").isLessThan((int) (peak * 0.6));
		assertThat(lastWeek).as("Woche vor dem Event: noch etwas Intensität").anyMatch(TrainingPrinciplesTest::isHard);
		assertThat(eventWeek.stream().filter(TrainingPrinciplesTest::isHard)).as("Event-Woche: höchstens ein Schärfer")
				.hasSizeLessThanOrEqualTo(1);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void freshForTheEvent(Rider rider) {
		LocalDate event = rider.eventDate();
		assertThat(rider.plan()).as("zwei Tage vorher Ruhe")
				.noneMatch(planned -> planned.getDate().equals(event.minusDays(2)));
		rider.plan().stream().filter(planned -> planned.getDate().equals(event.minusDays(1)))
				.forEach(planned -> assertThat(workoutOf(planned).getDurationInMinutes()).as("Vortag kurz")
						.isLessThanOrEqualTo(45));
		assertThat(rider.plan()).as("am Event-Tag selbst kein Training")
				.noneMatch(planned -> !planned.getDate().isBefore(event));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void longRidesGrowAndAreLongestInBuildOrPeak(Rider rider) {
		// gleich lang ist ok – oft begrenzt die verfügbare Zeit am Wochenende schon früh
		int base = longestIn(rider, Phase.BASE);
		int buildOrPeak = Math.max(longestIn(rider, Phase.BUILD), longestIn(rider, Phase.PEAK));
		int taper = longestIn(rider, Phase.TAPER);
		assertThat(buildOrPeak).as("Aufbau/Spitze mindestens so lang wie Grundlage").isGreaterThanOrEqualTo(base);
		assertThat(taper).as("im Tapering kürzer").isLessThan(buildOrPeak);
	}

	private static int longestIn(Rider rider, Phase phase) {
		return rider.plan().stream().filter(p -> p.getPhase() == phase)
				.mapToInt(p -> workoutOf(p).getDurationInMinutes()).max().orElse(0);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void mostTimeIsEasy(Rider rider) {
		int total = rider.plan().stream().mapToInt(p -> workoutOf(p).getDurationInMinutes()).sum();
		int hard = rider.plan().stream().filter(TrainingPrinciplesTest::isHard)
				.mapToInt(p -> workoutOf(p).getDurationInMinutes()).sum();
		// pyramidal/polarisiert: deutlich mehr als die Hälfte der Zeit locker
		assertThat((double) hard / total).as("Anteil harter Einheiten an der Zeit").isLessThan(0.4);
	}

	@Test
	void beginnersGetNoAnaerobicSprintOrOverUnderWork() {
		Rider beginner = riders().findFirst().orElseThrow();
		assertThat(beginner.plan())
				.filteredOn(planned -> planned.getWorkoutId() != OPENER)
				.extracting(planned -> workoutOf(planned).getType())
				.doesNotContain(WorkoutType.ANAEROBIC, WorkoutType.SPRINT, WorkoutType.OVER_UNDER);
	}

	@Test
	void finishGoalIsGentlerThanCompeting() {
		int finish = hardTss(rider("x", Level.AMBITIOUS, EventGoal.FINISH, EventType.GRAN_FONDO, 227, 5500, 20,
				0, 1.5, 1, 1.5, 0, 4, 2));
		int compete = hardTss(rider("x", Level.AMBITIOUS, EventGoal.COMPETE, EventType.GRAN_FONDO, 227, 5500, 20,
				0, 1.5, 1, 1.5, 0, 4, 2));
		assertThat(finish).isLessThan(compete);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void ftpIsTestedRegularlyButNotRightBeforeTheEvent(Rider rider) {
		List<LocalDate> tests = rider.plan().stream()
				.filter(planned -> workoutOf(planned).getType() == WorkoutType.FTP_TEST)
				.map(PlannedWorkout::getDate).sorted().toList();
		long planWeeks = ChronoUnit.WEEKS.between(START, rider.eventDate());
		if (planWeeks >= 14 && rider.athlete().getLevel() != Level.BEGINNER) {
			assertThat(tests).as("lange Pläne: mehr als ein Test").hasSizeGreaterThanOrEqualTo(2);
		}
		for (int i = 1; i < tests.size(); i++) {
			assertThat(ChronoUnit.WEEKS.between(tests.get(i - 1), tests.get(i))).as("Abstand zwischen Tests")
					.isGreaterThanOrEqualTo(5);
		}
		assertThat(tests).allMatch(date -> ChronoUnit.DAYS.between(date, rider.eventDate()) >= 14);
	}

	private static int hardTss(Rider rider) {
		return tss(rider.plan().stream().filter(TrainingPrinciplesTest::isHard).toList());
	}

}
