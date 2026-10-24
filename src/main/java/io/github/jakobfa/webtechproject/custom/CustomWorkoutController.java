package io.github.jakobfa.webtechproject.custom;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.web.FitDownload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Eigene Workouts – nur für den, der sie gebaut hat */
@RestController
public class CustomWorkoutController {

	private final CustomWorkoutService service;
	private final FitWorkoutExporter fitExporter;

	public CustomWorkoutController(CustomWorkoutService service, FitWorkoutExporter fitExporter) {
		this.service = service;
		this.fitExporter = fitExporter;
	}

	public record StepRequest(@NotNull StepKind kind, @Min(10) @Max(4 * 3600) int durationSeconds,
			@Min(30) @Max(250) int powerStartPercent, @Min(30) @Max(250) int powerEndPercent) {

		WorkoutStep toStep() {
			return new WorkoutStep(kind, durationSeconds, powerStartPercent, powerEndPercent);
		}
	}

	public record WorkoutRequest(@NotBlank @Size(max = 80) String title, @Size(max = 500) String description,
			@NotNull WorkoutType type, @NotEmpty @Size(max = 100) List<@Valid StepRequest> steps) {

		List<WorkoutStep> toSteps() {
			return steps.stream().map(StepRequest::toStep).toList();
		}
	}

	@GetMapping(path = "/me/workouts")
	public List<Workout> list(@AuthenticationPrincipal Jwt jwt) {
		return service.listOwn(CurrentUser.id(jwt));
	}

	@GetMapping(path = "/me/workouts/{id}")
	public ResponseEntity<Workout> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.findOwn(id, CurrentUser.id(jwt)).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping(path = "/me/workouts")
	public ResponseEntity<Workout> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkoutRequest request) {
		Workout workout = service.create(CurrentUser.id(jwt), request.title(), request.description(), request.type(),
				request.toSteps());
		return ResponseEntity.created(URI.create("/me/workouts/" + workout.getId())).body(workout);
	}

	@PutMapping(path = "/me/workouts/{id}")
	public ResponseEntity<Workout> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody WorkoutRequest request) {
		return service.update(id, CurrentUser.id(jwt), request.title(), request.description(), request.type(),
				request.toSteps()).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping(path = "/me/workouts/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.delete(id, CurrentUser.id(jwt)) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	@GetMapping(path = "/me/workouts/{id}/fit")
	public ResponseEntity<byte[]> downloadFit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.findOwn(id, CurrentUser.id(jwt)).map(workout -> FitDownload.of(workout, fitExporter))
				.orElse(ResponseEntity.notFound().build());
	}

	@ExceptionHandler(CustomWorkoutService.InvalidWorkoutException.class)
	public ResponseEntity<String> invalid(CustomWorkoutService.InvalidWorkoutException e) {
		return ResponseEntity.badRequest().body(e.getMessage());
	}

}
