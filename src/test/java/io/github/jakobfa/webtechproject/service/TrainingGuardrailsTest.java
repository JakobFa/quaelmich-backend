package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * Sportliche Leitplanken für den Wochenumfang und die langen Tage: Die Zeit ist immer die Obergrenze, darunter
 * steigt der Umfang gleichmäßig, die Level starten unterschiedlich hoch, und zwei lange Tage hintereinander gibt es
 * nur gezielt (starke Level, lange Events, jede zweite Belastungswoche, nie in Erholung und Tapering).
 */
class TrainingGuardrailsTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // ein Montag
	private static final int LONG_DAY_MINUTES = 180;
	private static final double LONG_DAY_SHARE = 0.75;

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	record Rider(String name, Athlete athlete, List<PlannedWorkout> plan) {

		@Override
		public String toString() {
			return name;
		}

		Level level() {
			return athlete.getLevel();
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
				rider("Einsteigerin, 4 h, Radmarathon", Level.BEGINNER, EventGoal.FINISH, EventType.GRAN_FONDO, 100,
						800, 12, 0, 1, 0, 1, 0, 2, 0),
				rider("Hobby, 6 h, Gran Fondo", Level.HOBBY, EventGoal.GOOD_TIME, EventType.GRAN_FONDO, 160, 2000,
						16, 0, 1, 0, 1.5, 0, 2, 1.5),
				rider("Hobby, 14 h, Ultra", Level.HOBBY, EventGoal.FINISH, EventType.ULTRA, 900, 8500, 27, 2, 2, 1,
						2, 2, 5, 5),
				rider("Ambitioniert, 10 h, Ötztaler", Level.AMBITIOUS, EventGoal.COMPETE, EventType.GRAN_FONDO, 227,
						5500, 20, 0, 1.5, 1, 1.5, 0, 4, 2),
				rider("Ambitioniert, 8 h, Ultra", Level.AMBITIOUS, EventGoal.FINISH, EventType.ULTRA, 600, 6000, 24,
						0, 1, 1, 1, 0, 3, 2),
				rider("Leistung, 19 h, Ultra 900 km", Level.PERFORMANCE, EventGoal.COMPETE, EventType.ULTRA, 900,
						8500, 27, 2, 2, 1, 2, 2, 5, 5),
				rider("Leistung, 12 h, Kriterium", Level.PERFORMANCE, EventGoal.COMPETE, EventType.CRITERIUM, 50,
						200, 10, 1, 1.5, 2, 1.5, 0, 3, 3));
	}

	private static int minutes(PlannedWorkout planned) {
		return LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow()
				.getDurationInMinutes();
	}

	/** Montag → Workouts der Woche, ohne die Event-Woche */
	private static Map<LocalDate, List<PlannedWorkout>> weeks(Rider rider) {
		LocalDate eventMonday = rider.athlete().getEvent().getDate().with(TemporalAdjusters.previousOrSame(
				DayOfWeek.MONDAY));
		return rider.plan().stream().filter(planned -> planned.getDate().isBefore(eventMonday))
				.collect(Collectors.groupingBy(
						planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
						TreeMap::new, Collectors.toList()));
	}

	private static int weekMinutes(List<PlannedWorkout> week) {
		return week.stream().mapToInt(TrainingGuardrailsTest::minutes).sum();
	}

	private static boolean isLoad(List<PlannedWorkout> week) {
		Phase phase = week.getFirst().getPhase();
		return PlanGenerator.isLoadPhase(phase);
	}

	private static List<PlannedWorkout> longDays(List<PlannedWorkout> week) {
		int longest = week.stream().mapToInt(TrainingGuardrailsTest::minutes).max().orElse(0);
		// "lang" = mindestens drei Stunden und nah an der längsten Fahrt der Woche (ab 75 %)
		return week.stream().filter(planned -> minutes(planned) >= LONG_DAY_MINUTES
				&& minutes(planned) >= longest * LONG_DAY_SHARE)
				.sorted(Comparator.comparing(PlannedWorkout::getDate)).toList();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void neverMoreThanTheTimeOfADay(Rider rider) {
		assertThat(rider.plan()).allSatisfy(planned -> assertThat(minutes(planned)).isLessThanOrEqualTo(
				(int) (rider.athlete().getAvailability().hoursOn(planned.getDate().getDayOfWeek()) * 60)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void anFtpTestIsPrecededByARestDay(Rider rider) {
		var daysWithWorkouts = rider.plan().stream().map(PlannedWorkout::getDate).collect(Collectors.toSet());
		LocalDate first = rider.plan().stream().map(PlannedWorkout::getDate).min(LocalDate::compareTo).orElseThrow();
		rider.plan().stream().filter(planned -> planned.getWorkoutId() != null
				&& planned.getWorkoutId() == WorkoutLibrary.FTP_TEST && planned.getDate().isAfter(first))
				.forEach(test -> assertThat(daysWithWorkouts).as("Tag vor dem FTP-Test am %s", test.getDate())
						.doesNotContain(test.getDate().minusDays(1)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void loadWeeksGrowByAboutTenPercentAtMost(Rider rider) {
		Integer previous = null;
		for (var entry : weeks(rider).entrySet()) {
			List<PlannedWorkout> week = entry.getValue();
			if (!isLoad(week)) {
				continue;
			}
			int minutes = weekMinutes(week);
			if (previous != null && entry.getKey().isAfter(START)) {
				// harte Einheiten bleiben beim Kürzen für den Deckel unangetastet: etwas Luft
				assertThat(minutes).as("Woche ab %s", entry.getKey())
						.isLessThanOrEqualTo((int) Math.max(previous * 1.15, previous + 45));
			}
			previous = minutes;
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void recoveryWeeksAreAtMostThreeQuartersOfTheWeekBefore(Rider rider) {
		Integer lastLoad = null;
		for (var entry : weeks(rider).entrySet()) {
			List<PlannedWorkout> week = entry.getValue();
			if (week.getFirst().getPhase() == Phase.RECOVERY && lastLoad != null) {
				assertThat(weekMinutes(week)).as("Woche ab %s", entry.getKey())
						.isLessThanOrEqualTo((int) (lastLoad * 0.78));
			}
			if (isLoad(week)) {
				lastLoad = weekMinutes(week);
			}
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void atMostTwoLongDaysAWeekAndTheyAreNeverBackToBackWithoutPurpose(Rider rider) {
		weeks(rider).forEach((monday, week) -> {
			List<PlannedWorkout> longDays = longDays(week);
			assertThat(longDays).as("Woche ab %s", monday).hasSizeLessThanOrEqualTo(2);
			if (longDays.size() == 2 && ChronoUnit.DAYS.between(longDays.get(0).getDate(),
					longDays.get(1).getDate()) == 1) {
				assertThat(rider.level()).as("Back-to-back nur ab Ambitioniert").isIn(Level.AMBITIOUS,
						Level.PERFORMANCE);
				assertThat(week.getFirst().getPhase()).as("Woche ab %s", monday).isIn(Phase.BASE, Phase.BUILD,
						Phase.PEAK);
				assertThat(minutes(longDays.get(1))).as("der zweite Tag ist nicht länger als der erste")
						.isLessThanOrEqualTo(minutes(longDays.get(0)));
			}
		});
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void backToBackOnlyInEveryOtherLoadWeek(Rider rider) {
		boolean previousWasBackToBack = false;
		for (var entry : weeks(rider).entrySet()) {
			List<PlannedWorkout> longDays = longDays(entry.getValue());
			boolean backToBack = longDays.size() == 2 && ChronoUnit.DAYS.between(longDays.get(0).getDate(),
					longDays.get(1).getDate()) == 1;
			if (isLoad(entry.getValue())) {
				assertThat(backToBack && previousWasBackToBack).as("Woche ab %s", entry.getKey()).isFalse();
				previousWasBackToBack = backToBack;
			}
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("riders")
	void theDayAfterTheLongDaysIsNotHard(Rider rider) {
		weeks(rider).forEach((monday, week) -> {
			List<PlannedWorkout> longDays = longDays(week);
			if (longDays.isEmpty()) {
				return;
			}
			LocalDate after = longDays.getLast().getDate().plusDays(1);
			week.stream().filter(planned -> planned.getDate().equals(after)).forEach(planned -> assertThat(
					LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType())
					.as("Tag nach der langen Ausfahrt (%s)", after)
					.isIn(WorkoutType.RECOVERY, WorkoutType.ENDURANCE, WorkoutType.LONG_RIDE));
		});
	}

	@Test
	void higherLevelsStartWithMoreOfTheirTimeAndTimePoorRidersUseTheirTimeBetter() {
		assertThat(PlanGenerator.startVolume(Level.BEGINNER, 12)).isLessThan(PlanGenerator.startVolume(Level.HOBBY, 12));
		assertThat(PlanGenerator.startVolume(Level.HOBBY, 12)).isLessThan(PlanGenerator.startVolume(Level.AMBITIOUS, 12));
		assertThat(PlanGenerator.startVolume(Level.AMBITIOUS, 12))
				.isLessThan(PlanGenerator.startVolume(Level.PERFORMANCE, 12));
		assertThat(PlanGenerator.startVolume(Level.HOBBY, 5)).isGreaterThan(PlanGenerator.startVolume(Level.HOBBY, 12));
		assertThat(PlanGenerator.startVolume(Level.PERFORMANCE, 5)).isLessThanOrEqualTo(1.0);
	}

	@Test
	void backToBackNeedsAStrongLevelALongEventAndAnOddLoadWeek() {
		EventProfile ultra = EventProfile.of(riders().filter(r -> r.name().startsWith("Leistung, 19")).findFirst()
				.orElseThrow().athlete());
		assertThat(PlanGenerator.backToBack(Level.PERFORMANCE, ultra, Phase.BUILD, 1)).isTrue();
		assertThat(PlanGenerator.backToBack(Level.PERFORMANCE, ultra, Phase.BUILD, 2)).isFalse();
		assertThat(PlanGenerator.backToBack(Level.PERFORMANCE, ultra, Phase.RECOVERY, 1)).isFalse();
		assertThat(PlanGenerator.backToBack(Level.PERFORMANCE, ultra, Phase.TAPER, 1)).isFalse();
		assertThat(PlanGenerator.backToBack(Level.HOBBY, ultra, Phase.BUILD, 1)).isFalse();
		assertThat(PlanGenerator.backToBack(Level.BEGINNER, ultra, Phase.BUILD, 1)).isFalse();
	}
}
