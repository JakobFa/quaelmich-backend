package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class TrainingPlanControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generatesAndLoadsOwnPlanWithDatedWorkouts() throws Exception {
		String token = registerWithProfile(mockMvc);

		mockMvc.perform(post("/me/plan").header("Authorization", token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventName").value("Testevent"))
				.andExpect(jsonPath("$.workouts.length()").value(greaterThan(10)))
				.andExpect(jsonPath("$.workouts[0].date").isString())
				.andExpect(jsonPath("$.workouts[0].phase").isString())
				.andExpect(jsonPath("$.workouts[0].completed").value(false))
				.andExpect(jsonPath("$.workouts[0].workout.title").isString())
				.andExpect(jsonPath("$.workouts[0].workout.steps").isArray());

		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workouts.length()").value(greaterThan(10)));
	}

	@Test
	void regeneratingReplacesTheOldPlan() throws Exception {
		String token = registerWithProfile(mockMvc);

		String first = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		String second = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();

		int firstCount = JsonPath.read(first, "$.workouts.length()");
		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(((Number) JsonPath.read(second, "$.id")).longValue()))
				.andExpect(jsonPath("$.workouts.length()").value(firstCount));
	}

	@Test
	void noPlanBeforeItWasCreated() throws Exception {
		String token = registerWithProfile(mockMvc);

		mockMvc.perform(get("/me/plan").header("Authorization", token)).andExpect(status().isNotFound());
	}

	@Test
	void noPlanWithoutProfile() throws Exception {
		String token = registerNewUser(mockMvc);

		mockMvc.perform(post("/me/plan").header("Authorization", token)).andExpect(status().isNotFound());
	}

}
