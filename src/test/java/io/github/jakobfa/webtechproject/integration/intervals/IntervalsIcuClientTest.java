package io.github.jakobfa.webtechproject.integration.intervals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Testet den Client gegen einen simulierten intervals.icu-Server – ohne echten Account und ohne Internet.
 */
class IntervalsIcuClientTest {

	private static final IntervalsCredentials CREDENTIALS = new IntervalsCredentials("geheim", "i42");
	// "API_KEY:geheim" in Base64
	private static final String AUTH_HEADER = "Basic QVBJX0tFWTpnZWhlaW0=";

	private final IntervalsEvent event = new IntervalsEvent("WORKOUT", "2026-10-21T00:00:00", "Ride",
			"Sweet Spot 3 × 12", "Beschreibung", "sweet-spot-3x12.fit", "AAAA", "quaelmich-2026-10-21");

	private MockRestServiceServer server;
	private IntervalsIcuClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new IntervalsIcuClient(new IntervalsProperties("https://intervals.icu", null, null), builder);
	}

	@Test
	void sendsEventsWithBasicAuthToTheUsersCalendar() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42/events/bulk?upsert=true"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", AUTH_HEADER))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].category").value("WORKOUT"))
				.andExpect(jsonPath("$[0].start_date_local").value("2026-10-21T00:00:00"))
				.andExpect(jsonPath("$[0].file_contents_base64").value("AAAA"))
				.andExpect(jsonPath("$[0].external_id").value("quaelmich-2026-10-21"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		client.upsertEvents(CREDENTIALS, List.of(event));

		server.verify();
	}

	@Test
	void deletesEventsByExternalId() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42/events/bulk-delete"))
				.andExpect(method(org.springframework.http.HttpMethod.PUT))
				.andExpect(content().json("[{\"external_id\": \"quaelmich-2026-10-22\"}]"))
				.andRespond(withSuccess());

		client.deleteEvents(CREDENTIALS, List.of("quaelmich-2026-10-22"));

		server.verify();
	}

	@Test
	void verifyAcceptsWorkingCredentials() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", AUTH_HEADER))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(client.verify(CREDENTIALS)).isTrue();
	}

	@Test
	void verifyRejectsWrongCredentials() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThat(client.verify(CREDENTIALS)).isFalse();
	}

	@Test
	void listsActivitiesAndIgnoresUnknownFields() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42/activities?oldest=2026-10-07&newest=2026-10-21"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", AUTH_HEADER))
				.andRespond(withSuccess("""
						[
						  { "id": "i1001", "start_date_local": "2026-10-20T17:30:00", "type": "Ride",
						    "moving_time": 5400, "distance": 45210.5, "icu_training_load": 68, "source": "WAHOO",
						    "icu_ftp": 245, "average_watts": 172 },
						  { "id": "s99", "source": "STRAVA", "_note": "STRAVA activities are not available via the API" }
						]
						""", MediaType.APPLICATION_JSON));

		List<IntervalsActivity> activities = client.listActivities(CREDENTIALS, LocalDate.of(2026, 10, 7),
				LocalDate.of(2026, 10, 21));

		assertThat(activities).hasSize(2);
		IntervalsActivity ride = activities.getFirst();
		assertThat(ride.isUsable()).isTrue();
		assertThat(ride.date()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(ride.movingTimeSeconds()).isEqualTo(5400);
		assertThat(ride.trainingLoad()).isEqualTo(68);
		assertThat(activities.get(1).isUsable()).as("Strava-Platzhalter").isFalse();
	}

	@Test
	void serverErrorsAreReported() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42/events/bulk?upsert=true"))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> client.upsertEvents(CREDENTIALS, List.of(event)))
				.isInstanceOf(RestClientException.class);
	}

	@Test
	void updatesTheFtpInTheRideSettings() {
		server.expect(requestTo("https://intervals.icu/api/v1/athlete/i42/sport-settings/Ride"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header("Authorization", AUTH_HEADER))
				.andExpect(content().json("{ \"ftp\": 265 }"))
				.andRespond(withSuccess());

		client.updateFtp(CREDENTIALS, 265);

		server.verify();
	}

}
