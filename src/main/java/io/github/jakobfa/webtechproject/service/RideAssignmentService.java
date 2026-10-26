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
	private final IntervalAccuracyService intervalAccuracy;

	public RideAssignmentService(PlannedWorkoutService plannedWorkoutService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository,
			IntervalAccuracyService intervalAccuracy) {
		this.intervalAccuracy = intervalAccuracy;
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
							if (ride.getActivityId().equals(planned.getRejectedActivityId())) {
								planned.setRejectedActivityId(null); // doch das Training gewesen
							}
							// hat die Fahrt den Plan schon angepasst, nicht nochmal
							planned.setAdaptationApplied(ride.isAdaptationApplied());
							extraRideRepository.delete(ride);
							// FTP-Test selbst zugeordnet: war es ein Rampentest, gibt es die neue FTP als Vorschlag
							if (planned.getWorkoutId() != null && planned.getWorkoutId() == WorkoutLibrary.FTP_TEST) {
								intervalAccuracy.rampTestOf(ride.getActivityId(), userId)
										.ifPresent(result -> FtpSuggestions.fromRampTest(planned.getPlan().getAthlete(),
												result, planned.getDate()));
							}
							return plannedWorkoutRepository.save(planned);
						}));
	}

	/**
	 * "Nein, anderes Training": Die Fahrt wird vom Workout gelöst und zur Zusatzfahrt – sie zählt weiter als
	 * Belastung. Das Workout ist wieder offen, und genau diese Fahrt wird ihm nie wieder automatisch zugeordnet.
	 * Leer, wenn das Workout fremd ist oder keine Fahrt verknüpft ist.
	 */
	@Transactional
	public Optional<PlannedWorkout> reject(Long plannedId, Long userId) {
		return plannedWorkoutService.findOwned(plannedId, userId)
				.filter(planned -> planned.getActivityId() != null)
				.map(planned -> {
					extraRideRepository.save(extraRideFrom(planned));
					planned.setRejectedActivityId(planned.getActivityId());
					planned.clearActivity();
					planned.setCompleted(false);
					planned.setRating(null);
					return plannedWorkoutRepository.save(planned);
				});
	}

	/** Macht aus der am Workout hängenden Fahrt eine Zusatzfahrt am selben Tag */
	public static ExtraRide extraRideFrom(PlannedWorkout planned) {
		ExtraRide ride = new ExtraRide(planned.getPlan().getAthlete(), planned.getActivityId());
		ride.setDate(planned.getDate());
		ride.setDurationMinutes(planned.getActualDurationMinutes());
		ride.setDistanceKm(planned.getActualDistanceKm());
		ride.setTss(planned.getActualTss());
		ride.setTssFromHeartRate(planned.isActualTssFromHeartRate());
		ride.setSport(Sport.RIDE);
		// hat die Fahrt den Plan schon angepasst, nicht nochmal
		ride.setAdaptationApplied(planned.isAdaptationApplied());
		return ride;
	}

	private List<ExtraRide> ridesOnTheSameDay(PlannedWorkout planned) {
		Long athleteId = planned.getPlan().getAthlete().getId();
		return extraRideRepository.findByAthleteIdAndDateBetween(athleteId, planned.getDate(), planned.getDate())
				.stream()
				.filter(ride -> ride.getSport() == Sport.RIDE)
				.toList();
	}

}
