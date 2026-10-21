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
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;

/**
 * Gefahrene Einheiten aus allen verbundenen Quellen (intervals.icu, Strava) abholen.
 */
@RestController
public class ActivityController {

	private final ActivityImportService activityImport;
	private final IntervalsAutoSyncService autoSync;

	public ActivityController(ActivityImportService activityImport, IntervalsAutoSyncService autoSync) {
		this.activityImport = activityImport;
		this.autoSync = autoSync;
	}

	/** Fahrten der letzten 14 Tage mit dem Plan verknüpfen. 409, wenn nichts verbunden ist oder kein Profil da ist. */
	@PostMapping(path = { "/me/activities/import", "/me/intervals/import" })
	public ResponseEntity<ImportResult> importActivities(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		try {
			var result = activityImport.importRecent(userId);
			// Wurde der Plan wegen einer harten Fahrt angepasst, gleich an intervals.icu senden – nicht erst nachts
			result.filter(r -> r.adjusted() > 0).ifPresent(r -> autoSync.syncUserIfEnabled(userId));
			return result.map(ResponseEntity::ok).orElse(ResponseEntity.status(HttpStatus.CONFLICT).build());
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

}
