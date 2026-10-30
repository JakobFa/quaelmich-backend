package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;

class ProgressServiceTest {

	/** Mittwoch */
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final ProgressService service = new ProgressService(null, null, null, library, null);

	private PlannedWorkout planned(LocalDate date, boolean completed) {
		PlannedWorkout planned = new PlannedWorkout(date, library.findById(5L).orElseThrow(), null, Phase.BASE);
		planned.setCompleted(completed);
		return planned;
	}

	/** Abgehakte Einheit mit tatsächlichem TSS */
	private PlannedWorkout done(LocalDate date, int actualTss) {
		PlannedWorkout workout = planned(date, true);
		workout.setActualTss(actualTss);
		return workout;
	}

	/** Plan mit Startdatum, damit der chronische Schnitt eine Historie hat */
	private static TrainingPlan planFrom(LocalDate start) {
		return new TrainingPlan(new io.github.jakobfa.webtechproject.model.Athlete(), start);
	}

	@Test
	void levelsGrowWithTrainingLoad() {
		assertThat(ProgressService.levelFor(0).name()).isEqualTo("Kettenfett-Neuling");
		assertThat(ProgressService.levelFor(249).number()).isEqualTo(1);
		assertThat(ProgressService.levelFor(250).name()).isEqualTo("Schlauchflicker");
		assertThat(ProgressService.nextLevel(300).fromXp()).isEqualTo(600);
		assertThat(ProgressService.levelFor(15000).name()).isEqualTo("Quälmeister");
		assertThat(ProgressService.nextLevel(15000).name()).isEqualTo("Quälmeister II");
		assertThat(ProgressService.levelFor(23500).name()).isEqualTo("Quälmeister III");
		assertThat(ProgressService.levelFor(23500).number()).isEqualTo(14);
	}

	@Test
	void xpComesFromRiddenLoadOrPlannedLoad() {
		PlannedWorkout ridden = planned(TODAY, true);
		ridden.setActivityId("i1");
		ridden.setActualTss(80);
		assertThat(service.xpOf(ridden)).isEqualTo(80);

		PlannedWorkout tickedOff = planned(TODAY, true);
		assertThat(service.xpOf(tickedOff)).as("geplanter TSS").isEqualTo(
				library.findById(5L).orElseThrow().getTss());

		assertThat(service.xpOf(planned(TODAY, false))).isZero();

		ExtraRide commute = new ExtraRide(null, "strava-1");
		commute.setDurationMinutes(30);
		assertThat(ProgressService.xpOf(commute)).as("ohne TSS: aus der Dauer").isEqualTo(15);
	}

	@Test
	void streakCountsStrongWeeksBackwards() {
		List<PlannedWorkout> workouts = new ArrayList<>();
		// vor drei Wochen schwach, dann zwei starke Wochen, laufende Woche erst halb erledigt
		LocalDate monday = LocalDate.of(2026, 10, 19);
		workouts.add(planned(monday.minusWeeks(3), false));
		workouts.add(planned(monday.minusWeeks(3).plusDays(2), false));
		workouts.add(planned(monday.minusWeeks(2), true));
		workouts.add(planned(monday.minusWeeks(2).plusDays(2), true));
		workouts.add(planned(monday.minusWeeks(1), true));
		workouts.add(planned(monday.minusWeeks(1).plusDays(2), false)); // 1 von 2 → schwach? siehe unten
		workouts.add(planned(monday, true));
		workouts.add(planned(monday.plusDays(4), false));

		// Woche -1: 50 % → bricht die Serie
		assertThat(ProgressService.streakWeeks(workouts, TODAY)).isZero();

		workouts.get(5).setCompleted(true);
		assertThat(ProgressService.streakWeeks(workouts, TODAY)).as("laufende Woche zählt noch nicht").isEqualTo(2);

		workouts.get(7).setCompleted(true);
		assertThat(ProgressService.streakWeeks(workouts, TODAY)).isEqualTo(3);
	}

	@Test
	void planProgressCountsOnlyDueWorkouts() {
		Athlete athlete = new Athlete();
		Event event = new Event();
		event.setName("Ötztaler");
		event.setDate(TODAY.plusDays(81));
		athlete.setEvent(event);
		TrainingPlan plan = new TrainingPlan(athlete, LocalDate.of(2026, 10, 5));
		plan.addWorkout(planned(LocalDate.of(2026, 10, 6), true));
		plan.addWorkout(planned(LocalDate.of(2026, 10, 8), false));
		plan.addWorkout(planned(TODAY, true));
		plan.addWorkout(planned(TODAY.plusDays(1), false));

		var progress = ProgressService.planProgress(plan, athlete, TODAY);

		assertThat(progress.week()).isEqualTo(3);
		assertThat(progress.due()).isEqualTo(3);
		assertThat(progress.completed()).isEqualTo(2);
		assertThat(progress.daysToEvent()).isEqualTo(81);
		assertThat(progress.totalWeeks()).isGreaterThan(12);
	}

