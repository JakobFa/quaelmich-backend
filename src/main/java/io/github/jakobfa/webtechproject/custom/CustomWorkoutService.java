package io.github.jakobfa.webtechproject.custom;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/** Eigene Workouts anlegen, ändern, löschen – und der Bibliothek bekannt machen, damit Plan, .fit & Co. sie finden */
@Service
public class CustomWorkoutService implements WorkoutCustomizationService.CustomWorkoutOwnership {

	/** länger plant auch quaelmich nicht */
	static final int MAX_SECONDS = 6 * 3600;

	/** Wird geworfen, wenn das Workout nicht gültig ist (z. B. länger als 6 h) */
	public static class InvalidWorkoutException extends RuntimeException {
		public InvalidWorkoutException(String message) {
			super(message);
		}
	}

	private final CustomWorkoutRepository repository;
	private final AppUserRepository users;
	private final Clock clock;

	public CustomWorkoutService(CustomWorkoutRepository repository, AppUserRepository users, Clock clock,
			WorkoutLibrary library) {
		this.repository = repository;
		this.users = users;
		this.clock = clock;
		// eingeplante eigene Workouts auflösen (Besitz prüft, wer sie einplant)
		library.setCustomWorkouts(publicId -> repository.findById(publicId - CustomWorkout.ID_OFFSET)
				.map(CustomWorkout::toWorkout));
	}

	public List<Workout> listOwn(Long userId) {
		return repository.findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
				.map(CustomWorkout::toWorkout).toList();
	}

	/** Eigenes, nicht gelöschtes Workout */
	public Optional<Workout> findOwn(Long publicId, Long userId) {
		return owned(publicId, userId).map(CustomWorkout::toWorkout);
	}

	@Transactional
	public Workout create(Long userId, String title, String description, WorkoutType type, List<WorkoutStep> steps) {
		validate(type, steps);
		CustomWorkout workout = new CustomWorkout();
		workout.setOwner(users.getReferenceById(userId));
		workout.setCreatedAt(Instant.now(clock));
		apply(workout, title, description, type, steps);
		return repository.save(workout).toWorkout();
	}

	/** Änderungen gelten auch für schon eingeplante Tage mit diesem Workout */
	@Transactional
	public Optional<Workout> update(Long publicId, Long userId, String title, String description, WorkoutType type,
			List<WorkoutStep> steps) {
		validate(type, steps);
		return owned(publicId, userId).map(workout -> {
			apply(workout, title, description, type, steps);
			return workout.toWorkout();
		});
	}

	@Transactional
	public boolean delete(Long publicId, Long userId) {
		return owned(publicId, userId).map(workout -> {
			workout.setDeleted(true);
			return true;
		}).orElse(false);
	}

	@Override
	public boolean isOwnedBy(Long workoutId, Long userId) {
		return owned(workoutId, userId).isPresent();
	}

	private Optional<CustomWorkout> owned(Long publicId, Long userId) {
		if (!CustomWorkout.isCustom(publicId)) {
			return Optional.empty();
		}
		return repository.findById(publicId - CustomWorkout.ID_OFFSET)
				.filter(workout -> !workout.isDeleted() && workout.getOwner().getId().equals(userId));
	}

	private static void apply(CustomWorkout workout, String title, String description, WorkoutType type,
			List<WorkoutStep> steps) {
		workout.setTitle(title.strip());
		workout.setDescription(description == null || description.isBlank() ? null : description.strip());
		workout.setType(type);
		workout.setSteps(List.copyOf(steps));
	}

	private static void validate(WorkoutType type, List<WorkoutStep> steps) {
		if (type == WorkoutType.FTP_TEST) {
			throw new InvalidWorkoutException("Einen FTP-Test gibt es schon in der Bibliothek.");
		}
		if (steps.stream().mapToInt(WorkoutStep::durationSeconds).sum() > MAX_SECONDS) {
			throw new InvalidWorkoutException("Höchstens 6 Stunden.");
		}
	}

}
