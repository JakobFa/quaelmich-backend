package io.github.jakobfa.webtechproject.health;

import java.time.Duration;
import java.util.Map;

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

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.auth.RateLimiter;

/**
 * Apple Health: Im Profil den Schlüssel einrichten; der Kurzbefehl auf dem iPhone schickt damit täglich Schlaf und
 * Bewegung an POST /health-data (ohne Login, der Schlüssel im Header X-Health-Key ist die Berechtigung).
 */
@RestController
public class HealthDataController {

	/** Uploads je Schlüssel und Stunde – ein Kurzbefehl schickt einmal am Tag, Platz für Tests von Hand */
	static final RateLimiter.Limit UPLOAD_LIMIT = new RateLimiter.Limit(30, Duration.ofHours(1));

	public record ConnectRequest(Boolean consent) {
	}

	public record ConnectResponse(String key) {
	}

	private final HealthService health;
	private final RateLimiter rateLimiter;

	public HealthDataController(HealthService health, RateLimiter rateLimiter) {
		this.health = health;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/me/health")
	public HealthService.Status status(@AuthenticationPrincipal Jwt jwt) {
		return health.status(CurrentUser.id(jwt));
	}

	/** Schlüssel erzeugen – nur mit ausdrücklicher Einwilligung (consent: true). Ein alter Schlüssel wird ungültig. */
	@PostMapping("/me/health")
	public ResponseEntity<ConnectResponse> connect(@AuthenticationPrincipal Jwt jwt,
			@RequestBody(required = false) ConnectRequest request) {
		if (request == null || !Boolean.TRUE.equals(request.consent())) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(new ConnectResponse(health.connect(CurrentUser.id(jwt))));
	}

	/** Verbindung beenden; alle Gesundheitsdaten werden gelöscht */
	@DeleteMapping("/me/health")
	public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
		health.disconnect(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}

	/** Upload vom Kurzbefehl. 404 bei unbekanntem Schlüssel, 400 mit Meldung bei unpassenden Werten. */
	@PostMapping("/health-data")
	public ResponseEntity<?> upload(@RequestHeader(name = "X-Health-Key", required = false) String key,
			@RequestBody(required = false) HealthService.Upload upload) {
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

}
