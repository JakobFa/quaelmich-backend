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

	public Optional<PlannedWorkout> findById(Long id) {
		return repository.findById(id);
	}

	/** Hakt ein Workout ab (oder nimmt das Häkchen zurück) und speichert die Bewertung. */
	@Transactional
	public Optional<PlannedWorkout> updateProgress(Long id, boolean completed, Integer rating) {
		return repository.findById(id).map(planned -> {
			planned.setCompleted(completed);
			// Ohne Häkchen gibt es auch keine Bewertung
			planned.setRating(completed ? rating : null);
			return repository.save(planned);
		});
	}

}
