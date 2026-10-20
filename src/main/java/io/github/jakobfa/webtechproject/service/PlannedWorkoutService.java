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

	/**
	 * Bestätigt oder verwirft die automatisch erkannte Fahrt.
	 * "Nein" heißt: anderes Training – das geplante Workout gilt dann als nicht absolviert.
	 * Die Fahrt bleibt gespeichert, denn die Belastung war trotzdem da.
	 */
	@Transactional
	public Optional<PlannedWorkout> confirmActivity(Long id, Long userId, boolean matchesPlan) {
		return findOwned(id, userId).filter(planned -> planned.getActivityId() != null).map(planned -> {
			planned.setActivityConfirmed(matchesPlan);
			planned.setCompleted(matchesPlan);
			if (!matchesPlan) {
				planned.setRating(null);
			}
			return repository.save(planned);
		});
	}

	private static boolean isOwnedBy(PlannedWorkout planned, Long userId) {
		var owner = planned.getPlan().getAthlete().getOwner();
		return owner != null && owner.getId().equals(userId);
	}

}
