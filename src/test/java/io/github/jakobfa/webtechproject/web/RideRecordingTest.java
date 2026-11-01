package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Aufgezeichnete Fahrt aus dem Player → .fit-Datei zum Hochladen */
@SpringBootTest
@AutoConfigureMockMvc
class RideRecordingTest {

	private static final String RIDE = """
			{ "startedAt": "2026-10-26T17:00:00Z", "title": "Sweet Spot 3 × 12",
			  "samples": [ { "second": 0, "power": 200, "cadence": 90, "heartRate": 130 },
			               { "second": 1, "power": 205, "cadence": 91, "heartRate": 131 } ] }
			""";

	@Autowired
	private MockMvc mockMvc;

	private ResultActions send(String authorization, String json) throws Exception {
		var request = post("/me/recordings/fit").contentType(MediaType.APPLICATION_JSON).content(json);
		if (authorization != null) {
			request.header("Authorization", authorization);
		}
		return mockMvc.perform(request);
	}

	@Test
	void returnsAFitFileNamedAfterTheWorkout() throws Exception {
		send(registerNewUser(mockMvc), RIDE)
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"quaelmich-2026-10-26-sweet-spot-3x12.fit\""));
	}

	@Test
	void needsALogin() throws Exception {
		send(null, RIDE).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsImpossibleValues() throws Exception {
		String token = registerNewUser(mockMvc);

		send(token, RIDE.replace("\"power\": 200", "\"power\": 5000")).andExpect(status().isBadRequest());
		send(token, RIDE.replace("\"second\": 1", "\"second\": 0")).andExpect(status().isBadRequest());
		send(token, RIDE.replace("\"power\": 200,", "\"power\": 200, \"speed\": 400,")).andExpect(status().isBadRequest());
		send(token, RIDE.replace("\"power\": 200,", "\"power\": 200, \"speed\": 31.5,")).andExpect(status().isOk());
		send(token, """
				{ "startedAt": "2026-10-26T17:00:00Z", "samples": [] }
				""").andExpect(status().isBadRequest());
	}

}
