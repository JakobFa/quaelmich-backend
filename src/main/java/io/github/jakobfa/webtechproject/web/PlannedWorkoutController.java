package io.github.jakobfa.webtechproject.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.service.AbsenceService;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.service.IntervalAccuracy;
import io.github.jakobfa.webtechproject.service.IntervalAccuracyService;
import io.github.jakobfa.webtechproject.service.RideAssignmentService;
import io.github.jakobfa.webtechproject.service.StravaDescriptionService;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService;
import io.github.jakobfa.webtechproject.web.dto.ExtraRideResponse;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService.Direction;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService.NotPossibleException;
import io.github.jakobfa.webtechproject.service.PlannedWorkoutService;
import io.github.jakobfa.webtechproject.web.dto.PlannedWorkoutResponse;
import io.github.jakobfa.webtechproject.web.dto.WorkoutProgressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class PlannedWorkoutController {

	private final PlannedWorkoutService plannedWorkoutService;
	private final PlanResponseMapper mapper;
	private final FitWorkoutExporter fitExporter;
	private final WorkoutCustomizationService customization;
	private final IntervalsAutoSyncService autoSync;
	private final RideAssignmentService rideAssignment;
	private final AbsenceService absence;
	private final IntervalAccuracyService intervalAccuracy;
	private final StravaDescriptionService stravaDescription;

	public PlannedWorkoutController(PlannedWorkoutService plannedWorkoutService, PlanResponseMapper mapper,
			FitWorkoutExporter fitExporter, WorkoutCustomizationService customization,
			IntervalsAutoSyncService autoSync, RideAssignmentService rideAssignment, AbsenceService absence,
			IntervalAccuracyService intervalAccuracy, StravaDescriptionService stravaDescription) {
		this.stravaDescription = stravaDescription;
		this.absence = absence;
		this.intervalAccuracy = intervalAccuracy;
		this.plannedWorkoutService = plannedWorkoutService;
		this.mapper = mapper;
		this.fitExporter = fitExporter;
		this.customization = customization;
		this.autoSync = autoSync;
		this.rideAssignment = rideAssignment;
	}

	public record RideAssignment(@NotNull Long extraRideId) {
	}

	/** Fahrten des Tages, die man diesem Workout selbst zuordnen kann (leer, wenn schon eine zugeordnet ist) */
	@GetMapping(path = "/planned-workouts/{id}/candidate-rides")
	public ResponseEntity<List<ExtraRideResponse>> candidateRides(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id) {
		return rideAssignment.candidates(id, CurrentUser.id(jwt))
				.map(rides -> ResponseEntity.ok(rides.stream().map(PlanResponseMapper::toResponse).toList()))
				.orElse(ResponseEntity.notFound().build());
	}

	/** "Diese Fahrt war das Training": hakt das Workout ab und übernimmt die Werte der Fahrt */
	@PutMapping(path = "/planned-workouts/{id}/activity")
	public ResponseEntity<PlannedWorkoutResponse> assignRide(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody RideAssignment request) {
		Long userId = CurrentUser.id(jwt);
		return rideAssignment.assign(id, userId, request.extraRideId())
				.map(planned -> describedResponse(userId, planned))
				.orElse(ResponseEntity.notFound().build());
	}

	public record AdjustmentRequest(@NotNull Direction direction) {
	}

	/**
	 * Workout anpassen: leichter, härter, kürzer, länger.
	 * 409, wenn das gerade nicht geht (z. B. schon erledigt oder leichter als Ausrollen).
	 */
	@PutMapping(path = "/planned-workouts/{id}/adjustment")
	public ResponseEntity<PlannedWorkoutResponse> adjust(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody AdjustmentRequest request) {
		Long userId = CurrentUser.id(jwt);
		try {
			return customization.adjust(id, userId, request.direction())
					.map(planned -> syncedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		} catch (NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	public record ReplacementRequest(@NotNull Long workoutId, Integer durationMinutes) {
	}

	/**
	 * Heute lieber etwas anderes: ein Workout aus der Bibliothek statt des geplanten.
	 * 409, wenn das nicht geht (schon erledigt, unbekanntes Workout oder unpassende Dauer).
	 */
	@PutMapping(path = "/planned-workouts/{id}/workout")
	public ResponseEntity<PlannedWorkoutResponse> replace(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ReplacementRequest request) {
		Long userId = CurrentUser.id(jwt);
		try {
			return customization.replace(id, userId, request.workoutId(), request.durationMinutes())
					.map(planned -> syncedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		} catch (NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	/**
	 * "Überrasch mich": ein zufälliges anderes Workout, das in die verfügbare Zeit des Tages passt.
	 * 409, wenn das nicht geht (schon erledigt, Krafttraining oder nichts Passendes).
	 */
	@PutMapping(path = "/planned-workouts/{id}/random")
	public ResponseEntity<PlannedWorkoutResponse> shuffle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		try {
			return customization.shuffle(id, userId)
					.map(planned -> syncedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		} catch (NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	public record SkipRequest(@NotNull SkipReason reason) {
	}

	/**
	 * "Nicht gemacht" mit Grund (keine Zeit, krank, …). 409, wenn schon eine Fahrt zugeordnet ist.
	 * Bei Krankheit oder Verletzung wird das nächste harte Training sanfter.
	 */
	@PutMapping(path = "/planned-workouts/{id}/skip")
	public ResponseEntity<PlannedWorkoutResponse> skip(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody SkipRequest request) {
		Long userId = CurrentUser.id(jwt);
		try {
			return absence.skip(id, userId, request.reason())
					.map(planned -> syncedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		} catch (AbsenceService.NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@DeleteMapping(path = "/planned-workouts/{id}/skip")
	public ResponseEntity<PlannedWorkoutResponse> undoSkip(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		return absence.undoSkip(id, userId)
				.map(planned -> syncedResponse(userId, planned))
				.orElse(ResponseEntity.notFound().build());
	}

	/** Zurück zum ursprünglich geplanten Workout */
	@DeleteMapping(path = "/planned-workouts/{id}/adjustment")
	public ResponseEntity<PlannedWorkoutResponse> resetAdjustment(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		try {
			return customization.reset(id, userId)
					.map(planned -> syncedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		} catch (NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	/** Geändertes Workout gleich an intervals.icu senden, falls Auto-Sync an ist */
	private ResponseEntity<PlannedWorkoutResponse> syncedResponse(Long userId, PlannedWorkout planned) {
		autoSync.syncUserIfEnabled(userId);
		return ResponseEntity.ok(mapper.toResponse(planned));
	}

	/**
	 * Wie genau wurden die Intervalle getroffen? Richtwert aus den Sekundenwerten der zugeordneten Fahrt.
	 * 204, wenn das nicht auswertbar ist (keine Fahrt, keine Intervalle, keine Watt-/Pulsdaten).
	 */
	@GetMapping(path = "/planned-workouts/{id}/accuracy")
	public ResponseEntity<IntervalAccuracy.Result> accuracy(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		if (plannedWorkoutService.findOwned(id, userId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return intervalAccuracy.accuracy(id, userId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.noContent().build());
	}

	/**
	 * Ergebnis des Rampentests (beste Minute, FTP = 75 % davon) aus der zugeordneten Fahrt.
	 * 204, wenn es kein FTP-Test ist, keine Fahrt mit Watt zugeordnet ist oder die Fahrt kein Rampentest war.
	 */
	@GetMapping(path = "/planned-workouts/{id}/ramp-test")
	public ResponseEntity<io.github.jakobfa.webtechproject.service.RampTest.Result> rampTest(
			@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		if (plannedWorkoutService.findOwned(id, userId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return intervalAccuracy.rampTest(id, userId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.noContent().build());
	}

	/**
	 * Einschätzung einer lockeren Fahrt (ohne Intervalle): wie gut wurde der Zielbereich gehalten?
	 * 204, wenn es Intervalle gibt, keine Fahrt zugeordnet ist oder keine Watt-/Pulsdaten da sind.
	 */
	@GetMapping(path = "/planned-workouts/{id}/hold")
	public ResponseEntity<IntervalAccuracy.HoldResult> hold(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		if (plannedWorkoutService.findOwned(id, userId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return intervalAccuracy.hold(id, userId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.noContent().build());
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
		Long userId = CurrentUser.id(jwt);
		return plannedWorkoutService.updateProgress(id, userId, progress.completed(), progress.rating())
				.map(planned -> describedResponse(userId, planned))
				.orElse(ResponseEntity.notFound().build());
	}

	public record ActivityConfirmation(boolean matchesPlan) {
	}

	/**
	 * "War das das geplante Training?" Ja = bestätigt; Nein = die Fahrt wird zur Zusatzfahrt und das Workout
	 * ist wieder offen. 404, wenn keine Fahrt verknüpft ist oder das Workout fremd ist.
	 */
	@PutMapping(path = "/planned-workouts/{id}/activity-confirmation")
	public ResponseEntity<PlannedWorkoutResponse> confirmActivity(@AuthenticationPrincipal Jwt jwt,
			@PathVariable Long id, @RequestBody ActivityConfirmation confirmation) {
		Long userId = CurrentUser.id(jwt);
		if (confirmation.matchesPlan()) {
			return plannedWorkoutService.confirmActivity(id, userId, true)
					.map(planned -> describedResponse(userId, planned))
					.orElse(ResponseEntity.notFound().build());
		}
		String activityId = plannedWorkoutService.findOwned(id, userId).map(PlannedWorkout::getActivityId)
				.orElse(null);
		return rideAssignment.reject(id, userId)
				.map(planned -> {
					stravaDescription.remove(userId, activityId);
					return ResponseEntity.ok(mapper.toResponse(planned));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	/** Training in die Strava-Beschreibung schreiben (im Hintergrund, falls eingeschaltet) */
	private ResponseEntity<PlannedWorkoutResponse> describedResponse(Long userId, PlannedWorkout planned) {
		stravaDescription.refresh(userId, planned.getId());
		return ResponseEntity.ok(mapper.toResponse(planned));
	}

	/** Im Puls-Modus Pulsbereiche, sonst null (= nach Watt) */
	static HeartRateTargets heartRateTargetsOf(Athlete athlete) {
		return athlete.isHeartRateMode() ? athlete.getHeartRateTargets() : null;
	}

	/** .fit-Datei mit der im Plan angepassten Dauer (z. B. "Grundlage 75 min") */
	@GetMapping(path = "/planned-workouts/{id}/fit")
	public ResponseEntity<byte[]> downloadFit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return plannedWorkoutService.findOwned(id, CurrentUser.id(jwt))
				.map(planned -> FitDownload.of(mapper.toResponse(planned).workout(), fitExporter,
						heartRateTargetsOf(planned.getPlan().getAthlete())))
				.orElse(ResponseEntity.notFound().build());
	}

}
