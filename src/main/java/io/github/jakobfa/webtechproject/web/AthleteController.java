package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import jakarta.validation.Valid;

/**
 * Das Profil des angemeldeten Nutzers. Über "/me" gibt es keine Möglichkeit, fremde Profile anzusprechen.
 */
@RestController
public class AthleteController {

	private final AthleteService athleteService;

	public AthleteController(AthleteService athleteService) {
		this.athleteService = athleteService;
	}

	@GetMapping(path = "/me/athlete")
	public ResponseEntity<Athlete> getMyAthlete(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	/** Legt das Profil an oder ändert es. */
	@PutMapping(path = "/me/athlete")
	public ResponseEntity<Athlete> saveMyAthlete(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody Athlete athlete) {
		return ResponseEntity.ok(athleteService.saveForOwner(CurrentUser.id(jwt), athlete));
	}

}
