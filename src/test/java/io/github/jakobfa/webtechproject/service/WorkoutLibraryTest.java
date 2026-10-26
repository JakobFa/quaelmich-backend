package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

class WorkoutLibraryTest {

	private final WorkoutLibrary library = new WorkoutLibrary();

	@Test
	void containsEveryWorkoutType() {
		assertThat(library.findAll())
				.extracting(Workout::getType)
				.contains(WorkoutType.values());
	}

	@Test
	void variantsGetHarderStepByStep() {
		for (WorkoutType type : WorkoutType.values()) {
			var variants = library.variantsOf(type);
			for (int i = 1; i < variants.size(); i++) {
				assertThat(variants.get(i).getTss())
						.as("%s: %s sollte härter sein als %s", type, variants.get(i).getTitle(),
								variants.get(i - 1).getTitle())
						.isGreaterThan(variants.get(i - 1).getTss());
			}
		}
	}

	@Test
	void tooHighStepGivesHardestVariant() {
		assertThat(library.variant(WorkoutType.SWEET_SPOT, 0).getTitle()).isEqualTo("Sweet Spot 3 × 10");
		assertThat(library.variant(WorkoutType.SWEET_SPOT, 99).getTitle()).isEqualTo("Sweet Spot 3 × 15");
	}

	@Test
	void varietyIsSelectableButNotPartOfThePlanProgression() {
		Workout fourByEight = library.findById(54L).orElseThrow(); // Sweet Spot 4 × 8

		assertThat(library.findAll()).contains(fourByEight);
		assertThat(library.variantsOf(WorkoutType.SWEET_SPOT)).doesNotContain(fourByEight)
				.extracting(Workout::getId).containsExactly(14L, 5L, 16L, 15L);
		assertThat(library.findAll().stream().filter(w -> w.getType() == WorkoutType.VO2MAX).count())
				.as("mehr Auswahl als in der Steigerung").isGreaterThan(library.variantsOf(WorkoutType.VO2MAX).size());
	}

	@Test
	void varietyWorkoutsHaveNeighboursForEasierAndHarder() {
		Workout fourByEight = library.findById(54L).orElseThrow();

		assertThat(library.easierVariant(fourByEight)).get()
				.satisfies(easier -> assertThat(easier.getTss()).isLessThan(fourByEight.getTss()));
		assertThat(library.harderVariant(fourByEight)).get()
				.satisfies(harder -> assertThat(harder.getTss()).isGreaterThan(fourByEight.getTss()));
	}

	@Test
	void idsAreUnique() {
		assertThat(library.findAll()).extracting(Workout::getId).doesNotHaveDuplicates();
	}

	@Test
	void everyWorkoutHasStepsAndPlausibleStress() {
		assertThat(library.findAll()).allSatisfy(workout -> {
			assertThat(workout.getSteps()).isNotEmpty();
			assertThat(workout.getTss()).isBetween(10, 250);
		});
	}

	@Test
	void sweetSpotIsBuiltFromWarmupThreeBlocksAndCooldown() {
		Workout sweetSpot = library.findById(5L).orElseThrow();

		// 10 min Aufwärmen + 3 × (12 + 5) min + 10 min Ausfahren
		assertThat(sweetSpot.getDurationInMinutes()).isEqualTo(71);
		assertThat(sweetSpot.getSteps()).hasSize(1 + 3 * 2 + 1);
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(library.findById(999L)).isEmpty();
	}

}
