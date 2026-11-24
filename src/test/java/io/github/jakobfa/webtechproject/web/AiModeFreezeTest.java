package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;

/** Bei eingeschaltetem KI-Modus ändert quaelmich den Plan nicht mehr von selbst; eigene Aktionen gelten weiter */
@SpringBootTest(properties = "app.mcp.enabled=true")
@AutoConfigureMockMvc
class AiModeFreezeTest {

	private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private TrainingPlanRepository plans;
	@Autowired
	private TrainingPlanService planService;
	@Autowired
	private TransactionTemplate tx;

	private String auth;
	private Long planId;
	private Long athleteId;
	private long firstWorkoutId;

	@BeforeEach
	void createPlan() throws Exception {
		auth = registerWithProfile(mockMvc);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", auth)).andReturn().getResponse()
				.getContentAsString();
		firstWorkoutId = ((Number) JsonPath.read(plan, "$.workouts[0].id")).longValue();
		planId = ((Number) JsonPath.read(plan, "$.id")).longValue();
		tx.executeWithoutResult(status -> athleteId = plans.findById(planId).orElseThrow().getAthlete().getId());
	}

	private void aiMode(boolean on) throws Exception {
		mockMvc.perform(put("/me/ai-mode").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"enabled\": %s }".formatted(on))).andExpect(status().isOk());
	}

	private Integer fingerprint() {
		return tx.execute(status -> plans.findById(planId).orElseThrow().getActualsFingerprint());
	}

	private List<Long> workoutIds() {
		return tx.execute(status -> plans.findById(planId).orElseThrow().getWorkouts().stream()
				.map(PlannedWorkout::getId).sorted().toList());
	}

	/** Verschiebt den Plan um 21 Tage nach hinten: Die laufende Woche ist die Erholungswoche, davor wurde nichts gefahren */
	private void shiftPlanBack() {
		tx.executeWithoutResult(status -> {
			TrainingPlan plan = plans.findById(planId).orElseThrow();
			plan.setStartDate(plan.getStartDate().minusDays(21));
			plan.getWorkouts().forEach(planned -> planned.setDate(planned.getDate().minusDays(21)));
			plans.save(plan);
		});
	}

	@Test
	void theAdaptationToRiddenValuesDoesNotRunWhileTheAiModeIsOn() throws Exception {
		aiMode(true);

		planService.adaptToActuals(athleteId);

		assertThat(fingerprint()).isNull();
	}

	@Test
	void checkingOffAWorkoutDoesNotAdaptThePlanWhileTheAiModeIsOn() throws Exception {
		aiMode(true);

		mockMvc.perform(put("/planned-workouts/" + firstWorkoutId + "/progress").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"completed\": true, \"rating\": 4 }"))
				.andExpect(status().isOk());

		assertThat(fingerprint()).isNull();
	}

	@Test
	void openingThePlanDoesNotRecalculateTheWeekWhileTheAiModeIsOn() throws Exception {
		shiftPlanBack();
		aiMode(true);
		List<Long> before = workoutIds();

		planService.currentPlan(athleteId);

		assertThat(workoutIds()).isEqualTo(before);
		LocalDate adapted = tx.execute(status -> plans.findById(planId).orElseThrow().getLastAdaptedMonday());
		assertThat(adapted).isNull();
	}

	@Test
	void theAutomaticAdaptationWorksAgainWhenTheAiModeIsSwitchedOff() throws Exception {
		aiMode(true);
		planService.adaptToActuals(athleteId);
		assertThat(fingerprint()).isNull();

		aiMode(false);
		planService.adaptToActuals(athleteId);

		assertThat(fingerprint()).isNotNull();
	}

	@Test
	void whatTheUserDoesHimselfStillWorksWhileTheAiModeIsOn() throws Exception {
		aiMode(true);

		mockMvc.perform(put("/planned-workouts/" + firstWorkoutId + "/skip").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"reason\": \"TIRED\" }"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/me/plan/sickness").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"from\": \"%s\" }".formatted(TODAY))).andExpect(status().isOk());
	}
}
