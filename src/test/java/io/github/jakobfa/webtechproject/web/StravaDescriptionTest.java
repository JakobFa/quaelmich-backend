package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.integration.strava.StravaClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.ActivityDetails;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaAthlete;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.TokenResponse;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/** Bestätigte Trainings landen in der Strava-Beschreibung – nur auf Wunsch und nie bei Pendelfahrten */
@SpringBootTest
@AutoConfigureMockMvc
class StravaDescriptionTest {

	private static final long ACTIVITY = 4242L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlannedWorkoutRepository repository;

	@MockitoBean
	private StravaClient client;

	private String token;
	private long plannedId;

	@BeforeEach
	void planWithLinkedStravaRide() throws Exception {
		token = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		plannedId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
		PlannedWorkout planned = repository.findById(plannedId).orElseThrow();
		planned.setActivityId("strava-" + ACTIVITY);
		planned.setCompleted(true);
		planned.setActualDurationMinutes(62);
		planned.setActualTss(48);
		repository.save(planned);

		when(client.exchangeCode("abc")).thenReturn(new TokenResponse("acc", "ref", 4_000_000_000L,
				new StravaAthlete(99L, "Jakob")));
	}

	private ResultActions connect(String scope) throws Exception {
		return mockMvc.perform(post("/me/strava").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"%s\" }".formatted(scope)));
	}

	private ResultActions confirm(boolean matchesPlan) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + plannedId + "/activity-confirmation")
				.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"matchesPlan\": %s }".formatted(matchesPlan)));
	}

	private ResultActions setDescriptions(boolean enabled) throws Exception {
		return mockMvc.perform(put("/me/strava/descriptions").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"enabled\": %s }".formatted(enabled)));
	}

	private void stravaHas(String description, boolean commute) {
		when(client.activity("acc", ACTIVITY)).thenReturn(new ActivityDetails(ACTIVITY, description, commute));
	}

	@Test
	void writesTheConfirmedTrainingBelowTheOwnText() throws Exception {
		connect("read,activity:read_all,activity:write")
				.andExpect(jsonPath("$.canWriteActivities").value(true))
				.andExpect(jsonPath("$.writeDescriptions").value(true));
		stravaHas("Schöne Runde", false);

		confirm(true).andExpect(status().isOk());

		verify(client, timeout(5000)).updateDescription(eq("acc"), eq(ACTIVITY), argThat(text -> text
				.startsWith("Schöne Runde\n\n── quaelmich ──") && text.contains("gefahren 62 min / 48 TSS")
				&& text.endsWith("Plan von quaelmich.com")));
	}

	@Test
	void neverWritesIntoACommute() throws Exception {
		connect("read,activity:read_all,activity:write");
		stravaHas("Zur Arbeit", true);

		confirm(true).andExpect(status().isOk());

		verify(client, timeout(5000)).activity("acc", ACTIVITY);
		verify(client, after(500).never()).updateDescription(anyString(), anyLong(), anyString());
	}

	@Test
	void rejectingTheRideRemovesTheBlock() throws Exception {
		connect("read,activity:read_all,activity:write");
		stravaHas("Schöne Runde\n\n── quaelmich ──\nGrundlage\nPlan von quaelmich.com", false);

		confirm(false).andExpect(status().isOk());

		verify(client, timeout(5000)).updateDescription("acc", ACTIVITY, "Schöne Runde");
	}

	@Test
	void switchedOffOrWithoutPermissionNothingIsWritten() throws Exception {
		connect("read,activity:read_all")
				.andExpect(jsonPath("$.canWriteActivities").value(false))
				.andExpect(jsonPath("$.writeDescriptions").value(false));
		setDescriptions(true).andExpect(status().isConflict());
		stravaHas("", false);

		confirm(true).andExpect(status().isOk());

		verify(client, after(500).never()).activity(anyString(), anyLong());
	}

	@Test
	void canBeSwitchedOff() throws Exception {
		connect("read,activity:read_all,activity:write");
		setDescriptions(false).andExpect(status().isOk()).andExpect(jsonPath("$.writeDescriptions").value(false));

		confirm(true).andExpect(status().isOk());

		verify(client, after(500).never()).activity(anyString(), anyLong());
	}

}
