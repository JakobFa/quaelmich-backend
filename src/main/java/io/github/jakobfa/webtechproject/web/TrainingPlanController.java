package io.github.jakobfa.webtechproject.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.web.dto.TrainingPlanResponse;

@RestController
public class TrainingPlanController {

	private final TrainingPlanService planService;
	private final AthleteService athleteService;
	private final PlanResponseMapper mapper;

	public TrainingPlanController(TrainingPlanService planService, AthleteService athleteService,
			PlanResponseMapper mapper) {
		this.planService = planService;
		this.athleteService = athleteService;
		this.mapper = mapper;
	}

	/** Erstellt den Plan des angemeldeten Nutzers (neu). 404, solange es noch kein Profil gibt. */
	@PostMapping(path = "/me/plan")
	public ResponseEntity<TrainingPlanResponse> generatePlan(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(Athlete::getId)
				.flatMap(planService::generate)
				.map(plan -> ResponseEntity.created(URI.create("/me/plan")).body(mapper.toResponse(plan)))
				.orElse(ResponseEntity.notFound().build());
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
