package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.service.PlannedWorkoutService;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.WorkoutProgressRequest;
import jakarta.validation.Valid;

@RestController
public class PlannedWorkoutController {

	private final PlannedWorkoutService plannedWorkoutService;
	private final PlanResponseMapper mapper;
	private final FitWorkoutExporter fitExporter;

	public PlannedWorkoutController(PlannedWorkoutService plannedWorkoutService, PlanResponseMapper mapper,
			FitWorkoutExporter fitExporter) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.mapper = mapper;
		this.fitExporter = fitExporter;
	}

	@GetMapping(path = "/planned-workouts/{id}")
	public ResponseEntity<PlannedWorkoutResponse> getPlannedWorkout(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id) {
		return plannedWorkoutService.findOwned(id, CurrentUser.id(jwt))
				.map(planned -> ResponseEntity.ok(mapper.toResponse(planned)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping(path = "/planned-workouts/{id}/progress")
	public ResponseEntity<PlannedWorkoutResponse> updateProgress(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id, @Valid @RequestBody WorkoutProgressRequest progress) {
		return plannedWorkoutService.updateProgress(id, CurrentUser.id(jwt), progress.completed(), progress.rating())
				.map(planned -> ResponseEntity.ok(mapper.toResponse(planned)))
				.orElse(ResponseEntity.notFound().build());
	}

	public record ActivityConfirmation(boolean matchesPlan) {
	}

	/** "War das das geplante Training?" – 404, wenn keine Fahrt verknüpft ist oder das Workout fremd ist */
	@PutMapping(path = "/planned-workouts/{id}/activity-confirmation")
	public ResponseEntity<PlannedWorkoutResponse> confirmActivity(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id, @RequestBody ActivityConfirmation confirmation) {
		return plannedWorkoutService.confirmActivity(id, CurrentUser.id(jwt), confirmation.matchesPlan())
				.map(planned -> ResponseEntity.ok(mapper.toResponse(planned)))
				.orElse(ResponseEntity.notFound().build());
	}

	/** .fit-Datei mit der im Plan angepassten Dauer (z. B. "Grundlage 75 min") */
	@GetMapping(path = "/planned-workouts/{id}/fit")
	public ResponseEntity<byte[]> downloadFit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return plannedWorkoutService.findOwned(id, CurrentUser.id(jwt))
				.map(planned -> FitDownload.of(mapper.toResponse(planned).workout(), fitExporter))
				.orElse(ResponseEntity.notFound().build());
	}

}
