package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;


/** Die Bibliothek ist öffentlich – deshalb ohne Login. */
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void listsAllWorkouts() throws Exception {
		mockMvc.perform(get("/workouts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(28))
				.andExpect(jsonPath("$[0].title").value("Lockeres Ausrollen 45 min"));
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


	@Test
	void downloadsWorkoutAsFitFile() throws Exception {
		mockMvc.perform(get("/workouts/5/fit"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"sweet-spot-3x12.fit\""));
	}

	@Test
	void downloadOfUnknownWorkoutIsNotFound() throws Exception {
		mockMvc.perform(get("/workouts/999/fit")).andExpect(status().isNotFound());
	}

}
