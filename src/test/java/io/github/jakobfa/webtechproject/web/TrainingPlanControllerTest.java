package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;

@SpringBootTest
@AutoConfigureMockMvc
class TrainingPlanControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AthleteService athleteService;

	@Autowired
	private ExtraRideRepository extraRides;

	/** Nutzer-ID steht im JWT ("sub") */
	private static Long userIdOf(String bearer) {
		String payload = new String(Base64.getUrlDecoder().decode(bearer.substring(7).split("\\.")[1]));
		return Long.valueOf(JsonPath.read(payload, "$.sub").toString());
	}

	@Test
	void showsExtraRidesFromTheMondayOfTheFirstWeek() throws Exception {
		String token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		LocalDate monday = LocalDate.now(ZoneId.of("Europe/Berlin"))
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		ExtraRide commute = new ExtraRide(athleteService.findByOwner(userIdOf(token)).orElseThrow(), "strava-1");
		commute.setDate(monday);
		commute.setDurationMinutes(25);
		extraRides.save(commute);

		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(jsonPath("$.extraRides[0].date").value(monday.toString()));
	}

	@Test
	void changedAvailabilityAdjustsThePlanButKeepsWhatIsDone() throws Exception {
		String token = registerWithProfile(mockMvc);
		String before = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		Number planId = JsonPath.read(before, "$.id");
		Number doneId = JsonPath.read(before, "$.workouts[0].id");
		mockMvc.perform(put("/planned-workouts/" + doneId + "/progress").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"completed\": true, \"rating\": 6 }"));

		// ab jetzt nur noch samstags Zeit
		String onlySaturday = TestUsers.athleteJson(240, "Testevent", LocalDate.now().plusWeeks(10))
				.replaceAll("\"availability\":\\s*\\{[^}]*\\}", "\"availability\": { \"monday\": 0, "
						+ "\"tuesday\": 0, \"wednesday\": 0, \"thursday\": 0, \"friday\": 0, \"saturday\": 3, "
						+ "\"sunday\": 0 }");
		mockMvc.perform(put("/me/athlete").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(onlySaturday)).andExpect(status().isOk());
		String after = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();

		assertThat((Number) JsonPath.read(after, "$.id")).as("derselbe Plan").isEqualTo(planId);
		List<Map<String, Object>> workouts = JsonPath.read(after, "$.workouts");
		assertThat(workouts).filteredOn(w -> doneId.equals(w.get("id"))).singleElement()
				.satisfies(w -> {
					assertThat(w.get("completed")).isEqualTo(true);
					assertThat(w.get("rating")).isEqualTo(6);
				});
		assertThat(workouts).filteredOn(w -> !doneId.equals(w.get("id")))
				.isNotEmpty()
				.allSatisfy(w -> assertThat(LocalDate.parse((String) w.get("date")).getDayOfWeek())
						.isEqualTo(DayOfWeek.SATURDAY));
	}

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
