package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.jakobfa.webtechproject.integration.strava.StravaClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaAthlete;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.TokenResponse;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.UploadStatus;

/** Fahrten aus dem Player gehen auf Wunsch direkt zu Strava – nur mit Erlaubnis activity:write */
@SpringBootTest
@AutoConfigureMockMvc
class StravaUploadTest {

	private static final String RIDE = """
			{ "startedAt": "2026-10-26T17:00:00Z", "title": "Tempo 2 × 15",
			  "samples": [ { "second": 0, "power": 200, "cadence": 90, "heartRate": 130, "speed": 30.5 },
			               { "second": 1, "power": 205, "cadence": 91, "heartRate": 131, "speed": 30.8 } ] }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StravaClient client;

	private String token;

	@BeforeEach
	void user() throws Exception {
		token = registerNewUser(mockMvc);
		when(client.exchangeCode("abc")).thenReturn(new TokenResponse("acc", "ref", 4_000_000_000L,
				new StravaAthlete(99L, "Jakob")));
	}

	private ResultActions connect(String scope) throws Exception {
		return mockMvc.perform(post("/me/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"%s\" }".formatted(scope)));
	}

	private ResultActions connectForUploads(String scope) throws Exception {
		return mockMvc.perform(post("/me/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"%s\", \"purpose\": \"uploads\" }".formatted(scope)));
	}

	private ResultActions setUploads(boolean enabled) throws Exception {
		return mockMvc.perform(put("/me/strava/uploads").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"enabled\": %s }".formatted(enabled)));
	}

	private ResultActions upload() throws Exception {
		return mockMvc.perform(post("/me/recordings/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(RIDE));
	}

	@Test
	void uploadsTheRideAndReturnsTheStravaActivity() throws Exception {
		connect("read,activity:read_all,activity:write");
		when(client.upload(eq("acc"), any(byte[].class), eq("Tempo 2 × 15"), eq("quaelmich-1793034000")))
				.thenReturn(new UploadStatus(7L, null, "ready", 555L));

		upload().andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("DONE"))
				.andExpect(jsonPath("$.activityId").value(555));

		verify(client).updateSportType("acc", 555L, "VirtualRide");
	}

	@Test
	void theRideStaysUploadedEvenIfStravaRefusesToChangeTheSport() throws Exception {
		connect("read,activity:write");
		when(client.upload(anyString(), any(byte[].class), anyString(), anyString()))
				.thenReturn(new UploadStatus(7L, null, "ready", 555L));
		doThrow(new org.springframework.web.client.RestClientException("kaputt")).when(client)
				.updateSportType(anyString(), anyLong(), anyString());

		upload().andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DONE"));
	}

	@Test
	void aRideThatIsStillProcessingIsNotChangedYet() throws Exception {
		connect("read,activity:write");
		when(client.upload(anyString(), any(byte[].class), anyString(), anyString()))
				.thenReturn(new UploadStatus(7L, "Fehler", "error", null));

		upload().andExpect(status().isOk());

		verify(client, never()).updateSportType(anyString(), anyLong(), anyString());
	}

	@Test
	void waitsForStravaToFinishProcessing() throws Exception {
		connect("read,activity:write");
		when(client.upload(anyString(), any(byte[].class), anyString(), anyString()))
				.thenReturn(new UploadStatus(7L, null, "processing", null));
		when(client.uploadStatus("acc", 7L)).thenReturn(new UploadStatus(7L, null, "ready", 556L));

		upload().andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("DONE"))
				.andExpect(jsonPath("$.activityId").value(556));
	}

	@Test
	void reportsADuplicateInsteadOfAnError() throws Exception {
		connect("read,activity:write");
		when(client.upload(anyString(), any(byte[].class), anyString(), anyString()))
				.thenReturn(new UploadStatus(7L, "quaelmich.fit duplicate of activity 123", "error", null));

		upload().andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
	}

	@Test
	void reportsAFileThatStravaRejects() throws Exception {
		connect("read,activity:write");
		when(client.upload(anyString(), any(byte[].class), anyString(), anyString()))
				.thenReturn(new UploadStatus(7L, "The file is empty.", "error", null));

		upload().andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("FAILED"));
	}

	@Test
	void needsTheWritePermission() throws Exception {
		connect("read,activity:read_all");

		upload().andExpect(status().isConflict());

		verify(client, never()).upload(anyString(), any(byte[].class), anyString(), anyString());
	}

	@Test
	void needsAStravaConnection() throws Exception {
		upload().andExpect(status().isNotFound());
	}

	@Test
	void theAutomaticUploadIsOffUntilSwitchedOn() throws Exception {
		connect("read,activity:read_all,activity:write").andExpect(jsonPath("$.uploadRides").value(false));

		setUploads(true).andExpect(status().isOk()).andExpect(jsonPath("$.uploadRides").value(true));
		setUploads(false).andExpect(status().isOk()).andExpect(jsonPath("$.uploadRides").value(false));
	}

	@Test
	void theAutomaticUploadNeedsThePermission() throws Exception {
		connect("read,activity:read_all");

		setUploads(true).andExpect(status().isConflict());
	}

	@Test
	void connectingWithoutPermissionSwitchesTheUploadOff() throws Exception {
		connect("read,activity:write");
		setUploads(true).andExpect(status().isOk());

		connect("read,activity:read_all").andExpect(jsonPath("$.uploadRides").value(false));
	}

	@Test
	void allowingTheUploadDoesNotSwitchOnTheDescriptions() throws Exception {
		connect("read,activity:read_all");

		connectForUploads("read,activity:read_all,activity:write")
				.andExpect(jsonPath("$.uploadRides").value(true))
				.andExpect(jsonPath("$.writeDescriptions").value(false));
	}

	@Test
	void allowingTheDescriptionsDoesNotSwitchOnTheUpload() throws Exception {
		connect("read,activity:read_all,activity:write")
				.andExpect(jsonPath("$.writeDescriptions").value(true))
				.andExpect(jsonPath("$.uploadRides").value(false));
	}

	@Test
	void reconnectingKeepsTheUploadSwitch() throws Exception {
		connectForUploads("read,activity:write");

		connect("read,activity:write").andExpect(jsonPath("$.uploadRides").value(true));
	}

	@Test
	void rejectsABrokenRecording() throws Exception {
		connect("read,activity:write");

		mockMvc.perform(post("/me/recordings/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(RIDE.replace("\"second\": 1", "\"second\": 0")))
				.andExpect(status().isBadRequest());

		verify(client, never()).upload(anyString(), any(byte[].class), anyString(), anyString());
	}

}
