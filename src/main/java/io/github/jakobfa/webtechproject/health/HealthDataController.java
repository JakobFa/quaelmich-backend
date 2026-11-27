package io.github.jakobfa.webtechproject.health;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.auth.RateLimiter;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;

/**
 * Schlaf und Erholung: im Profil die Übernahme von intervals.icu einschalten (mit Einwilligung) und die letzten Tage
 * ansehen. Dazu der Upload per iPhone-Kurzbefehl (POST /health-data mit X-Health-Key) – derzeit abgeschaltet
 * (app.health.shortcut-enabled), dann antworten Schlüssel und Upload mit 404.
 */
@RestController
public class HealthDataController {

	/** Uploads je Schlüssel und Stunde – ein Kurzbefehl schickt einmal am Tag, Platz für Tests von Hand */
	static final RateLimiter.Limit UPLOAD_LIMIT = new RateLimiter.Limit(30, Duration.ofHours(1));

	public record ConsentRequest(Boolean consent) {
	}

	public record ConnectResponse(String key) {
	}

	/** intervalsConnected: ohne intervals.icu-Verbindung lässt sich nichts übernehmen */
	public record Status(boolean intervalsConnected, boolean importEnabled, Instant consentAt, Instant lastImportAt,
			boolean sharedWithAi, List<HealthService.Day> days) {
	}

	public record ShareRequest(Boolean share) {
	}

	private final HealthService health;
	private final WellnessImportService wellness;
	private final IntervalsConnectionService intervals;
	private final RateLimiter rateLimiter;
	private final boolean shortcutEnabled;

	public HealthDataController(HealthService health, WellnessImportService wellness,
			IntervalsConnectionService intervals, RateLimiter rateLimiter,
			@Value("${app.health.shortcut-enabled:false}") boolean shortcutEnabled) {
		this.health = health;
		this.wellness = wellness;
		this.intervals = intervals;
		this.rateLimiter = rateLimiter;
		this.shortcutEnabled = shortcutEnabled;
	}

	/** Status und die letzten zwei Wochen; holt vorher neu von intervals.icu, wenn der letzte Abruf älter ist */
	@GetMapping("/me/health")
	public Status status(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		wellness.importIfStale(userId);
		return statusOf(userId);
	}

	/** Übernahme von intervals.icu einschalten – nur mit consent: true. 404 ohne intervals.icu-Verbindung. */
	@PostMapping("/me/health/intervals")
	public ResponseEntity<Status> enable(@AuthenticationPrincipal Jwt jwt,
			@RequestBody(required = false) ConsentRequest request) {
		if (request == null || !Boolean.TRUE.equals(request.consent())) {
			return ResponseEntity.badRequest().build();
		}
		Long userId = CurrentUser.id(jwt);
		try {
			if (wellness.enable(userId).isEmpty()) {
				return ResponseEntity.notFound().build();
			}
		} catch (RestClientException e) {
			// eingeschaltet ist es trotzdem; der nächste Abruf holt die Daten
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(statusOf(userId));
		}
		return ResponseEntity.ok(statusOf(userId));
	}

	/** Jetzt neu holen. 404, wenn die Übernahme aus ist. */
	@PostMapping("/me/health/intervals/import")
	public ResponseEntity<Status> importNow(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		try {
			if (wellness.importHistory(userId).isEmpty()) {
				return ResponseEntity.notFound().build();
			}
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
		return ResponseEntity.ok(statusOf(userId));
	}

	/**
	 * Schlaf & Erholung auch der KI zeigen (KI-Modus, Werkzeug get_wellness) – nur mit share: true als ausdrückliche
	 * Einwilligung; share: false nimmt sie zurück. 404, wenn die Übernahme aus ist.
	 */
	@org.springframework.web.bind.annotation.PutMapping("/me/health/ai")
	public ResponseEntity<Status> shareWithAi(@AuthenticationPrincipal Jwt jwt,
			@RequestBody(required = false) ShareRequest request) {
		if (request == null || request.share() == null) {
			return ResponseEntity.badRequest().build();
		}
		Long userId = CurrentUser.id(jwt);
		return wellness.shareWithAi(userId, request.share()).map(settings -> ResponseEntity.ok(statusOf(userId)))
				.orElse(ResponseEntity.notFound().build());
	}

	/** Ausschalten: Einwilligung widerrufen, alle Gesundheitsdaten löschen */
	@DeleteMapping("/me/health")
	public ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt) {
		Long userId = CurrentUser.id(jwt);
		wellness.disable(userId);
		health.disconnect(userId);
		return ResponseEntity.noContent().build();
	}

	/** Kurzbefehl: Schlüssel erzeugen – nur mit consent: true. Ein alter Schlüssel wird ungültig. */
	@PostMapping("/me/health/shortcut")
	public ResponseEntity<ConnectResponse> connectShortcut(@AuthenticationPrincipal Jwt jwt,
			@RequestBody(required = false) ConsentRequest request) {
		if (!shortcutEnabled) {
			return ResponseEntity.notFound().build();
		}
		if (request == null || !Boolean.TRUE.equals(request.consent())) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(new ConnectResponse(health.connect(CurrentUser.id(jwt))));
	}

	/** Upload vom Kurzbefehl. 404 bei unbekanntem Schlüssel oder abgeschaltet, 400 mit Meldung bei unpassenden Werten. */
	@PostMapping("/health-data")
	public ResponseEntity<?> upload(@RequestHeader(name = "X-Health-Key", required = false) String key,
			@RequestBody(required = false) HealthService.Upload upload) {
		if (!shortcutEnabled) {
			return ResponseEntity.notFound().build();
		}
		var owner = health.ownerOf(key);
		if (owner.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		if (!rateLimiter.tryAcquire("health:" + HealthService.hash(key.trim()), UPLOAD_LIMIT)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3600").build();
		}
		if (upload == null) {
			return ResponseEntity.badRequest().body(Map.of("message", "Keine Werte mitgeschickt."));
		}
		return ResponseEntity.ok(health.upload(owner.get(), upload));
	}

	@ExceptionHandler(HealthService.InvalidValueException.class)
	public ResponseEntity<Map<String, String>> invalid(HealthService.InvalidValueException e) {
		return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	}

	private Status statusOf(Long userId) {
		var settings = wellness.findByOwner(userId);
		return new Status(intervals.findByOwner(userId).isPresent(), settings.isPresent(),
				settings.map(WellnessImport::getConsentAt).orElse(null),
				settings.map(WellnessImport::getLastImportAt).orElse(null),
				settings.map(s -> s.getSharedWithAiAt() != null).orElse(false), health.recent(userId));
	}

}
