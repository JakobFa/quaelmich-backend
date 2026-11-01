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
	void uploadsAFitFileAsIndoorRide() {
		server.expect(requestTo("https://www.strava.com/api/v3/uploads"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer acc"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
				.andExpect(content().string(org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("name=\"data_type\""),
						org.hamcrest.Matchers.containsString("name=\"trainer\""),
						org.hamcrest.Matchers.containsString("name=\"external_id\""),
						org.hamcrest.Matchers.containsString("quaelmich-123"),
						org.hamcrest.Matchers.containsString("filename=\"quaelmich.fit\""))))
				.andRespond(withSuccess("""
						{ "id": 7, "status": "Your activity is still being processed.", "error": null,
						  "activity_id": null, "external_id": "quaelmich-123" }
						""", MediaType.APPLICATION_JSON));

		var status = client.upload("acc", new byte[] { 1, 2, 3 }, "Tempo 2 × 15", "quaelmich-123");

		assertThat(status.id()).isEqualTo(7L);
		assertThat(status.activityId()).isNull();
		assertThat(status.error()).isNull();
	}

	@Test
	void changesTheSportTypeOfAnActivity() {
		server.expect(requestTo("https://www.strava.com/api/v3/activities/555"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header("Authorization", "Bearer acc"))
				.andExpect(content().json("{\"sport_type\": \"VirtualRide\"}"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		client.updateSportType("acc", 555, "VirtualRide");

		server.verify();
	}

	@Test
	void readsTheUploadStatus() {
		server.expect(requestTo("https://www.strava.com/api/v3/uploads/7"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer acc"))
				.andRespond(withSuccess("""
						{ "id": 7, "status": "Your activity is ready.", "error": null, "activity_id": 555 }
						""", MediaType.APPLICATION_JSON));

		assertThat(client.uploadStatus("acc", 7).activityId()).isEqualTo(555L);
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
			assertThat(activity.isUsable()).isTrue();
			assertThat(activity.toRecordedActivity(250, null).id()).isEqualTo("strava-777");
			assertThat(activity.toRecordedActivity(250, null).start().getHour()).as("Ortszeit, nicht UTC").isEqualTo(7);
		});
	}

}
