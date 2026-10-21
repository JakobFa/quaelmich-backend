package io.github.jakobfa.webtechproject.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/**
 * Selbst zuordnen: "Diese Fahrt war das geplante Training". Aus der Zusatzfahrt werden die Ist-Werte
 * des Workouts – so zählt die Belastung nur einmal und verändert den Plan nicht doppelt.
 */
@Service
public class RideAssignmentService {

	private final PlannedWorkoutService plannedWorkoutService;
	private final PlannedWorkoutRepository plannedWorkoutRepository;
	private final ExtraRideRepository extraRideRepository;

	public RideAssignmentService(PlannedWorkoutService plannedWorkoutService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.extraRideRepository = extraRideRepository;
	}

	/** Radfahrten am Tag des Workouts, die noch keinem Workout zugeordnet sind. Leer, wenn fremd oder schon zugeordnet */
	@Transactional(readOnly = true)
	public Optional<List<ExtraRide>> candidates(Long plannedId, Long userId) {
		return plannedWorkoutService.findOwned(plannedId, userId).map(planned -> planned.getActivityId() != null
				? List.<ExtraRide>of()
				: ridesOnTheSameDay(planned));
	}

	/** Leer, wenn Workout oder Fahrt nicht passen (fremd, anderer Tag, keine Radfahrt) */
	@Transactional
	public Optional<PlannedWorkout> assign(Long plannedId, Long userId, Long extraRideId) {
		return plannedWorkoutService.findOwned(plannedId, userId)
				.flatMap(planned -> ridesOnTheSameDay(planned).stream()
						.filter(ride -> ride.getId().equals(extraRideId))
						.findFirst()
						.map(ride -> {
							planned.setActivityId(ride.getActivityId());
							planned.setActualDurationMinutes(ride.getDurationMinutes());
							planned.setActualDistanceKm(ride.getDistanceKm());
							planned.setActualTss(ride.getTss());
							planned.setActualTssFromHeartRate(ride.isTssFromHeartRate());
							planned.setCompleted(true);
							planned.setActivityConfirmed(true); // selbst zugeordnet = bestätigt
							// hat die Fahrt den Plan schon angepasst, nicht nochmal
							planned.setAdaptationApplied(ride.isAdaptationApplied());
							extraRideRepository.delete(ride);
							return plannedWorkoutRepository.save(planned);
						}));
	}

	private List<ExtraRide> ridesOnTheSameDay(PlannedWorkout planned) {
		Long athleteId = planned.getPlan().getAthlete().getId();
		return extraRideRepository.findByAthleteIdAndDateBetween(athleteId, planned.getDate(), planned.getDate())
				.stream()
				.filter(ride -> ride.getSport() == Sport.RIDE)
				.toList();
	}

}
