package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.EventGoal;
import io.github.jakobfa.webtechproject.model.EventType;
import io.github.jakobfa.webtechproject.model.Level;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/** Krafttraining im Plan: nur auf Wunsch, nach festen Regeln rund um die Radeinheiten */
class StrengthTrainingTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 2); // ein Montag
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final PlanGenerator GENERATOR = new PlanGenerator(LIBRARY);

	private static Athlete athlete(boolean strength, double... hoursMoToSu) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(hoursMoToSu[0]);
		availability.setTuesday(hoursMoToSu[1]);
		availability.setWednesday(hoursMoToSu[2]);
		availability.setThursday(hoursMoToSu[3]);
		availability.setFriday(hoursMoToSu[4]);
		availability.setSaturday(hoursMoToSu[5]);
		availability.setSunday(hoursMoToSu[6]);
		Event event = new Event();
		event.setName("Rund um Sachsen");
		event.setDate(START.plusWeeks(16).with(DayOfWeek.SATURDAY));
		event.setDistanceKm(160.0);
		event.setElevationMeters(1800);
		event.setType(EventType.GRAN_FONDO);
		event.setGoal(EventGoal.GOOD_TIME);
		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setLevel(Level.AMBITIOUS);
		athlete.setAvailability(availability);
		athlete.setEvent(event);
		athlete.setStrengthTraining(strength);
		return athlete;
	}

	private static List<PlannedWorkout> plan(Athlete athlete) {
		return GENERATOR.generateSeason(athlete, START, Map.of(), List.of());
	}

	private static WorkoutType typeOf(PlannedWorkout planned) {
		return LIBRARY.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow().getType();
	}

	private static List<PlannedWorkout> strength(List<PlannedWorkout> plan) {
		return plan.stream().filter(planned -> typeOf(planned) == WorkoutType.STRENGTH).toList();
	}

	private static Map<LocalDate, Long> perWeek(List<PlannedWorkout> sessions) {
		return sessions.stream().collect(Collectors.groupingBy(
				planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
				Collectors.counting()));
	}

	@Test
	void withoutTheTickThereIsNoStrengthTraining() {
		assertThat(strength(plan(athlete(false, 1, 1, 1, 1, 1, 3, 2)))).isEmpty();
	}

	@Test
	void ridesStayExactlyTheSame() {
		List<PlannedWorkout> without = plan(athlete(false, 1, 1, 1, 1, 1, 3, 2));
		List<PlannedWorkout> with = plan(athlete(true, 1, 1, 1, 1, 1, 3, 2));

		assertThat(with.stream().filter(planned -> typeOf(planned) != WorkoutType.STRENGTH)
				.map(planned -> planned.getDate() + " " + planned.getWorkoutId() + " " + planned.getDurationMinutes()))
				.containsExactlyElementsOf(without.stream()
						.map(planned -> planned.getDate() + " " + planned.getWorkoutId() + " "
								+ planned.getDurationMinutes())
						.toList());
	}

	@Test
	void moreInTheBasePhaseLessLaterNoneInTheTaper() {
		List<PlannedWorkout> sessions = strength(plan(athlete(true, 1, 1, 1, 1, 1, 3, 2)));

		Map<Phase, Set<Long>> sessionsPerPhase = sessions.stream().collect(Collectors.groupingBy(
				PlannedWorkout::getPhase, Collectors.mapping(PlannedWorkout::getWorkoutId, Collectors.toSet())));
		assertThat(sessionsPerPhase.get(Phase.BASE))
				.containsExactlyInAnyOrder(WorkoutLibrary.STRENGTH_BASE_A, WorkoutLibrary.STRENGTH_BASE_B);
		assertThat(sessionsPerPhase.get(Phase.BUILD)).containsExactly(WorkoutLibrary.STRENGTH_MAINTENANCE);
		assertThat(sessionsPerPhase).doesNotContainKey(Phase.TAPER);
		assertThat(perWeek(sessions).values()).allMatch(count -> count <= 2);
	}

	@Test
	void legsAreFreshForHardRidesAndTheEvent() {
		Athlete athlete = athlete(true, 1, 1, 1, 1, 1, 3, 2);
		List<PlannedWorkout> plan = plan(athlete);
		Map<LocalDate, List<WorkoutType>> ridesPerDay = plan.stream()
				.filter(planned -> typeOf(planned) != WorkoutType.STRENGTH)
				.collect(Collectors.groupingBy(PlannedWorkout::getDate,
						Collectors.mapping(StrengthTrainingTest::typeOf, Collectors.toList())));
		LocalDate event = athlete.getEvent().getDate();

		List<LocalDate> days = strength(plan).stream().map(PlannedWorkout::getDate).sorted().toList();
		assertThat(days).isNotEmpty();
		for (LocalDate day : days) {
			assertThat(ridesPerDay.getOrDefault(day, List.of())).as("am selben Tag %s", day)
					.noneMatch(PlanGenerator.DEMANDING::contains);
			assertThat(ridesPerDay.getOrDefault(day.plusDays(1), List.of())).as("am Tag nach %s", day)
					.noneMatch(PlanGenerator.DEMANDING::contains);
			assertThat(day).isBefore(event.minusDays(1));
		}
		for (int i = 1; i < days.size(); i++) {
			assertThat(days.get(i)).as("nie zwei Tage hintereinander").isAfter(days.get(i - 1).plusDays(1));
		}
	}

	@Test
	void onlyOnDaysWithTime() {
		// Mo und Fr ohne Zeit
		List<PlannedWorkout> sessions = strength(plan(athlete(true, 0, 1, 1, 1, 0, 3, 2)));

		assertThat(sessions).isNotEmpty().noneMatch(planned -> planned.getDate().getDayOfWeek() == DayOfWeek.MONDAY
				|| planned.getDate().getDayOfWeek() == DayOfWeek.FRIDAY);
	}

}
