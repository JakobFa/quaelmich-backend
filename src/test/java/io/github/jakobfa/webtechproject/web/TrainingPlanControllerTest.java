package io.github.jakobfa.webtechproject.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TrainingPlanControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private long createAthlete() throws Exception {
		String json = """
				{
				  "ftp": 240, "weightKg": 70, "level": "HOBBY",
				  "event": { "name": "Rund um Berlin", "date": "%s", "distanceKm": 150, "elevationMeters": 800 },
				  "availability": { "tuesday": 1.5, "thursday": 1.5, "saturday": 3, "sunday": 2 }
				}
				""".formatted(LocalDate.now().plusWeeks(10));
		String response = mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	@Test
	void generatesAndLoadsPlanWithDatedWorkouts() throws Exception {
		long athleteId = createAthlete();

		mockMvc.perform(post("/athletes/" + athleteId + "/plan"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventName").value("Rund um Berlin"))
				.andExpect(jsonPath("$.workouts.length()").value(greaterThan(10)))
				.andExpect(jsonPath("$.workouts[0].date").isString())
				.andExpect(jsonPath("$.workouts[0].phase").isString())
				.andExpect(jsonPath("$.workouts[0].completed").value(false))
				.andExpect(jsonPath("$.workouts[0].workout.title").isString())
				.andExpect(jsonPath("$.workouts[0].workout.steps").isArray());

		mockMvc.perform(get("/athletes/" + athleteId + "/plan"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workouts.length()").value(greaterThan(10)));
	}

	@Test
	void regeneratingReplacesTheOldPlan() throws Exception {
		long athleteId = createAthlete();

		String first = mockMvc.perform(post("/athletes/" + athleteId + "/plan")).andReturn().getResponse()
				.getContentAsString();
		String second = mockMvc.perform(post("/athletes/" + athleteId + "/plan")).andReturn().getResponse()
				.getContentAsString();

		int firstCount = JsonPath.read(first, "$.workouts.length()");
		mockMvc.perform(get("/athletes/" + athleteId + "/plan"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(((Number) JsonPath.read(second, "$.id")).longValue()))
				.andExpect(jsonPath("$.workouts.length()").value(firstCount));
	}

	@Test
	void planOfAthleteWithoutPlanIsNotFound() throws Exception {
		long athleteId = createAthlete();

		mockMvc.perform(get("/athletes/" + athleteId + "/plan")).andExpect(status().isNotFound());
	}

	@Test
	void planForUnknownAthleteIsNotFound() throws Exception {
		mockMvc.perform(post("/athletes/99999/plan")).andExpect(status().isNotFound());
	}

}