	@Test
	void countdownGoesToTheNextUpcomingEvent() {
		var athlete = new io.github.jakobfa.webtechproject.model.Athlete();
		var first = new io.github.jakobfa.webtechproject.model.Event();
		first.setName("Schon vorbei");
		first.setDate(TODAY.minusDays(3));
		athlete.setEvent(first);
		var next = new io.github.jakobfa.webtechproject.model.Event();
		next.setName("Herbst-Gravel");
		next.setDate(TODAY.plusDays(40));
		TrainingPlan plan = new TrainingPlan(athlete, TODAY.minusWeeks(10));

		var progress = ProgressService.planProgress(plan, athlete, java.util.List.of(next), TODAY);

		assertThat(progress.eventName()).isEqualTo("Herbst-Gravel");
		assertThat(progress.daysToEvent()).isEqualTo(40);
		assertThat(progress.week()).isLessThanOrEqualTo(progress.totalWeeks());
		assertThat(progress.totalWeeks()).isGreaterThanOrEqualTo(15);
	}

	@Test
	void loadCheckComparesTheLastSevenDaysWithThePlan() {
		// geplant: 3 × Sweet Spot 3 × 12 (je 67 TSS) = 201 TSS
		PlannedWorkout done = planned(TODAY.minusDays(5), true);
		PlannedWorkout ridden = planned(TODAY.minusDays(3), false);
		ridden.setActualTss(70);
		PlannedWorkout missed = planned(TODAY.minusDays(1), false);
		PlannedWorkout old = planned(TODAY.minusDays(9), true); // außerhalb der 7 Tage
		PlannedWorkout upcoming = planned(TODAY.plusDays(1), false); // noch nicht fällig
		var extra = new io.github.jakobfa.webtechproject.model.ExtraRide(new io.github.jakobfa.webtechproject.model.Athlete(), "x");
		extra.setDate(TODAY.minusDays(2));
		extra.setTss(40);

		var plan = planFrom(TODAY.minusWeeks(10));
		var check = service.loadCheck(plan, java.util.List.of(done, ridden, missed, old, upcoming),
				java.util.List.of(extra), TODAY);

		assertThat(check.plannedTss()).isEqualTo(201);
		assertThat(check.actualTss()).isEqualTo(67 + 70 + 40);
		assertThat(check.status()).isEqualTo(ProgressService.LoadStatus.ON_TRACK);
	}

	@Test
	void aRelaxedExtraRideInATightlyPlannedWeekIsNotTooMuch() {
		// genau dein Fall: knappe Woche (201 geplant), du fährst 232 – aber dein Schnitt ist ~220/Woche
		assertThat(ProgressService.statusOf(201, 232, 220)).isEqualTo(ProgressService.LoadStatus.ON_TRACK);
	}

	@Test
	void statusUsesTwoReferences() {
		// "zu viel" am chronischen Schnitt (ACWR), nicht am Plan
		assertThat(ProgressService.statusOf(300, 350, 250)).as("1,4× Schnitt")
				.isEqualTo(ProgressService.LoadStatus.A_BIT_MUCH);
		assertThat(ProgressService.statusOf(300, 450, 250)).as("1,8× Schnitt")
				.isEqualTo(ProgressService.LoadStatus.TOO_MUCH);
		// unter dem Plan geblieben → zu wenig (am Plan gemessen)
		assertThat(ProgressService.statusOf(300, 150, 300)).isEqualTo(ProgressService.LoadStatus.TOO_LITTLE);
		// geplante Erholungswoche wie geplant gefahren → nicht "zu wenig"
		assertThat(ProgressService.statusOf(130, 130, 300)).isEqualTo(ProgressService.LoadStatus.ON_TRACK);
		assertThat(ProgressService.statusOf(0, 0, 200)).isEqualTo(ProgressService.LoadStatus.RESTING);
	}

	@Test
	void whenNewTheChronicBaselineComesFromThePlannedWeeks() {
		LocalDate start = TODAY; // heute gestartet, noch keine Historie
		java.util.List<PlannedWorkout> ahead = new java.util.ArrayList<>();
		for (int day = 0; day < 28; day += 2) {
			ahead.add(planned(TODAY.plusDays(day), false)); // 14 × 67 TSS über 4 Wochen ≈ 234/Woche
		}

		int chronic = service.chronicWeekly(planFrom(start), ahead, java.util.List.of(), TODAY);

		assertThat(chronic).as("Basis ≈ geplantes Wochenpensum").isBetween(210, 240);
		// wer am ersten Tag gleich das Doppelte fährt, bekommt eine Warnung
		assertThat(ProgressService.statusOf(230, 470, chronic)).isEqualTo(ProgressService.LoadStatus.TOO_MUCH);
	}

}
