package io.github.jakobfa.webtechproject.integration.intervals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Spricht mit der API von intervals.icu.
 * Authentifizierung per HTTP Basic Auth: Benutzername "API_KEY", Passwort = persönlicher Schlüssel.
 */
@Component
public class IntervalsIcuClient {

	private final RestClient restClient;
	private final IntervalsProperties properties;

	@Autowired
	public IntervalsIcuClient(IntervalsProperties properties) {
		this(properties, RestClient.builder());
	}

	/** Für Tests: erlaubt einen vorbereiteten Builder (z. B. mit MockRestServiceServer) */
	IntervalsIcuClient(IntervalsProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	/**
	 * Legt die Workouts im Kalender an. Workouts mit bereits bekannter external_id werden ersetzt (upsert).
	 */
	public void upsertEvents(List<IntervalsEvent> events) {
		restClient.post()
				.uri("/api/v1/athlete/{athleteId}/events/bulk?upsert=true", properties.athleteId())
				.header(HttpHeaders.AUTHORIZATION, basicAuth())
				.contentType(MediaType.APPLICATION_JSON)
				.body(events)
				.retrieve()
				.toBodilessEntity();
	}

	private String basicAuth() {
		String credentials = "API_KEY:" + properties.apiKey();
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

}
