package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.service.PlannedWorkoutService;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.WorkoutProgressRequest;
import jakarta.validation.Valid;

@RestController
public class PlannedWorkoutController {

	private final PlannedWorkoutService plannedWorkoutService;
	private final PlanResponseMapper mapper;

	public PlannedWorkoutController(PlannedWorkoutService plannedWorkoutService, PlanResponseMapper mapper) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.mapper = mapper;
	}

	@GetMapping(path = "/planned-workouts/{id}")
	public ResponseEntity<PlannedWorkoutResponse> getPlannedWorkout(@PathVariable Long id) {
		return plannedWorkoutService.findById(id)
				.map(planned -> ResponseEntity.ok(mapper.toResponse(planned)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping(path = "/planned-workouts/{id}/progress")
	public ResponseEntity<PlannedWorkoutResponse> updateProgress(@PathVariable Long id,
			@Valid @RequestBody WorkoutProgressRequest progress) {
		return plannedWorkoutService.updateProgress(id, progress.completed(), progress.rating())
				.map(planned -> ResponseEntity.ok(mapper.toResponse(planned)))
				.orElse(ResponseEntity.notFound().build());
	}

}
