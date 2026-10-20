package io.github.jakobfa.webtechproject.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class PlannedWorkoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private long plannedId;

	@BeforeEach
	void createPlan() throws Exception {
		String athlete = """
				{
				  "ftp": 240, "weightKg": 70, "level": "HOBBY",
				  "event": { "name": "Test", "date": "%s", "distanceKm": 100, "elevationMeters": 500 },
				  "availability": { "tuesday": 1.5, "saturday": 3 }
				}
				""".formatted(LocalDate.now().plusWeeks(8));
		String created = mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(athlete))
				.andReturn().getResponse().getContentAsString();
		long athleteId = ((Number) JsonPath.read(created, "$.id")).longValue();

		String plan = mockMvc.perform(post("/athletes/" + athleteId + "/plan")).andReturn().getResponse()
				.getContentAsString();
		plannedId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
	}

	private String progress(boolean completed, Integer rating) {
		return "{ \"completed\": %s, \"rating\": %s }".formatted(completed, rating);
	}

	@Test
	void loadsPlannedWorkoutWithWorkoutDetails() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(plannedId))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.workout.steps").isArray());
	}

	@Test
	void marksWorkoutAsCompletedWithRating() throws Exception {
		mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.contentType(MediaType.APPLICATION_JSON).content(progress(true, 7)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));

		mockMvc.perform(get("/planned-workouts/" + plannedId))
				.andExpect(jsonPath("$.completed").value(true))
				.andExpect(jsonPath("$.rating").value(7));
	}

	@Test
	void uncheckingRemovesTheRating() throws Exception {
		mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.contentType(MediaType.APPLICATION_JSON).content(progress(true, 7)));

		mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.contentType(MediaType.APPLICATION_JSON).content(progress(false, 7)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.rating").value(nullValue()));
	}

	@Test
	void rejectsRatingOutsideOneToTen() throws Exception {
		mockMvc.perform(put("/planned-workouts/" + plannedId + "/progress")
				.contentType(MediaType.APPLICATION_JSON).content(progress(true, 11)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void downloadsPlannedWorkoutAsFitFile() throws Exception {
		mockMvc.perform(get("/planned-workouts/" + plannedId + "/fit"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.endsWith(".fit\"")));
	}

	@Test
	void unknownPlannedWorkoutIsNotFound() throws Exception {
		mockMvc.perform(get("/planned-workouts/99999")).andExpect(status().isNotFound());
		mockMvc.perform(put("/planned-workouts/99999/progress")
				.contentType(MediaType.APPLICATION_JSON).content(progress(true, 5)))
				.andExpect(status().isNotFound());
	}

}
