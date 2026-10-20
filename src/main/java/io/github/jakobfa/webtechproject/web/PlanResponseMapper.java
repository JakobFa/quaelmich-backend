package io.github.jakobfa.webtechproject.web;

import org.springframework.stereotype.Component;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

/**
 * Wandelt Plan-Entities in die Antworten fürs Frontend um und ergänzt die Workout-Daten aus der Bibliothek.
 */
@Component
public class PlanResponseMapper {

	private final WorkoutLibrary workoutLibrary;

	public PlanResponseMapper(WorkoutLibrary workoutLibrary) {
		this.workoutLibrary = workoutLibrary;
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
						planned.getActualTss());
		return new PlannedWorkoutResponse(planned.getId(), planned.getDate(), planned.getPhase(),
				planned.isCompleted(), planned.getRating(), workout, actual);
	}

}
