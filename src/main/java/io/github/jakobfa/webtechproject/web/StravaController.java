package io.github.jakobfa.webtechproject.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.strava.StravaConnection;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.integration.strava.StravaService.NotConfiguredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Verbindung des angemeldeten Nutzers zu Strava (OAuth).
 * Das Frontend leitet zu Strava weiter; nach dem Rücksprung schickt es den Code hierher.
 */
@RestController
public class StravaController {

	public record ConnectRequest(@NotBlank @Size(max = 200) String code) {
	}

	/**
	 * @param available ist Strava auf dem Server eingerichtet?
	 * @param clientId  öffentliche ID der Strava-App (fürs Weiterleiten zu Strava)
	 */
	public record StatusResponse(boolean available, String clientId, boolean connected, String athleteName,
			Instant connectedAt) {
	}

	private final StravaService stravaService;

	public StravaController(StravaService stravaService) {
		this.stravaService = stravaService;
	}

	@GetMapping(path = "/me/strava")
	public StatusResponse status(@AuthenticationPrincipal Jwt jwt) {
		return statusOf(CurrentUser.id(jwt));
	}

	/** 503 ohne Strava-App auf dem Server, 422 wenn Strava den Code ablehnt, 502 wenn Strava nicht erreichbar ist */
	@PostMapping(path = "/me/strava")
	public ResponseEntity<StatusResponse> connect(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ConnectRequest request) {
		Long userId = CurrentUser.id(jwt);
		try {
			stravaService.connect(userId, request.code());
			return ResponseEntity.ok(statusOf(userId));
		} catch (NotConfiguredException e) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		} catch (HttpClientErrorException e) {
			return ResponseEntity.unprocessableContent().build();
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

	/** Trennt die Verbindung und löscht alle von Strava übernommenen Fahrten */
	@DeleteMapping(path = "/me/strava")
	public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
		stravaService.disconnect(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}

	private StatusResponse statusOf(Long userId) {
		boolean available = stravaService.isConfigured();
		var connection = available ? stravaService.findByOwner(userId) : java.util.Optional.<StravaConnection>empty();
		return new StatusResponse(available, available ? stravaService.clientId() : null, connection.isPresent(),
				connection.map(StravaConnection::getAthleteName).orElse(null),
				connection.map(StravaConnection::getConnectedAt).orElse(null));
	}

}
