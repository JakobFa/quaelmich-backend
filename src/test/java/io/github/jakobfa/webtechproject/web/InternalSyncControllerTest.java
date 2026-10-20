package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsCredentials;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;

/**
 * Nächtlicher Sync: nur mit richtigem Geheimnis (in Tests "test-sync-token"), nur für Nutzer mit Auto-Sync.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalSyncControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IntervalsIcuClient client;

	private String connectedUserWithPlan(String apiKey, boolean autoSync) throws Exception {
		when(client.verify(any())).thenReturn(true);
		String token = registerWithProfile(mockMvc);
		mockMvc.perform(put("/me/intervals").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"apiKey\": \"%s\", \"athleteId\": \"i7\" }".formatted(apiKey)));
		mockMvc.perform(put("/me/intervals/settings").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"autoSync\": %s }".formatted(autoSync)));
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		return token;
	}

	@Test
	void refusesWithoutOrWithWrongToken() throws Exception {
		mockMvc.perform(post("/internal/intervals/sync-all")).andExpect(status().isForbidden());
		mockMvc.perform(post("/internal/intervals/sync-all").header("X-Sync-Token", "falsch"))
				.andExpect(status().isForbidden());
	}

	@Test
	void syncsAllUsersWithAutoSyncButNotTheOthers() throws Exception {
		connectedUserWithPlan("nacht-an", true);
		connectedUserWithPlan("nacht-aus", false);

		mockMvc.perform(post("/internal/intervals/sync-all").header("X-Sync-Token", "test-sync-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.failed").value(0));

		verify(client, org.mockito.Mockito.atLeastOnce())
				.upsertEvents(eq(new IntervalsCredentials("nacht-an", "i7")), argThat(events -> !events.isEmpty()));
		verify(client, never()).upsertEvents(eq(new IntervalsCredentials("nacht-aus", "i7")), any());
	}

}
