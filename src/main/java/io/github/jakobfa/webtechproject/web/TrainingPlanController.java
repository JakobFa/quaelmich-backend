package io.github.jakobfa.webtechproject.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

@RestController
public class TrainingPlanController {

	private final TrainingPlanService planService;
	private final AthleteService athleteService;
	private final PlanResponseMapper mapper;
	private final IntervalsAutoSyncService autoSync;

	public TrainingPlanController(TrainingPlanService planService, AthleteService athleteService,
			PlanResponseMapper mapper, IntervalsAutoSyncService autoSync) {
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

	@GetMapping(path = "/me/plan")
	public ResponseEntity<TrainingPlanResponse> getPlan(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(Athlete::getId)
				.flatMap(planService::findByAthlete)
				.map(plan -> ResponseEntity.ok(mapper.toResponse(plan)))
				.orElse(ResponseEntity.notFound().build());
	}

}
