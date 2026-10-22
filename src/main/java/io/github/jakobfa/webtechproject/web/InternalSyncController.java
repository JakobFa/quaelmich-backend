package io.github.jakobfa.webtechproject.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService.SyncAllResult;

/**
 * Nächtlicher Sync für alle Nutzer mit Auto-Sync. Wird von GitHub Actions per Zeitplan aufgerufen
 * (das kostenlose Render-Backend schläft sonst und könnte selbst keinen Zeitplan ausführen).
 *
 * Geschützt durch das Geheimnis INTERVALS_SYNC_TOKEN im Header "X-Sync-Token".
 */
@RestController
public class InternalSyncController {

	private final IntervalsAutoSyncService autoSync;
	private final InternalAccess access;

	public InternalSyncController(IntervalsAutoSyncService autoSync, InternalAccess access) {
		this.autoSync = autoSync;
		this.access = access;
	}

	@PostMapping(path = "/internal/intervals/sync-all")
	public ResponseEntity<SyncAllResult> syncAll(@RequestHeader(name = "X-Sync-Token", required = false) String token) {
		if (!access.isEnabled()) {
			return ResponseEntity.notFound().build(); // ohne konfiguriertes Geheimnis gibt es die Route nicht
		}
		if (!access.accepts(token)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(autoSync.syncAll());
	}

}
