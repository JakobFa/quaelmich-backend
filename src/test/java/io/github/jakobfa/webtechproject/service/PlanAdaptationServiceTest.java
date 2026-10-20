package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

class PlanAdaptationServiceTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final PlanAdaptationService adaptation = new PlanAdaptationService(library);

	private PlannedWorkout planned(LocalDate date, long workoutId) {
		return new PlannedWorkout(date, library.findById(workoutId).orElseThrow(), null, Phase.BUILD);
	}

	/** Grundlage am Montag, gefahren mit dem Vielfachen der geplanten Last */
	private PlannedWorkout riddenEndurance(double factor) {
		PlannedWorkout ridden = planned(MONDAY, 2L);
		int plannedTss = library.findById(2L).orElseThrow().getTss();
		ridden.setActivityId("i1");
		ridden.setActualTss((int) Math.round(plannedTss * factor));
		return ridden;
	}

	@Test
	void harderRideMakesTheNextHardSessionOneStepEasier() {
		PlannedWorkout ridden = riddenEndurance(1.3);
		PlannedWorkout sweetSpot = planned(MONDAY.plusDays(1), 5L); // Sweet Spot 3 × 12

		var adjusted = adaptation.adaptAfter(ridden, List.of(ridden, sweetSpot));

		assertThat(adjusted).contains(sweetSpot);
		assertThat(sweetSpot.getWorkoutId()).isEqualTo(14L); // Sweet Spot 3 × 10
		assertThat(sweetSpot.getOriginalTitle()).isEqualTo("Sweet Spot 3 × 12");
		assertThat(sweetSpot.getAdjustmentNote()).startsWith("Angepasst: Am Mo., 19.10.");
		assertThat(ridden.isAdaptationApplied()).isTrue();
	}

	@Test
	void muchHarderRideTurnsTheNextHardSessionIntoRecovery() {
		PlannedWorkout ridden = riddenEndurance(1.6);
		PlannedWorkout vo2 = planned(MONDAY.plusDays(2), 8L);

		adaptation.adaptAfter(ridden, List.of(vo2));

		var replacement = library.resolve(vo2.getWorkoutId(), vo2.getDurationMinutes()).orElseThrow();
		assertThat(replacement.getType()).isEqualTo(WorkoutType.RECOVERY);
		assertThat(replacement.getDurationInMinutes()).isLessThanOrEqualTo(60);
	}

	@Test
	void easiestVariantBecomesRecovery() {
		PlannedWorkout ridden = riddenEndurance(1.3);
		PlannedWorkout tempo = planned(MONDAY.plusDays(1), 12L); // leichteste Tempo-Variante

		adaptation.adaptAfter(ridden, List.of(tempo));

		assertThat(tempo.getWorkoutId()).isEqualTo(1L);
	}

	@Test
	void onlyLooksTwoDaysAheadAndSkipsEasyAndDoneSessions() {
		PlannedWorkout ridden = riddenEndurance(1.6);
		PlannedWorkout easy = planned(MONDAY.plusDays(1), 2L);
		PlannedWorkout done = planned(MONDAY.plusDays(1), 5L);
		done.setCompleted(true);
		PlannedWorkout tooLate = planned(MONDAY.plusDays(3), 8L);

		assertThat(adaptation.adaptAfter(ridden, List.of(easy, done, tooLate))).isEmpty();
		assertThat(tooLate.getAdjustmentNote()).isNull();
	}

	@Test
	void picksTheEarliestHardSession() {
		PlannedWorkout ridden = riddenEndurance(1.3);
		PlannedWorkout dayTwo = planned(MONDAY.plusDays(2), 8L);
		PlannedWorkout dayOne = planned(MONDAY.plusDays(1), 5L);

		assertThat(adaptation.adaptAfter(ridden, List.of(dayTwo, dayOne))).contains(dayOne);
		assertThat(dayTwo.getAdjustmentNote()).isNull();
	}

	@Test
	void easierOrAsPlannedChangesNothing() {
		PlannedWorkout next = planned(MONDAY.plusDays(1), 5L);

		assertThat(adaptation.adaptAfter(riddenEndurance(0.5), List.of(next))).isEmpty();
		assertThat(adaptation.adaptAfter(riddenEndurance(1.1), List.of(next))).isEmpty();
		assertThat(next.getWorkoutId()).isEqualTo(5L);
	}

	@Test
	void adaptsOnlyOncePerRide() {
		PlannedWorkout ridden = riddenEndurance(1.3);
		PlannedWorkout first = planned(MONDAY.plusDays(1), 5L);
		adaptation.adaptAfter(ridden, List.of(first));

		PlannedWorkout second = planned(MONDAY.plusDays(2), 8L);
		assertThat(adaptation.adaptAfter(ridden, List.of(first, second))).isEmpty();
		assertThat(second.getAdjustmentNote()).isNull();
	}

	@Test
	void assessesTheRide() {
		assertThat(adaptation.assess(riddenEndurance(1.6))).isEqualTo(Assessment.MUCH_HARDER);
	}

}
