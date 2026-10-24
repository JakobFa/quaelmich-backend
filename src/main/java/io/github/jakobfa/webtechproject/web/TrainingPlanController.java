package io.github.jakobfa.webtechproject.web;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.service.AbsenceService;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class TrainingPlanController {

	private final TrainingPlanService planService;
	private final AthleteService athleteService;
	private final PlanResponseMapper mapper;
	private final IntervalsAutoSyncService autoSync;

	private final AbsenceService absence;

	public TrainingPlanController(TrainingPlanService planService, AthleteService athleteService,
			PlanResponseMapper mapper, IntervalsAutoSyncService autoSync, AbsenceService absence) {
		this.absence = absence;
		this.planService = planService;
		this.athleteService = athleteService;
		this.mapper = mapper;
		this.autoSync = autoSync;
	}

	/** Erstellt den Plan des angemeldeten Nutzers (neu). 404, solange es noch kein Profil gibt. */
	@PostMapping(path = "/me/plan")
	public ResponseEntity<TrainingPlanResponse> generatePlan(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		var plan = athleteService.findByOwner(userId).map(Athlete::getId).flatMap(planService::generate);
		if (plan.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		// Neuen Plan gleich an intervals.icu senden, falls der Nutzer das eingeschaltet hat
		autoSync.syncUserIfEnabled(userId);
		return ResponseEntity.created(URI.create("/me/plan")).body(mapper.toResponse(plan.get()));
	}

	public record PauseRequest(@NotNull LocalDate from, @NotNull LocalDate until) {
	}

	/** Plan pausieren (z. B. Urlaub). 409 bei Zeitraum in der Vergangenheit oder länger als 8 Wochen. */
	@PutMapping(path = "/me/plan/pause")
	public ResponseEntity<TrainingPlanResponse> pause(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PauseRequest request) {
		Long userId = CurrentUser.id(jwt);
		try {
			return absence.pause(userId, request.from(), request.until())
					.map(plan -> syncedResponse(userId, plan))
					.orElse(ResponseEntity.notFound().build());
		} catch (AbsenceService.NotPossibleException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	/**
	 * Andere Zeit nur für eine Woche (Montag im Pfad): verteilt die Woche ab heute neu.
	 * 400 bei keinem Montag oder vergangener Woche.
	 */
	@PutMapping(path = "/me/plan/weeks/{monday}/availability")
	public ResponseEntity<TrainingPlanResponse> setWeekAvailability(@AuthenticationPrincipal Jwt jwt,
			@PathVariable LocalDate monday, @Valid @RequestBody WeeklyAvailability availability) {
		return changeWeek(CurrentUser.id(jwt), monday, availability);
	}

	/** Zurück zur normalen Verfügbarkeit für diese Woche */
	@DeleteMapping(path = "/me/plan/weeks/{monday}/availability")
	public ResponseEntity<TrainingPlanResponse> clearWeekAvailability(@AuthenticationPrincipal Jwt jwt,
			@PathVariable LocalDate monday) {
		return changeWeek(CurrentUser.id(jwt), monday, null);
	}

	private ResponseEntity<TrainingPlanResponse> changeWeek(Long userId, LocalDate monday,
			WeeklyAvailability availability) {
		try {
			return athleteService.findByOwner(userId)
					.flatMap(athlete -> planService.setWeekAvailability(athlete.getId(), monday, availability))
					.map(plan -> syncedResponse(userId, plan))
					.orElse(ResponseEntity.notFound().build());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}
	}

	/** Pause beenden – ab heute gilt der Plan wieder */
	@DeleteMapping(path = "/me/plan/pause")
	public ResponseEntity<TrainingPlanResponse> resume(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		return absence.resume(userId)
				.map(plan -> syncedResponse(userId, plan))
				.orElse(ResponseEntity.notFound().build());
	}

	private ResponseEntity<TrainingPlanResponse> syncedResponse(Long userId, TrainingPlan plan) {
		autoSync.syncUserIfEnabled(userId);
		return ResponseEntity.ok(mapper.toResponse(plan));
	}

	@GetMapping(path = "/me/plan")
	public ResponseEntity<TrainingPlanResponse> getPlan(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(Athlete::getId)
				.flatMap(planService::findByAthlete)
				.map(plan -> ResponseEntity.ok(mapper.toResponse(plan)))
				.orElse(ResponseEntity.notFound().build());
	}

}
