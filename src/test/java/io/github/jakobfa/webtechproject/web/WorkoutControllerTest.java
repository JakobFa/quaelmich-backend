package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

@WebMvcTest(WorkoutController.class)
@Import(WorkoutLibrary.class)
class WorkoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsAllWorkouts() throws Exception {
		mockMvc.perform(get("/workouts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(11))
				.andExpect(jsonPath("$[0].title").value("Lockeres Ausrollen"));
	}

	@Test
	void returnsSingleWorkoutWithStepsAndComputedValues() throws Exception {
		mockMvc.perform(get("/workouts/5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("SWEET_SPOT"))
				.andExpect(jsonPath("$.durationInMinutes").value(71))
				.andExpect(jsonPath("$.tss").isNumber())
				.andExpect(jsonPath("$.steps[1].powerStartPercent").value(90));
	}

	@Test
	void unknownWorkoutIsNotFound() throws Exception {
		mockMvc.perform(get("/workouts/999")).andExpect(status().isNotFound());
	}

}
