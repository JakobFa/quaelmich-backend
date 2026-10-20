package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

class WorkoutLibraryTest {

	private final WorkoutLibrary library = new WorkoutLibrary();

	@Test
	void containsOneWorkoutPerType() {
		assertThat(library.findAll())
				.extracting(Workout::getType)
				.containsExactlyInAnyOrder(WorkoutType.values());
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
