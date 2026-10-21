package io.github.jakobfa.webtechproject.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.ActivityImportService;
import io.github.jakobfa.webtechproject.integration.intervals.ActivityImportService.ImportResult;

/**
 * Gefahrene Einheiten aus allen verbundenen Quellen (intervals.icu, Strava) abholen.
 */
@RestController
public class ActivityController {

	private final ActivityImportService activityImport;

	public ActivityController(ActivityImportService activityImport) {
		this.activityImport = activityImport;
	}

	/** Fahrten der letzten 14 Tage mit dem Plan verknüpfen. 409, wenn nichts verbunden ist oder kein Profil da ist. */
	@PostMapping(path = { "/me/activities/import", "/me/intervals/import" })
	public ResponseEntity<ImportResult> importActivities(@AuthenticationPrincipal Jwt jwt) {
		try {
			return activityImport.importRecent(CurrentUser.id(jwt))
					.map(ResponseEntity::ok)
					.orElse(ResponseEntity.status(HttpStatus.CONFLICT).build());
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

}
