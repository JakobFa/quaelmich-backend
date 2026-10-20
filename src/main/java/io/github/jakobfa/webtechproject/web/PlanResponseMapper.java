package io.github.jakobfa.webtechproject.web;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

/**
 * Wandelt Plan-Entities in die Antworten fürs Frontend um und ergänzt die Workout-Daten aus der Bibliothek.
 */
@Component
public class PlanResponseMapper {

	private final WorkoutLibrary workoutLibrary;
	private final PlanAdaptationService adaptation;

	public PlanResponseMapper(WorkoutLibrary workoutLibrary, PlanAdaptationService adaptation) {
		this.workoutLibrary = workoutLibrary;
		this.adaptation = adaptation;
	}

	public TrainingPlanResponse toResponse(TrainingPlan plan) {
		var event = plan.getAthlete().getEvent();
		return new TrainingPlanResponse(plan.getId(), plan.getStartDate(), event.getName(), event.getDate(),
				plan.getWorkouts().stream().map(this::toResponse).toList());
	}

	public PlannedWorkoutResponse toResponse(PlannedWorkout planned) {
		var workout = workoutLibrary.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		var actual = planned.getActivityId() == null ? null
				: new PlannedWorkoutResponse.Actual(planned.getActualDurationMinutes(), planned.getActualDistanceKm(),
						planned.getActualTss(), adaptation.assess(planned), planned.getActivityConfirmed());
		var adjustment = planned.getAdjustmentNote() == null ? null
				: new PlannedWorkoutResponse.Adjustment(planned.getAdjustmentNote(), planned.getOriginalTitle());
		return new PlannedWorkoutResponse(planned.getId(), planned.getDate(), planned.getPhase(),
				planned.isCompleted(), planned.getRating(), workout, actual, adjustment);
	}

}
