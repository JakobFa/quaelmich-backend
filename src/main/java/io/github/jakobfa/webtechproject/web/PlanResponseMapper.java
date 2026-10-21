package io.github.jakobfa.webtechproject.web;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.integration.RecordedRide;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;
import io.github.jakobfa.webtechproject.web.dto.ExtraRideResponse;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

/**
 * Wandelt Plan-Entities in die Antworten fürs Frontend um und ergänzt die Workout-Daten aus der Bibliothek.
 */
@Component
public class PlanResponseMapper {

	private final WorkoutLibrary workoutLibrary;
	private final PlanAdaptationService adaptation;
	private final ExtraRideRepository extraRideRepository;

	public PlanResponseMapper(WorkoutLibrary workoutLibrary, PlanAdaptationService adaptation,
			ExtraRideRepository extraRideRepository) {
		this.workoutLibrary = workoutLibrary;
		this.adaptation = adaptation;
		this.extraRideRepository = extraRideRepository;
	}

	public TrainingPlanResponse toResponse(TrainingPlan plan) {
		var event = plan.getAthlete().getEvent();
		return new TrainingPlanResponse(plan.getId(), plan.getStartDate(), event.getName(), event.getDate(),
				plan.getWorkouts().stream().map(this::toResponse).toList(),
				extraRideRepository
						.findByAthleteIdAndDateGreaterThanEqualOrderByDate(plan.getAthlete().getId(), plan.getStartDate())
						.stream().map(PlanResponseMapper::toResponse).toList());
	}

	private static ExtraRideResponse toResponse(ExtraRide ride) {
		return new ExtraRideResponse(ride.getId(), ride.getDate(), ride.getDurationMinutes(), ride.getDistanceKm(),
				ride.getTss(), ride.assessment(), RecordedRide.stravaUrl(ride.getActivityId()));
	}

	public PlannedWorkoutResponse toResponse(PlannedWorkout planned) {
		var workout = workoutLibrary.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		var actual = planned.getActivityId() == null ? null
				: new PlannedWorkoutResponse.Actual(planned.getActualDurationMinutes(), planned.getActualDistanceKm(),
						planned.getActualTss(), adaptation.assess(planned), planned.getActivityConfirmed(),
						RecordedRide.stravaUrl(planned.getActivityId()));
		var adjustment = planned.getAdjustmentNote() == null ? null
				: new PlannedWorkoutResponse.Adjustment(planned.getAdjustmentNote(), planned.getOriginalTitle());
		return new PlannedWorkoutResponse(planned.getId(), planned.getDate(), planned.getPhase(),
				planned.isCompleted(), planned.getRating(), workout, actual, adjustment);
	}

}
