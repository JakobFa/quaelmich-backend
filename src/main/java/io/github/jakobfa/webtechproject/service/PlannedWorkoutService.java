package io.github.jakobfa.webtechproject.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

@Service
public class PlannedWorkoutService {

	private final PlannedWorkoutRepository repository;

	public PlannedWorkoutService(PlannedWorkoutRepository repository) {
		this.repository = repository;
	}

	/** Nur Workouts aus dem eigenen Plan – fremde gelten als "nicht gefunden". */
	public Optional<PlannedWorkout> findOwned(Long id, Long userId) {
		return repository.findById(id).filter(planned -> isOwnedBy(planned, userId));
	}

	/** Hakt ein Workout ab (oder nimmt das Häkchen zurück) und speichert die Bewertung. */
	@Transactional
	public Optional<PlannedWorkout> updateProgress(Long id, Long userId, boolean completed, Integer rating) {
		return findOwned(id, userId).map(planned -> {
			planned.setCompleted(completed);
			// Ohne Häkchen gibt es auch keine Bewertung
			planned.setRating(completed ? rating : null);
			return repository.save(planned);
		});
	}

	private static boolean isOwnedBy(PlannedWorkout planned, Long userId) {
		var owner = planned.getPlan().getAthlete().getOwner();
		return owner != null && owner.getId().equals(userId);
	}

}
