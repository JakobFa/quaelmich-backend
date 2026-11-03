package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.integration.strava.StravaClient;

/** Ein Upload hält bis zu ca. 5 s eine Verbindung zu Strava offen: zu viele hintereinander werden gebremst */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
class StravaUploadRateLimitTest {

	private static final String RIDE = """
			{ "startedAt": "2026-10-26T17:00:00Z", "title": "Tempo 2 × 15",
			  "samples": [ { "second": 0, "power": 200, "cadence": 90, "heartRate": 130, "speed": 30.5 },
			               { "second": 1, "power": 205, "cadence": 91, "heartRate": 131, "speed": 30.8 } ] }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StravaClient client;

	@Test
	void tooManyUploadsInAnHourAreRefused() throws Exception {
		String token = registerNewUser(mockMvc);
		String other = registerNewUser(mockMvc);

		for (int i = 0; i < RideRecordingController.UPLOAD_LIMIT.attempts(); i++) {
			// ohne Strava-Verbindung 404 – es zählt trotzdem als Versuch
			mockMvc.perform(post("/me/recordings/strava").header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON).content(RIDE)).andExpect(status().isNotFound());
		}

		mockMvc.perform(post("/me/recordings/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(RIDE)).andExpect(status().isTooManyRequests());
		// ein anderer Nutzer ist nicht betroffen
		mockMvc.perform(post("/me/recordings/strava").header("Authorization", other)
				.contentType(MediaType.APPLICATION_JSON).content(RIDE)).andExpect(status().isNotFound());
	}
}
