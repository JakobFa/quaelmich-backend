package io.github.jakobfa.webtechproject.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService.SyncResult;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;

@RestController
public class IntervalsController {

	private final IntervalsSyncService syncService;
	private final AthleteService athleteService;

	public IntervalsController(IntervalsSyncService syncService, AthleteService athleteService) {
		this.syncService = syncService;
		this.athleteService = athleteService;
	}

	/** Ob für den angemeldeten Nutzer der Sync angeboten werden soll (steuert den Button im Frontend). */
	@GetMapping(path = "/me/intervals")
	public Map<String, Boolean> status(@AuthenticationPrincipal Jwt jwt) {
		boolean available = athleteService.findByOwner(CurrentUser.id(jwt))
				.map(Athlete::getId)
				.map(syncService::isAvailableFor)
				.orElse(false);
		return Map.of("available", available);
	}

	@PostMapping(path = "/me/intervals/sync")
	public ResponseEntity<SyncResult> sync(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "14") int days) {
		var athleteId = athleteService.findByOwner(CurrentUser.id(jwt)).map(Athlete::getId);
		if (athleteId.isEmpty() || !syncService.isAvailableFor(athleteId.get())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (days < 1 || days > 60) {
			return ResponseEntity.badRequest().build();
		}
		try {
			return syncService.syncUpcoming(athleteId.get(), days)
					.map(ResponseEntity::ok)
					.orElse(ResponseEntity.notFound().build());
		} catch (RestClientException e) {
			// intervals.icu nicht erreichbar oder Schlüssel ungültig
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

}
