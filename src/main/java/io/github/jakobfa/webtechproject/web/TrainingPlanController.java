package io.github.jakobfa.webtechproject.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

@RestController
public class TrainingPlanController {

	private final TrainingPlanService planService;
	private final WorkoutLibrary workoutLibrary;

	public TrainingPlanController(TrainingPlanService planService, WorkoutLibrary workoutLibrary) {
		this.planService = planService;
		this.workoutLibrary = workoutLibrary;
	}

	@PostMapping(path = "/athletes/{athleteId}/plan")
	public ResponseEntity<TrainingPlanResponse> generatePlan(@PathVariable Long athleteId) {
		return planService.generate(athleteId)
				.map(plan -> ResponseEntity.created(URI.create("/athletes/" + athleteId + "/plan")).body(toResponse(plan)))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping(path = "/athletes/{athleteId}/plan")
	public ResponseEntity<TrainingPlanResponse> getPlan(@PathVariable Long athleteId) {
		return planService.findByAthlete(athleteId)
				.map(plan -> ResponseEntity.ok(toResponse(plan)))
				.orElse(ResponseEntity.notFound().build());
	}

	private TrainingPlanResponse toResponse(TrainingPlan plan) {
		var event = plan.getAthlete().getEvent();
		return new TrainingPlanResponse(plan.getId(), plan.getStartDate(), event.getName(), event.getDate(),
				plan.getWorkouts().stream().map(this::toResponse).toList());
	}

	private PlannedWorkoutResponse toResponse(PlannedWorkout planned) {
		var workout = workoutLibrary.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		return new PlannedWorkoutResponse(planned.getId(), planned.getDate(), planned.getPhase(),
				planned.isCompleted(), planned.getRating(), workout);
	}

}
