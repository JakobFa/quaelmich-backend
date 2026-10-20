package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionRepository;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsCredentials;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;

/**
 * Ablauf Verbinden → Sync → Trennen. intervals.icu selbst ist durch eine Attrappe ersetzt.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntervalsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private IntervalsConnectionRepository connectionRepository;

	@MockitoBean
	private IntervalsIcuClient client;

	private ResultActions connect(String token, String apiKey) throws Exception {
		return mockMvc.perform(put("/me/intervals").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"apiKey\": \"%s\", \"athleteId\": \"i42\" }".formatted(apiKey)));
	}

	@Test
	void isNotConnectedAtFirst() throws Exception {
		mockMvc.perform(get("/me/intervals").header("Authorization", registerNewUser(mockMvc)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void connectsWithWorkingKeyAndNeverReturnsIt() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerNewUser(mockMvc);

		connect(token, "mein-geheimer-schluessel")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.intervalsAthleteId").value("i42"))
				.andExpect(jsonPath("$.apiKey").doesNotExist());

		mockMvc.perform(get("/me/intervals").header("Authorization", token))
				.andExpect(jsonPath("$.connected").value(true))
				.andExpect(jsonPath("$.apiKey").doesNotExist());
	}

	@Test
	void storesTheKeyOnlyEncrypted() throws Exception {
		when(client.verify(any())).thenReturn(true);
		connect(registerNewUser(mockMvc), "klartext-schluessel-123");

		assertThat(connectionRepository.findAll()).allSatisfy(connection ->
				assertThat(connection.getEncryptedApiKey()).doesNotContain("klartext"));
	}

	@Test
	void rejectsKeysThatIntervalsDoesNotAccept() throws Exception {
		when(client.verify(any())).thenReturn(false);
		String token = registerNewUser(mockMvc);

		connect(token, "falsch").andExpect(status().isUnprocessableContent());
		mockMvc.perform(get("/me/intervals").header("Authorization", token))
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void syncUsesTheUsersOwnDecryptedKey() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		connect(token, "schluessel-von-lina");

		mockMvc.perform(post("/me/intervals/sync").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uploaded").isNumber());

		verify(client).upsertEvents(eq(new IntervalsCredentials("schluessel-von-lina", "i42")),
				argThat(events -> !events.isEmpty()));
	}

	@Test
	void autoSyncIsOnByDefaultAndNewPlansAreSentAutomatically() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerWithProfile(mockMvc);
		connect(token, "auto-schluessel").andExpect(jsonPath("$.autoSync").value(true));
		clearInvocations(client);

		mockMvc.perform(post("/me/plan").header("Authorization", token)).andExpect(status().isCreated());

		verify(client).upsertEvents(eq(new IntervalsCredentials("auto-schluessel", "i42")),
				argThat(events -> !events.isEmpty()));
		mockMvc.perform(get("/me/intervals").header("Authorization", token))
				.andExpect(jsonPath("$.lastSyncedAt").isString());
	}

	@Test
	void autoSyncCanBeSwitchedOff() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerWithProfile(mockMvc);
		connect(token, "aus-schluessel");

		mockMvc.perform(put("/me/intervals/settings").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"autoSync\": false }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.autoSync").value(false));
		clearInvocations(client);

		mockMvc.perform(post("/me/plan").header("Authorization", token)).andExpect(status().isCreated());

		verify(client, never()).upsertEvents(any(), any());
	}

	@Test
	void planCreationStillWorksWhenIntervalsFails() throws Exception {
		when(client.verify(any())).thenReturn(true);
		org.mockito.Mockito.doThrow(new org.springframework.web.client.ResourceAccessException("offline"))
				.when(client).upsertEvents(any(), any());
		String token = registerWithProfile(mockMvc);
		connect(token, "schluessel");

		mockMvc.perform(post("/me/plan").header("Authorization", token)).andExpect(status().isCreated());
	}

	@Test
	void importsActivitiesForConnectedUsers() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerWithProfile(mockMvc);
		connect(token, "import-schluessel");

		mockMvc.perform(post("/me/intervals/import").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activities").value(0))
				.andExpect(jsonPath("$.matched").value(0));
	}

	@Test
	void importWithoutConnectionIsRefused() throws Exception {
		mockMvc.perform(post("/me/intervals/import").header("Authorization", registerWithProfile(mockMvc)))
				.andExpect(status().isConflict());
	}

	@Test
	void syncWithoutConnectionIsRefused() throws Exception {
		mockMvc.perform(post("/me/intervals/sync").header("Authorization", registerWithProfile(mockMvc)))
				.andExpect(status().isConflict());
	}

	@Test
	void disconnectRemovesTheConnection() throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerNewUser(mockMvc);
		connect(token, "schluessel");

		mockMvc.perform(delete("/me/intervals").header("Authorization", token)).andExpect(status().isNoContent());

		mockMvc.perform(get("/me/intervals").header("Authorization", token))
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void connectionsArePerUser() throws Exception {
		when(client.verify(any())).thenReturn(true);
		connect(registerNewUser(mockMvc), "schluessel");

		mockMvc.perform(get("/me/intervals").header("Authorization", registerNewUser(mockMvc)))
				.andExpect(jsonPath("$.connected").value(false));
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(get("/me/intervals")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/me/intervals/sync")).andExpect(status().isUnauthorized());
	}

}
