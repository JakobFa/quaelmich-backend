package io.github.jakobfa.webtechproject.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.strava.StravaConnection;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.integration.strava.StravaService.NotConfiguredException;
import io.github.jakobfa.webtechproject.integration.strava.StravaService.ProfileSuggestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Verbindung des angemeldeten Nutzers zu Strava (OAuth).
 * Das Frontend leitet zu Strava weiter; nach dem Rücksprung schickt es den Code hierher.
 */
@RestController
public class StravaController {

	/**
	 * scope: die von Strava im Rücksprung gemeldeten Erlaubnisse; purpose: wofür neu verbunden wurde ("uploads" =
	 * für den automatischen Upload, sonst für die Beschreibung)
	 */
	public record ConnectRequest(@NotBlank @Size(max = 200) String code, @Size(max = 200) String scope,
			@Size(max = 20) String purpose) {
	}

	/**
	 * @param available ist Strava auf dem Server eingerichtet?
	 * @param clientId  öffentliche ID der Strava-App (fürs Weiterleiten zu Strava)
	 * @param canWriteActivities hat Strava das Bearbeiten von Aktivitäten erlaubt (activity:write)?
	 * @param writeDescriptions  Training in die Beschreibung bestätigter Fahrten schreiben
	 */
	public record StatusResponse(boolean available, String clientId, boolean connected, String athleteName,
			Instant connectedAt, boolean canWriteActivities, boolean writeDescriptions, boolean uploadRides) {
	}

	public record DescriptionSetting(boolean enabled) {
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
			stravaService.connect(userId, request.code(), request.scope(), request.purpose());
			return ResponseEntity.ok(statusOf(userId));
		} catch (NotConfiguredException e) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		} catch (HttpClientErrorException e) {
			return ResponseEntity.unprocessableContent().build();
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

	/**
	 * FTP, Gewicht und geschätzter Schwellenpuls aus Strava – zum Übernehmen ins eigene Profil.
	 * 404, wenn nicht verbunden; 502, wenn Strava nicht antwortet.
	 */
	@GetMapping(path = "/me/strava/profile")
	public ResponseEntity<ProfileSuggestion> profileSuggestion(@AuthenticationPrincipal Jwt jwt) {
		try {
			return stravaService.profileSuggestion(CurrentUser.id(jwt))
					.map(ResponseEntity::ok)
					.orElse(ResponseEntity.notFound().build());
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

	/** Schalter für die Strava-Beschreibung. 404 ohne Verbindung, 409 ohne Erlaubnis activity:write */
	@PutMapping(path = "/me/strava/descriptions")
	public ResponseEntity<StatusResponse> setDescriptions(@AuthenticationPrincipal Jwt jwt,
			@RequestBody DescriptionSetting setting) {
		Long userId = CurrentUser.id(jwt);
		try {
			return stravaService.setWriteDescriptions(userId, setting.enabled())
					.map(connection -> ResponseEntity.ok(statusOf(userId)))
					.orElse(ResponseEntity.notFound().build());
		} catch (StravaService.MissingScopeException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	/** Schalter für den automatischen Upload der Player-Fahrten. 404 ohne Verbindung, 409 ohne Erlaubnis */
	@PutMapping(path = "/me/strava/uploads")
	public ResponseEntity<StatusResponse> setUploads(@AuthenticationPrincipal Jwt jwt,
			@RequestBody DescriptionSetting setting) {
		Long userId = CurrentUser.id(jwt);
		try {
			return stravaService.setUploadRides(userId, setting.enabled())
					.map(connection -> ResponseEntity.ok(statusOf(userId)))
					.orElse(ResponseEntity.notFound().build());
		} catch (StravaService.MissingScopeException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
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
				connection.map(StravaConnection::getConnectedAt).orElse(null),
				connection.map(StravaConnection::canWriteActivities).orElse(false),
				connection.map(StravaConnection::isWriteDescriptions).orElse(false),
				connection.map(StravaConnection::isUploadRides).orElse(false));
	}

}
