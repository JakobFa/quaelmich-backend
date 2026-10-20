package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class PlannedWorkoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private String token;
	private long plannedId;

	@BeforeEach
	void createPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		plannedId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
	}

	private ResultActions saveProgress(String authorization, boolean completed, Integer rating) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"completed\": %s, \"rating\": %s }".formatted(completed, rating)));
	}

	@Test
	void loadsPlannedWorkoutWithWorkoutDetails() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(plannedId))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.workout.steps").isArray());
	}

	@Test
	void marksWorkoutAsCompletedWithRating() throws Exception {
		saveProgress(token, true, 7)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));

		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));
	}

	@Test
	void uncheckingRemovesTheRating() throws Exception {
		saveProgress(token, true, 7);

		saveProgress(token, false, 7)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.rating").value(nullValue()));
	}

	@Test
	void rejectsRatingOutsideOneToTen() throws Exception {
		saveProgress(token, true, 11).andExpect(status().isBadRequest());
	}

	@Test
	void downloadsPlannedWorkoutAsFitFile() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/fit").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", endsWith(".fit\"")));
	}

	@Test
	void otherUsersCannotSeeOrChangeTheWorkout() throws Exception {
		String otherUser = registerWithProfile(mockMvc);

		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", otherUser))
				.andExpect(status().isNotFound());
		saveProgress(otherUser, true, 3).andExpect(status().isNotFound());
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/fit").header("Authorization", otherUser))
				.andExpect(status().isNotFound());

		// unverändert für den Besitzer
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.completed").value(false));
	}

	@Test
	void unknownPlannedWorkoutIsNotFound() throws Exception {
		mockMvc.perform(get("/planned-workouts/99999").header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	@Test
	void needsLogin() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId)).andExpect(status().isUnauthorized());
	}

}
