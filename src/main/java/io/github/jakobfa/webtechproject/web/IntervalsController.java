package io.github.jakobfa.webtechproject.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService.SyncResult;

@RestController
public class IntervalsController {

	private final IntervalsSyncService syncService;

	public IntervalsController(IntervalsSyncService syncService) {
		this.syncService = syncService;
	}

	/** Ob für dieses Profil der Sync angeboten werden soll (steuert den Button im Frontend). */
	@GetMapping(path = "/athletes/{athleteId}/intervals")
	public Map<String, Boolean> status(@PathVariable Long athleteId) {
		return Map.of("available", syncService.isAvailableFor(athleteId));
	}

	@PostMapping(path = "/athletes/{athleteId}/intervals/sync")
	public ResponseEntity<SyncResult> sync(@PathVariable Long athleteId,
			@RequestParam(defaultValue = "14") int days) {
		if (!syncService.isAvailableFor(athleteId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		if (days < 1 || days > 60) {
			return ResponseEntity.badRequest().build();
		}
		try {
			return syncService.syncUpcoming(athleteId, days)
					.map(ResponseEntity::ok)
					.orElse(ResponseEntity.notFound().build());
		} catch (RestClientException e) {
			// intervals.icu nicht erreichbar oder Schlüssel ungültig
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

}
