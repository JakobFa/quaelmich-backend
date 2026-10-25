package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsFtpSync;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import jakarta.validation.Valid;

/**
 * Das Profil des angemeldeten Nutzers. Über "/me" gibt es keine Möglichkeit, fremde Profile anzusprechen.
 */
@RestController
public class AthleteController {

	private final AthleteService athleteService;
	private final IntervalsFtpSync ftpSync;

	public AthleteController(AthleteService athleteService, IntervalsFtpSync ftpSync) {
		this.athleteService = athleteService;
		this.ftpSync = ftpSync;
	}

	@GetMapping(path = "/me/athlete")
	public ResponseEntity<Athlete> getMyAthlete(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	/** Legt das Profil an oder ändert es. Eine geänderte FTP geht auch an intervals.icu, falls verbunden. */
	@PutMapping(path = "/me/athlete")
	public ResponseEntity<Athlete> saveMyAthlete(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody Athlete athlete) {
		Long userId = CurrentUser.id(jwt);
		Integer oldFtp = athleteService.findByOwner(userId).map(Athlete::getFtp).orElse(null);
		Athlete saved = athleteService.saveForOwner(userId, athlete);
		if (saved.getFtp() != null && !saved.getFtp().equals(oldFtp)) {
			ftpSync.pushFtp(userId, saved.getFtp());
		}
		return ResponseEntity.ok(saved);
	}

	/** Hinweis "FTP wahrscheinlich zu niedrig" ausblenden */
	@DeleteMapping(path = "/me/athlete/ftp-suggestion")
	public ResponseEntity<Athlete> dismissFtpSuggestion(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.dismissFtpSuggestion(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

}
