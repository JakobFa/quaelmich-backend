package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/** Neue Intervallformen: in der Bibliothek, passend zum Ziel eingesetzt, mit frei wählbarer Länge */
class NewWorkoutsTest {

	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();

	private static Set<Long> idsIn(String rider) {
		return TrainingGuardrailsTest.riders().filter(candidate -> candidate.name().startsWith(rider)).findFirst()
				.orElseThrow().plan().stream().map(PlannedWorkout::getWorkoutId).collect(Collectors.toSet());
	}

	@Test
	void theLibraryHasTheNewIntervalForms() {
		assertThat(LIBRARY.findById(71L).orElseThrow().getType()).isEqualTo(WorkoutType.THRESHOLD);
		assertThat(LIBRARY.findById(72L).orElseThrow().getTitle()).isEqualTo("Seiler 4 × 8");
		assertThat(LIBRARY.findById(73L).orElseThrow().getType()).isEqualTo(WorkoutType.VO2MAX);
		assertThat(LIBRARY.findById(74L).orElseThrow().getType()).isEqualTo(WorkoutType.ANAEROBIC);
		assertThat(LIBRARY.findById(75L).orElseThrow().getType()).isEqualTo(WorkoutType.SWEET_SPOT);
		assertThat(LIBRARY.findById(76L).orElseThrow().getType()).isEqualTo(WorkoutType.THRESHOLD);
		assertThat(LIBRARY.findById(77L).orElseThrow().getType()).isEqualTo(WorkoutType.VO2MAX);
		for (long id : new long[] { 71, 72, 73, 74, 75, 76, 77 }) {
			Workout workout = LIBRARY.findById(id).orElseThrow();
			assertThat(workout.getSteps()).as(workout.getTitle()).isNotEmpty();
			assertThat(workout.getDurationInMinutes()).as(workout.getTitle()).isBetween(30, 110);
			assertThat(workout.getDescription()).isNotBlank();
		}
	}

	@Test
	void specialtyWorkoutsOnlyFitTheirGoals() {
		var tabata = LIBRARY.findById(74L).orElseThrow();
		var strength = LIBRARY.findById(75L).orElseThrow();
		var norwegian = LIBRARY.findById(71L).orElseThrow();
		var ladder = LIBRARY.findById(73L).orElseThrow(); // passt zu jedem Ziel

		assertThat(WorkoutLibrary.suitableFor(tabata, EventProfile.Focus.SHORT_INTENSE)).isTrue();
		assertThat(WorkoutLibrary.suitableFor(tabata, EventProfile.Focus.ULTRA)).isFalse();
		assertThat(WorkoutLibrary.suitableFor(strength, EventProfile.Focus.ULTRA)).isTrue();
		assertThat(WorkoutLibrary.suitableFor(strength, EventProfile.Focus.SHORT_INTENSE)).isFalse();
		assertThat(WorkoutLibrary.suitableFor(norwegian, EventProfile.Focus.SHORT_INTENSE)).isFalse();
		assertThat(WorkoutLibrary.suitableFor(ladder, EventProfile.Focus.SHORT_INTENSE)).isTrue();
		assertThat(WorkoutLibrary.suitableFor(tabata, null)).isTrue();
		// auch in der Auswahl: für Ultra nie ein Tabata
		for (int step = 0; step < 4; step++) {
			assertThat(LIBRARY.alternatives(WorkoutType.ANAEROBIC, step, EventProfile.Focus.ULTRA))
					.noneMatch(workout -> workout.getId() == 74L);
		}
	}

	@Test
	void theNewEnduranceWorkoutsFitAnyLengthAndAreResolvedFromTheirIds() {
		for (int minutes : new int[] { 60, 90, 150, 240 }) {
			assertThat(WorkoutLibrary.enduranceWithFinish(minutes).getDurationInMinutes()).isEqualTo(minutes);
			assertThat(LIBRARY.resolve(31L, minutes).orElseThrow().getDurationInMinutes()).isEqualTo(minutes);
		}
		for (int minutes : new int[] { 210, 270, 360 }) {
			assertThat(WorkoutLibrary.longRideProgressive(minutes).getDurationInMinutes()).isEqualTo(minutes);
			assertThat(LIBRARY.resolve(32L, minutes).orElseThrow().getDurationInMinutes()).isEqualTo(minutes);
		}
		assertThat(WorkoutCustomizationService.isVariable(31L)).isTrue();
		assertThat(WorkoutCustomizationService.isVariable(32L)).isTrue();
	}

	@Test
	void ultraPlansUseEnduranceStrengthAndProgressiveLongRides() {
		for (String rider : new String[] { "Leistung, 19 h, Ultra", "Hobby, 14 h, Ultra", "Ambitioniert, 8 h, Ultra" }) {
			Set<Long> ids = idsIn(rider);
			if (!rider.startsWith("Ambitioniert")) {
				assertThat(ids).as(rider).contains(32L); // progressive lange Ausfahrt (braucht lange Wochenenden)
			}
			assertThat(ids).as(rider).containsAnyOf(71L, 75L); // Norwegian Schwelle oder Kraftausdauer
			assertThat(ids).as(rider).doesNotContain(74L, 77L); // keine kurzen, harten Spezialeinheiten
		}
	}

	@Test
	void shortIntensePlansUseTabataOrMicroBursts() {
		Set<Long> ids = idsIn("Leistung, 12 h, Kriterium");
		assertThat(ids).containsAnyOf(74L, 77L);
		assertThat(ids).doesNotContain(71L, 75L, 32L);
	}

	@Test
	void climbingPlansUseHillRepeatsOrStrengthEndurance() {
		assertThat(idsIn("Ambitioniert, 10 h, Ötztaler")).containsAnyOf(75L, 76L, 71L);
	}

	@Test
	void beginnersNeverGetTheHardSpecialties() {
		assertThat(idsIn("Einsteigerin")).doesNotContain(74L, 77L, 71L, 76L);
	}

	@Test
	void everyPlanKeepsUsingTheNewEnduranceWithFinishInSpiceWeeks() {
		Set<Long> all = TrainingGuardrailsTest.riders().flatMap(rider -> rider.plan().stream())
				.map(PlannedWorkout::getWorkoutId).collect(Collectors.toSet());
		assertThat(all).contains(31L);
	}
}
