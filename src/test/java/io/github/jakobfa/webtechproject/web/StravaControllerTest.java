package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;

import io.github.jakobfa.webtechproject.integration.strava.StravaActivity;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.HeartRateZones;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaAthlete;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaProfile;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.ZoneRange;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.Zones;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.TokenResponse;

/**
 * Ablauf Verbinden → Fahrten holen → Trennen. Strava selbst ist durch eine Attrappe ersetzt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StravaControllerTest {

	private static final String TODAY = LocalDate.now(ZoneId.of("Europe/Berlin")).toString();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StravaClient client;

	private ResultActions connect(String token) throws Exception {
		return connect(token, "read,activity:read_all");
	}

	private ResultActions connect(String token, String scope) throws Exception {
		return mockMvc.perform(post("/me/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"%s\" }".formatted(scope)));
	}

	/** Strava liefert einen schon abgelaufenen Token → beim ersten Abruf muss erneuert werden */
	private void stravaAcceptsTheCode() {
		when(client.exchangeCode("abc"))
				.thenReturn(new TokenResponse("alt", "ref", 0, new StravaAthlete(99L, "Jakob")));
		when(client.refresh("ref")).thenReturn(new TokenResponse("neu", "ref2", 4_000_000_000L, null));
	}

	@Test
	void showsTheClientIdForTheRedirect() throws Exception {
		mockMvc.perform(get("/me/strava").header("Authorization", registerNewUser(mockMvc)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.clientId").value("12345"))
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void connectsAndImportsRidesWithARefreshedToken() throws Exception {
		String token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		stravaAcceptsTheCode();
		when(client.listActivities(eq("neu"), any(), any())).thenReturn(List.of(
				new StravaActivity(777L, "Ride", TODAY + "T07:30:00Z", 1800, 9800.0, null, null, null)));

		connect(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.athleteName").value("Jakob"));

		mockMvc.perform(post("/me/activities/import").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activities").value(1));
		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(content().string(containsString("https://www.strava.com/activities/777")));
	}

	@Test
	void disconnectingDeletesTheStravaRides() throws Exception {
		String token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		stravaAcceptsTheCode();
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				new StravaActivity(778L, "Ride", TODAY + "T07:30:00Z", 1800, 9800.0, null, null, null)));
		connect(token);
		mockMvc.perform(post("/me/activities/import").header("Authorization", token));

		mockMvc.perform(delete("/me/strava").header("Authorization", token)).andExpect(status().isNoContent());

		verify(client).deauthorize("neu");
		mockMvc.perform(get("/me/strava").header("Authorization", token))
				.andExpect(jsonPath("$.connected").value(false));
		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(content().string(not(containsString("strava.com/activities/778"))));
	}

	@Test
	void suggestsFtpWeightAndThresholdHeartRateFromStrava() throws Exception {
		String token = registerNewUser(mockMvc);
		stravaAcceptsTheCode();
		when(client.profile("neu")).thenReturn(new StravaProfile(260, 71.84));
		when(client.zones("neu")).thenReturn(new Zones(new HeartRateZones(false, List.of(
				new ZoneRange(0, 120), new ZoneRange(120, 150), new ZoneRange(150, 165), new ZoneRange(165, 178),
				new ZoneRange(178, -1)))));
		connect(token, "read,activity:read_all,profile:read_all");

		mockMvc.perform(get("/me/strava/profile").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profileAccess").value(true))
				.andExpect(jsonPath("$.ftp").value(260))
				.andExpect(jsonPath("$.weightKg").value(71.8))
				.andExpect(jsonPath("$.thresholdHeartRate").value(178));
	}

	@Test
	void withoutProfilePermissionAsksToReconnect() throws Exception {
		String token = registerNewUser(mockMvc);
		stravaAcceptsTheCode();
		connect(token);

		mockMvc.perform(get("/me/strava/profile").header("Authorization", token))
				.andExpect(jsonPath("$.profileAccess").value(false))
				.andExpect(jsonPath("$.ftp").doesNotExist());
	}

	@Test
	void rejectedCodeGives422() throws Exception {
		when(client.exchangeCode("abc")).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

		connect(registerNewUser(mockMvc)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void importWithoutAnyConnectionIsAConflict() throws Exception {
		mockMvc.perform(post("/me/activities/import").header("Authorization", registerWithProfile(mockMvc)))
				.andExpect(status().isConflict());
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(get("/me/strava")).andExpect(status().isUnauthorized());
	}

}
