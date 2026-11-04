package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Krank melden: offen (Plan steht still), rückwirkend und wieder gesund */
@SpringBootTest
@AutoConfigureMockMvc
class SicknessTest {

	private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlannedWorkoutRepository repository;

	private String token;
	private Long athleteId;

	@BeforeEach
	void createPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		long firstId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
		athleteId = repository.findById(firstId).orElseThrow().getPlan().getAthlete().getId();
	}

	private ResultActions sick(String body) throws Exception {
		return mockMvc.perform(put("/me/plan/sickness").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions healthy(String query) throws Exception {
		return mockMvc.perform(delete("/me/plan/sickness" + query).header("Authorization", token));
	}

	private List<PlannedWorkout> workouts(LocalDate from, LocalDate until) {
		return repository.findByPlanAthleteIdAndDateBetween(athleteId, from, until);
	}

	@Test
	void openSicknessHoldsTheWholePlanUntilYouAreHealthy() throws Exception {
		sick("{ \"from\": \"%s\" }".formatted(TODAY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sickSince").value(TODAY.toString()));

		assertThat(workouts(TODAY, TODAY.plusWeeks(8)))
				.isNotEmpty().allMatch(p -> p.getSkipReason() == SkipReason.SICK);

		// auch eine neue Planung (z. B. andere Verfügbarkeit) ändert nichts am Stillstand
		mockMvc.perform(post("/me/plan").header("Authorization", token)).andExpect(status().isCreated());
		assertThat(workouts(TODAY, TODAY.plusWeeks(8))).allMatch(p -> p.getSkipReason() == SkipReason.SICK);

		healthy("").andExpect(status().isOk()).andExpect(jsonPath("$.sickSince").value(nullValue()));
		assertThat(workouts(TODAY, TODAY.plusWeeks(8))).isNotEmpty()
				.noneMatch(p -> p.getSkipReason() == SkipReason.SICK);
	}

	@Test
	void sicknessCanBeEnteredAfterTheFactWhenTheAppWasNotOpened() throws Exception {
		LocalDate since = TODAY.minusDays(5);
		sick("{ \"from\": \"%s\" }".formatted(since)).andExpect(status().isOk())
				.andExpect(jsonPath("$.sickSince").value(since.toString()));

		// die vergangenen Tage seit dem Beginn sind als krank markiert, die Zukunft steht still
		List<PlannedWorkout> sinceThen = workouts(since, TODAY.plusWeeks(2));
		assertThat(sinceThen).isNotEmpty().allMatch(p -> p.getSkipReason() == SkipReason.SICK);
		assertThat(workouts(TODAY.minusDays(30), since.minusDays(1))).noneMatch(p -> p.getSkipReason() == SkipReason.SICK);
	}

	@Test
	void aClosedPeriodInThePastIsMarkedAndThePlanContinues() throws Exception {
		LocalDate from = TODAY.minusDays(6);
		LocalDate until = TODAY.minusDays(2);
		sick("{ \"from\": \"%s\", \"until\": \"%s\" }".formatted(from, until))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sickSince").value(nullValue()));

		assertThat(workouts(from, until)).allMatch(p -> p.getSkipReason() == SkipReason.SICK);
		assertThat(workouts(TODAY.plusDays(1), TODAY.plusWeeks(2))).noneMatch(p -> p.getSkipReason() == SkipReason.SICK);
	}

	@Test
	void healthySinceAnEarlierDayFreesThoseDays() throws Exception {
		sick("{ \"from\": \"%s\" }".formatted(TODAY.minusDays(6))).andExpect(status().isOk());
		LocalDate healthySince = TODAY.minusDays(2);

		healthy("?since=" + healthySince).andExpect(status().isOk());

		assertThat(workouts(TODAY.minusDays(6), healthySince.minusDays(1)))
				.allMatch(p -> p.getSkipReason() == SkipReason.SICK);
		assertThat(workouts(healthySince, TODAY)).noneMatch(p -> p.getSkipReason() == SkipReason.SICK);
	}

	@Test
	void longIllnessGivesAnEasyReentryWeekWithoutHardSessions() throws Exception {
		sick("{ \"from\": \"%s\" }".formatted(TODAY.minusDays(10))).andExpect(status().isOk());
		healthy("").andExpect(status().isOk());

		String plan = mockMvc.perform(get("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		List<String> phases = JsonPath.read(plan, "$.workouts[?(@.date >= '" + TODAY + "' && @.date <= '"
				+ TODAY.plusDays(3) + "')].phase");
		assertThat(phases).isNotEmpty().allMatch("RECOVERY"::equals);
	}

	@Test
	void sicknessFromTheFutureOrTooFarBackIsRejected() throws Exception {
		sick("{ \"from\": \"%s\" }".formatted(TODAY.plusDays(1))).andExpect(status().isConflict());
		sick("{ \"from\": \"%s\" }".formatted(TODAY.minusDays(100))).andExpect(status().isConflict());
		sick("{ \"from\": \"%s\", \"until\": \"%s\" }".formatted(TODAY, TODAY.minusDays(1)))
				.andExpect(status().isConflict());
	}

	@Test
	void healthyWithoutSicknessIsRejectedAndAPeriodWhileSickToo() throws Exception {
		healthy("").andExpect(status().isConflict());
		sick("{ \"from\": \"%s\" }".formatted(TODAY)).andExpect(status().isOk());
		sick("{ \"from\": \"%s\", \"until\": \"%s\" }".formatted(TODAY.minusDays(3), TODAY.minusDays(2)))
				.andExpect(status().isConflict());
	}

	@Test
	void ridesAlreadyLinkedAreNotTouched() throws Exception {
		PlannedWorkout planned = workouts(TODAY, TODAY.plusWeeks(1)).getFirst();
		planned.setActivityId("i42");
		repository.save(planned);

		sick("{ \"from\": \"%s\" }".formatted(TODAY)).andExpect(status().isOk());

		assertThat(repository.findById(planned.getId()).orElseThrow().getSkipReason()).isNull();
	}
}
