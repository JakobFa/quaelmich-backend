package io.github.jakobfa.webtechproject.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService.SyncAllResult;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsProperties;

/**
 * Nächtlicher Sync für alle Nutzer mit Auto-Sync. Wird von GitHub Actions per Zeitplan aufgerufen
 * (das kostenlose Render-Backend schläft sonst und könnte selbst keinen Zeitplan ausführen).
 *
 * Geschützt durch das Geheimnis INTERVALS_SYNC_TOKEN im Header "X-Sync-Token".
 */
@RestController
public class InternalSyncController {

	private final IntervalsAutoSyncService autoSync;
	private final IntervalsProperties properties;

	public InternalSyncController(IntervalsAutoSyncService autoSync, IntervalsProperties properties) {
		this.autoSync = autoSync;
		this.properties = properties;
	}

	@PostMapping(path = "/internal/intervals/sync-all")
	public ResponseEntity<SyncAllResult> syncAll(@RequestHeader(name = "X-Sync-Token", required = false) String token) {
		String expected = properties.syncToken();
		if (expected == null || expected.isBlank()) {
			return ResponseEntity.notFound().build(); // ohne konfiguriertes Geheimnis gibt es die Route nicht
		}
		if (token == null || !constantTimeEquals(token, expected)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return ResponseEntity.ok(autoSync.syncAll());
	}

	/** Vergleich in konstanter Zeit, damit das Geheimnis nicht über Antwortzeiten erraten werden kann */
	private static boolean constantTimeEquals(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}
