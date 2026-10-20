package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/**
 * Unit-Test mit gemocktem Repository (vgl. Thema 9): Der Service wird ohne Datenbank getestet.
 */
class PlannedWorkoutServiceTest {

	private final PlannedWorkoutRepository repository = mock(PlannedWorkoutRepository.class);
	private final PlannedWorkoutService service = new PlannedWorkoutService(repository);

	/** Geplantes Workout im Plan des Nutzers mit der ID 1 */
	private static PlannedWorkout ownedByUserOne() {
		AppUser owner = new AppUser("a@example.com", "hash", null);
		owner.setId(1L);
		Athlete athlete = new Athlete();
		athlete.setOwner(owner);
		PlannedWorkout planned = new PlannedWorkout();
		planned.setPlan(new TrainingPlan(athlete, null));
		return planned;
	}

	@Test
	void savesCompletionAndRating() {
		PlannedWorkout planned = ownedByUserOne();
		when(repository.findById(1L)).thenReturn(Optional.of(planned));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PlannedWorkout result = service.updateProgress(1L, 1L, true, 8).orElseThrow();

		assertThat(result.isCompleted()).isTrue();
		assertThat(result.getRating()).isEqualTo(8);
		verify(repository).save(planned);
	}

	@Test
	void dropsRatingWhenNotCompleted() {
		PlannedWorkout planned = ownedByUserOne();
		planned.setCompleted(true);
		planned.setRating(6);
		when(repository.findById(1L)).thenReturn(Optional.of(planned));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PlannedWorkout result = service.updateProgress(1L, 1L, false, 6).orElseThrow();

		assertThat(result.isCompleted()).isFalse();
		assertThat(result.getRating()).isNull();
	}

	@Test
	void doesNothingForUnknownId() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThat(service.updateProgress(99L, 1L, true, 5)).isEmpty();
		verify(repository, never()).save(any());
	}

	@Test
	void doesNotChangeWorkoutsOfOtherUsers() {
		when(repository.findById(1L)).thenReturn(Optional.of(ownedByUserOne()));

		assertThat(service.updateProgress(1L, 2L, true, 5)).isEmpty();
		verify(repository, never()).save(any());
	}

}
