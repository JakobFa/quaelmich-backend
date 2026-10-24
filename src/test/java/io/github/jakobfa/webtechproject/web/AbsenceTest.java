package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/** "Nicht gemacht" mit Grund und Pausen */
@SpringBootTest
@AutoConfigureMockMvc
class AbsenceTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlannedWorkoutRepository repository;

	private String token;
	private long firstId;
	private long secondId;

	@BeforeEach
	void createPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		firstId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
		secondId = ((Number) JsonPath.read(plan, "$.workouts[1].id")).longValue();
	}

	private ResultActions skip(long id, String reason) throws Exception {
		return mockMvc.perform(put("/planned-workouts/" + id + "/skip").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"reason\": \"%s\" }".formatted(reason)));
	}

	private ResultActions pause(LocalDate from, LocalDate until) throws Exception {
		return mockMvc.perform(put("/me/plan/pause").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"from\": \"%s\", \"until\": \"%s\" }".formatted(from, until)));
	}

	@Test
	void notDoneIsFeedbackToo() throws Exception {
		skip(firstId, "NO_TIME")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skipReason").value("NO_TIME"))
				.andExpect(jsonPath("$.completed").value(false))
				.andExpect(jsonPath("$.options.harder").value(false));

		mockMvc.perform(delete("/planned-workouts/" + firstId + "/skip").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skipReason").value(nullValue()));
	}

	@Test
	void checkingOffClearsTheSkip() throws Exception {
		skip(firstId, "TIRED");

		mockMvc.perform(put("/planned-workouts/" + firstId + "/progress").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"completed\": true, \"rating\": 4 }"))
				.andExpect(jsonPath("$.skipReason").value(nullValue()));
	}

	@Test
	void withALinkedRideTheWorkoutCannotBeSkipped() throws Exception {
		PlannedWorkout planned = repository.findById(firstId).orElseThrow();
		planned.setActivityId("i123");
		repository.save(planned);

		skip(firstId, "SICK").andExpect(status().isConflict());
	}

	@Test
	void pauseOnlyThroughThePlan() throws Exception {
		skip(firstId, "PAUSE").andExpect(status().isConflict());
		skip(firstId, "LAZY").andExpect(status().isBadRequest());
		mockMvc.perform(put("/planned-workouts/" + firstId + "/skip").header("Authorization", registerWithProfile(mockMvc))
				.contentType(MediaType.APPLICATION_JSON).content("{ \"reason\": \"SICK\" }"))
				.andExpect(status().isNotFound());
	}

	@Test
	void afterBeingSickTheNextHardSessionIsGentler() throws Exception {
		PlannedWorkout sickDay = repository.findById(firstId).orElseThrow();
		PlannedWorkout next = repository.findById(secondId).orElseThrow();
		next.setDate(sickDay.getDate().plusDays(1));
		next.setWorkoutId(8L); // VO2max 5 × 4
		next.setDurationMinutes(null);
		next.setAdjustmentNote(null);
		repository.save(next);

		skip(firstId, "SICK").andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/planned-workouts/" + secondId).header("Authorization", token))
				.andExpect(jsonPath("$.workout.title").value("VO2max 5 × 3"))
				.andExpect(jsonPath("$.adjustment.note").value(containsString("krank")));
	}

	@Test
	void pausingSkipsTheDaysAndResumingBringsThemBack() throws Exception {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
		LocalDate until = today.plusDays(6);

		pause(today, until).andExpect(status().isOk());
		Long athleteId = repository.findById(firstId).orElseThrow().getPlan().getAthlete().getId();
		List<PlannedWorkout> inPause = repository.findByPlanAthleteIdAndDateBetween(athleteId, today, until);
		assertThat(inPause).isNotEmpty().allMatch(p -> p.getSkipReason() == SkipReason.PAUSE);

		mockMvc.perform(delete("/me/plan/pause").header("Authorization", token)).andExpect(status().isOk());
		assertThat(repository.findAllById(inPause.stream().map(PlannedWorkout::getId).toList()))
				.allMatch(p -> p.getSkipReason() == null);
	}

	@Test
	void pauseMustBeInTheFutureAndNotTooLong() throws Exception {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));

		pause(today.minusDays(1), today).andExpect(status().isConflict());
		pause(today, today.plusDays(60)).andExpect(status().isConflict());
		pause(today.plusDays(3), today.plusDays(1)).andExpect(status().isConflict());
	}

}
