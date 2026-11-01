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

/**
 * Zwei Regeln, die ein Trainer von jedem Plan erwartet: jede Woche ein Pausentag, und der Umfang springt nicht von
 * einer Woche zur nächsten. Beide gelten auch dann, wenn jemand für eine einzelne Woche viel mehr Zeit einträgt.
 */
class PlanRestAndGrowthTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final PlanGenerator generator = new PlanGenerator(library);

	private static WeeklyAvailability hours(double mon, double tue, double wed, double thu, double fri, double sat,
			double sun) {
		WeeklyAvailability availability = new WeeklyAvailability();
		availability.setMonday(mon);
		availability.setTuesday(tue);
		availability.setWednesday(wed);
		availability.setThursday(thu);
		availability.setFriday(fri);
		availability.setSaturday(sat);
		availability.setSunday(sun);
		return availability;
	}

	private static Athlete athlete(WeeklyAvailability availability) {
		Event event = new Event();
		event.setName("Ultra");
		event.setDate(MONDAY.plusWeeks(30).plusDays(5));
		event.setDistanceKm(900.0);
		event.setElevationMeters(8000);
		event.setType(EventType.ULTRA);

		Athlete athlete = new Athlete();
		athlete.setFtp(250);
		athlete.setWeightKg(72.0);
		athlete.setLevel(Level.PERFORMANCE);
		athlete.setEvent(event);
		athlete.setAvailability(availability);
		return athlete;
	}

	private Map<LocalDate, List<PlannedWorkout>> byWeek(List<PlannedWorkout> plan) {
		Map<LocalDate, List<PlannedWorkout>> weeks = new TreeMap<>();
		plan.forEach(planned -> weeks.computeIfAbsent(planned.getDate().with(TemporalAdjusters.previousOrSame(
				DayOfWeek.MONDAY)), monday -> new java.util.ArrayList<>()).add(planned));
		return weeks;
	}

	private int minutes(List<PlannedWorkout> week) {
		return week.stream().mapToInt(planned -> library.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
				.orElseThrow().getDurationInMinutes()).sum();
	}

	private static boolean isLoadWeek(List<PlannedWorkout> week) {
		return week.stream().allMatch(planned -> planned.getPhase() == Phase.BASE || planned.getPhase() == Phase.BUILD
				|| planned.getPhase() == Phase.PEAK);
	}

	@Test
	void aWeekWithTimeOnEveryDayHasARestDay() {
		// jeden Tag Zeit, auch am Mittwoch
		Athlete athlete = athlete(hours(2, 2, 2, 2, 2, 4, 4));

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generate(athlete, MONDAY));

		assertThat(weeks.values()).filteredOn(week -> week.getFirst().getPhase() != Phase.TAPER)
				.allSatisfy(week -> assertThat(week).hasSizeLessThanOrEqualTo(6));
	}

	@Test
	void theRestDayIsTheShortestOneNearTheMiddleOfTheWeek() {
		// Mo 5 · Di 2 · Mi 2 · Do 2 · Fr 2 · Sa 5 · So 5 – dein Fall: frei bleibt der Mittwoch
		Athlete athlete = athlete(hours(5, 2, 2, 2, 2, 5, 5));

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generate(athlete, MONDAY));

		assertThat(weeks.values()).allSatisfy(week -> assertThat(week)
				.noneMatch(planned -> planned.getDate().getDayOfWeek() == DayOfWeek.WEDNESDAY
						&& planned.getPhase() != Phase.TAPER));
	}

	@Test
	void daysWithoutTimeStayFreeAndNoExtraDayIsDropped() {
		// Mittwoch hat schon keine Zeit: das ist der Pausentag, sonst entfällt nichts
		Athlete athlete = athlete(hours(2, 2, 0, 2, 2, 4, 4));

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generate(athlete, MONDAY));

		LocalDate secondWeek = MONDAY.plusWeeks(1);
		assertThat(weeks.get(secondWeek)).hasSize(6);
	}

	@Test
	void aWeekWithMuchMoreTimeDoesNotJumpInVolume() {
		// Normale Woche mit wenig Zeit, danach trägt jemand für eine Woche 5 h an drei Tagen ein
		Athlete athlete = athlete(hours(1, 2, 0, 2, 2.25, 0, 1));
		Map<LocalDate, WeeklyAvailability> overrides = Map.of(MONDAY.plusWeeks(2), hours(5, 2, 2, 2, 2, 5, 5));

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generateSeason(athlete, MONDAY, overrides,
				List.of()));

		int before = minutes(weeks.get(MONDAY.plusWeeks(1)));
		int jump = minutes(weeks.get(MONDAY.plusWeeks(2)));
		assertThat(jump).isLessThanOrEqualTo(PlanGenerator.weeklyCap(before));
		assertThat(jump).as("trotzdem nicht weniger als vorher").isGreaterThanOrEqualTo(before);
	}

	@Test
	void everyLoadWeekGrowsAtMostByTheCap() {
		Athlete athlete = athlete(hours(1, 2, 0, 2, 2.25, 0, 1));
		// Zeit springt von Woche zu Woche
		Map<LocalDate, WeeklyAvailability> overrides = new TreeMap<>();
		for (int week = 1; week < 12; week++) {
			overrides.put(MONDAY.plusWeeks(week), hours(5, 3, 3, 3, 3, 6, 6));
		}

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generateSeason(athlete, MONDAY, overrides,
				List.of()));

		double reference = 0;
		for (Map.Entry<LocalDate, List<PlannedWorkout>> entry : weeks.entrySet()) {
			List<PlannedWorkout> week = entry.getValue();
			if (!isLoadWeek(week) || entry.getKey().equals(MONDAY)) {
				continue;
			}
			if (reference > 0) {
				assertThat(minutes(week)).as(entry.getKey().toString())
						.isLessThanOrEqualTo(PlanGenerator.weeklyCap(reference) + PlanGenerator.MIN_CAPPED_RIDE);
			}
			reference = Math.max(minutes(week), reference * PlanGenerator.REFERENCE_DECAY);
		}
	}

	@Test
	void theLongRideStaysTheLongestRideWhenTheWeekIsCapped() {
		Athlete athlete = athlete(hours(1, 2, 0, 2, 2.25, 0, 1));
		Map<LocalDate, WeeklyAvailability> overrides = Map.of(MONDAY.plusWeeks(2), hours(5, 2, 2, 2, 2, 5, 5));

		List<PlannedWorkout> capped = byWeek(generator.generateSeason(athlete, MONDAY, overrides, List.of()))
				.get(MONDAY.plusWeeks(2));

		int longest = capped.stream().mapToInt(planned -> library.resolve(planned.getWorkoutId(),
				planned.getDurationMinutes()).orElseThrow().getDurationInMinutes()).max().orElseThrow();
		PlannedWorkout sunday = capped.stream().filter(planned -> planned.getDate().getDayOfWeek() == DayOfWeek.SUNDAY)
				.findFirst().orElseThrow();
		assertThat(library.resolve(sunday.getWorkoutId(), sunday.getDurationMinutes()).orElseThrow()
				.getDurationInMinutes()).isEqualTo(longest);
	}

	@Test
	void theCapIsTenPercentButAtLeastHalfAnHour() {
		assertThat(PlanGenerator.weeklyCap(300)).isEqualTo(330);
		assertThat(PlanGenerator.weeklyCap(100)).isEqualTo(130);
	}

	@Test
	void aRecoveryWeekIsLighterThanTheLoadWeekBefore() {
		assertThat(PlanGenerator.weekCap(Phase.RECOVERY, 600)).isEqualTo(420);
		assertThat(PlanGenerator.weekCap(Phase.TAPER, 600)).isNull();
		assertThat(PlanGenerator.weekCap(Phase.BASE, 0)).isNull();
	}

	@Test
	void recoveryWeeksInThePlanAreNeverLongerThanTheLoadWeekBefore() {
		Athlete athlete = athlete(hours(1, 2, 0, 2, 2.25, 0, 1));
		Map<LocalDate, WeeklyAvailability> overrides = new TreeMap<>();
		for (int week = 1; week < 12; week++) {
			overrides.put(MONDAY.plusWeeks(week), hours(5, 3, 3, 3, 3, 6, 6));
		}

		Map<LocalDate, List<PlannedWorkout>> weeks = byWeek(generator.generateSeason(athlete, MONDAY, overrides,
				List.of()));

		int previousLoad = 0;
		for (List<PlannedWorkout> week : weeks.values()) {
			if (isLoadWeek(week)) {
				previousLoad = minutes(week);
			} else if (week.getFirst().getPhase() == Phase.RECOVERY && previousLoad > 0) {
				assertThat(minutes(week)).isLessThanOrEqualTo(previousLoad);
			}
		}
	}
}
