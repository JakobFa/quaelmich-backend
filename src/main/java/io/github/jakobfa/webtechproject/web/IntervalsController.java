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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnection;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService.InvalidCredentialsException;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsSyncService.SyncResult;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Verbindung des angemeldeten Nutzers zu seinem eigenen intervals.icu-Konto.
 * Der API-Schlüssel wird nur entgegengenommen, nie zurückgegeben.
 */
@RestController
public class IntervalsController {

	public record ConnectRequest(
			@NotBlank @Size(max = 200) String apiKey,
			/** z. B. "i12345"; leer = Konto, dem der Schlüssel gehört */
			@Pattern(regexp = "^$|^0$|^i?\\d{1,12}$") String athleteId) {
	}

	public record StatusResponse(boolean connected, String intervalsAthleteId, Instant connectedAt) {

		static StatusResponse of(IntervalsConnection connection) {
			return new StatusResponse(true, connection.getIntervalsAthleteId(), connection.getConnectedAt());
		}

		static StatusResponse disconnected() {
			return new StatusResponse(false, null, null);
		}
	}

	private final IntervalsConnectionService connectionService;
	private final IntervalsSyncService syncService;
	private final AthleteService athleteService;

	public IntervalsController(IntervalsConnectionService connectionService, IntervalsSyncService syncService,
			AthleteService athleteService) {
		this.connectionService = connectionService;
		this.syncService = syncService;
		this.athleteService = athleteService;
	}

	@GetMapping(path = "/me/intervals")
	public StatusResponse status(@AuthenticationPrincipal Jwt jwt) {
		return connectionService.findByOwner(CurrentUser.id(jwt))
				.map(StatusResponse::of)
				.orElse(StatusResponse.disconnected());
	}

	/** Verbinden oder Schlüssel ändern. 422, wenn intervals.icu die Zugangsdaten ablehnt. */
	@PutMapping(path = "/me/intervals")
	public ResponseEntity<StatusResponse> connect(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ConnectRequest request) {
		try {
			IntervalsConnection connection = connectionService.connect(CurrentUser.id(jwt), request.apiKey(),
					request.athleteId());
			return ResponseEntity.ok(StatusResponse.of(connection));
		} catch (InvalidCredentialsException e) {
			return ResponseEntity.unprocessableContent().build();
		} catch (RestClientException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

	@DeleteMapping(path = "/me/intervals")
	public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
		connectionService.disconnect(CurrentUser.id(jwt));
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/me/intervals/sync")
	public ResponseEntity<SyncResult> sync(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "14") int days) {
		if (days < 1 || days > 60) {
			return ResponseEntity.badRequest().build();
		}
		Long userId = CurrentUser.id(jwt);
		var credentials = connectionService.credentialsOf(userId);
		if (credentials.isEmpty()) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build(); // noch nicht verbunden
		}
		var athleteId = athleteService.findByOwner(userId).map(Athlete::getId);
		if (athleteId.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		try {
			return syncService.syncUpcoming(credentials.get(), athleteId.get(), days)
					.map(ResponseEntity::ok)
					.orElse(ResponseEntity.notFound().build());
		} catch (RestClientException e) {
			// intervals.icu nicht erreichbar oder Schlüssel inzwischen ungültig
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
	}

}
