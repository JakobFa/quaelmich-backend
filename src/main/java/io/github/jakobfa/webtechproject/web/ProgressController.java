package io.github.jakobfa.webtechproject.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.service.ProgressService;
import io.github.jakobfa.webtechproject.service.ProgressService.Progress;

@RestController
public class ProgressController {

	private final ProgressService progressService;

	public ProgressController(ProgressService progressService) {
		this.progressService = progressService;
	}

	/** Level, Erfahrungspunkte, Serie und Stand im Plan. 404, solange es kein Profil gibt. */
	@GetMapping(path = "/me/progress")
	public ResponseEntity<Progress> progress(@AuthenticationPrincipal Jwt jwt) {
		return progressService.progressOf(CurrentUser.id(jwt))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

}
