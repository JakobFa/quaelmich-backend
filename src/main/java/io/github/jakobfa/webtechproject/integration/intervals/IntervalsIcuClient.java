package io.github.jakobfa.webtechproject.integration.intervals;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Spricht mit der API von intervals.icu – jeweils mit den Zugangsdaten des betreffenden Nutzers.
 * Authentifizierung per HTTP Basic Auth: Benutzername "API_KEY", Passwort = persönlicher Schlüssel.
 */
@Component
public class IntervalsIcuClient {

	private final RestClient restClient;

	@Autowired
	public IntervalsIcuClient(IntervalsProperties properties) {
		this(properties, RestClient.builder());
	}

	/** Für Tests: erlaubt einen vorbereiteten Builder (z. B. mit MockRestServiceServer) */
	IntervalsIcuClient(IntervalsProperties properties, RestClient.Builder builder) {
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	/**
	 * Prüft, ob Schlüssel und Athleten-ID gültig sind, indem das Athletenprofil abgefragt wird.
	 * Liefert false bei abgelehnten Zugangsdaten; Netzwerk- oder Serverfehler werden weitergereicht.
	 */
	public boolean verify(IntervalsCredentials credentials) {
		try {
			restClient.get()
					.uri("/api/v1/athlete/{athleteId}", credentials.athleteId())
					.header(HttpHeaders.AUTHORIZATION, basicAuth(credentials))
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (HttpClientErrorException e) {
			HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
			if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN || status == HttpStatus.NOT_FOUND) {
				return false;
			}
			throw e;
		}
	}

	/**
	 * Legt die Workouts im Kalender an. Workouts mit bereits bekannter external_id werden ersetzt (upsert).
	 */
	public void upsertEvents(IntervalsCredentials credentials, List<IntervalsEvent> events) {
		restClient.post()
				.uri("/api/v1/athlete/{athleteId}/events/bulk?upsert=true", credentials.athleteId())
				.header(HttpHeaders.AUTHORIZATION, basicAuth(credentials))
				.contentType(MediaType.APPLICATION_JSON)
				.body(events)
				.retrieve()
				.toBodilessEntity();
	}

	/** Löscht Workouts aus dem Kalender anhand ihrer external_id (unbekannte IDs werden ignoriert) */
	public void deleteEvents(IntervalsCredentials credentials, List<String> externalIds) {
		restClient.put()
				.uri("/api/v1/athlete/{athleteId}/events/bulk-delete", credentials.athleteId())
				.header(HttpHeaders.AUTHORIZATION, basicAuth(credentials))
				.contentType(MediaType.APPLICATION_JSON)
				.body(externalIds.stream().map(id -> java.util.Map.of("external_id", id)).toList())
				.retrieve()
				.toBodilessEntity();
	}

	/** Aktivitäten im Zeitraum (beide Tage eingeschlossen) */
	public List<IntervalsActivity> listActivities(IntervalsCredentials credentials, LocalDate oldest, LocalDate newest) {
		List<IntervalsActivity> activities = restClient.get()
				.uri("/api/v1/athlete/{athleteId}/activities?oldest={oldest}&newest={newest}", credentials.athleteId(),
						oldest, newest)
				.header(HttpHeaders.AUTHORIZATION, basicAuth(credentials))
				.retrieve()
				.body(new ParameterizedTypeReference<List<IntervalsActivity>>() {
				});
		return activities == null ? List.of() : activities;
	}

	private static String basicAuth(IntervalsCredentials credentials) {
		String userAndPassword = "API_KEY:" + credentials.apiKey();
		return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
	}

}
