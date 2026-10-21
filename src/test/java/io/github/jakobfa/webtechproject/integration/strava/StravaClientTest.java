package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Testet den Strava-Client gegen einen simulierten Strava-Server.
 */
class StravaClientTest {

	private MockRestServiceServer server;
	private StravaClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new StravaClient(new StravaProperties("https://www.strava.com", "12345", "geheim"), builder);
	}

	@Test
	void exchangesTheCodeForTokens() {
		server.expect(requestTo("https://www.strava.com/oauth/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().formDataContains(java.util.Map.of("client_id", "12345", "client_secret", "geheim",
						"code", "abc", "grant_type", "authorization_code")))
				.andRespond(withSuccess("""
						{ "access_token": "acc", "refresh_token": "ref", "expires_at": 1792600000,
						  "athlete": { "id": 99, "firstname": "Jakob", "lastname": "X" } }
						""", MediaType.APPLICATION_JSON));

		var tokens = client.exchangeCode("abc");

		assertThat(tokens.accessToken()).isEqualTo("acc");
		assertThat(tokens.refreshToken()).isEqualTo("ref");
		assertThat(tokens.athlete().firstname()).isEqualTo("Jakob");
		assertThat(tokens.toString()).doesNotContain("acc");
	}

	@Test
	void listsActivitiesWithBearerToken() {
		server.expect(requestTo(
				"https://www.strava.com/api/v3/athlete/activities?after=1791000000&before=1792000000&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer acc"))
				.andRespond(withSuccess("""
						[ { "id": 777, "sport_type": "Ride", "start_date_local": "2026-10-21T07:30:00Z",
						    "moving_time": 1800, "distance": 9800.0, "commute": true, "kudos_count": 3 } ]
						""", MediaType.APPLICATION_JSON));

		List<StravaActivity> activities = client.listActivities("acc", Instant.ofEpochSecond(1791000000),
				Instant.ofEpochSecond(1792000000));

		assertThat(activities).singleElement().satisfies(activity -> {
			assertThat(activity.isUsableRide()).isTrue();
			assertThat(activity.toRecordedRide(250).id()).isEqualTo("strava-777");
			assertThat(activity.toRecordedRide(250).start().getHour()).as("Ortszeit, nicht UTC").isEqualTo(7);
		});
	}

}
