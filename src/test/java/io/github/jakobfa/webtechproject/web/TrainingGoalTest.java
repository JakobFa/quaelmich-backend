package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/** Trainieren ohne Event: Ziel wählen, Plan ohne Enddatum, der beim Öffnen mitwächst */
@SpringBootTest
@AutoConfigureMockMvc
class TrainingGoalTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TrainingPlanRepository plans;

	@Autowired
	private TransactionTemplate transactions;

	private String token;

	@BeforeEach
	void register() throws Exception {
		token = registerNewUser(mockMvc);
	}

	private ResultActions saveProfile(String goal, boolean withEvent) throws Exception {
		String event = withEvent
				? """
						, "event": { "name": "Rund um Sachsen", "date": "%s", "distanceKm": 160, "elevationMeters": 1500 }
						""".formatted(LocalDate.now().plusWeeks(12))
				: "";
		return mockMvc.perform(put("/me/athlete").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "ftp": 250, "weightKg": 72, "level": "AMBITIOUS", "trainingGoal": "%s",
						  "availability": { "monday": 0, "tuesday": 1.5, "wednesday": 0, "thursday": 1.5,
						                    "friday": 0, "saturday": 3, "sunday": 2 } %s }
						""".formatted(goal, event)));
	}

	@Test
	void trainsForMoreFtpWithoutAnyEvent() throws Exception {
		saveProfile("FTP", false).andExpect(status().isOk()).andExpect(jsonPath("$.trainingGoal").value("FTP"))
				.andExpect(jsonPath("$.event").doesNotExist());

		mockMvc.perform(post("/me/plan").header("Authorization", token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.trainingGoal").value("FTP"))
				.andExpect(jsonPath("$.eventName").doesNotExist())
				.andExpect(jsonPath("$.eventProfile.focus").value("TIME_TRIAL"))
				// FTP-Test zu Beginn bzw. nach dem ersten Zyklus (startet der Plan sonntags, ist Woche 1 nur ein Tag)
				.andExpect(jsonPath("$.workouts[*].workout.type", hasItem("FTP_TEST")));

		mockMvc.perform(get("/me/progress").header("Authorization", token))
				.andExpect(jsonPath("$.plan.cycle").value(1))
				.andExpect(jsonPath("$.plan.totalWeeks").value(8));
	}

	@Test
	void anEventGoalNeedsAnEvent() throws Exception {
		saveProfile("EVENT", false).andExpect(status().isBadRequest());
		saveProfile("EVENT", true).andExpect(status().isOk()).andExpect(jsonPath("$.event.name").value("Rund um Sachsen"));
	}

	@Test
	void switchingBetweenEventAndGoal() throws Exception {
		saveProfile("EVENT", true);
		mockMvc.perform(post("/me/plan").header("Authorization", token));

		saveProfile("CLIMBING", true).andExpect(status().isOk()).andExpect(jsonPath("$.event").doesNotExist());
		mockMvc.perform(post("/me/plan").header("Authorization", token))
				.andExpect(jsonPath("$.trainingGoal").value("CLIMBING"))
				.andExpect(jsonPath("$.eventDate").doesNotExist());

		saveProfile("EVENT", true).andExpect(status().isOk()).andExpect(jsonPath("$.event.name").value("Rund um Sachsen"));
	}

	@Test
	void thePlanGrowsWhenItIsOpened() throws Exception {
		saveProfile("ENDURANCE", false);
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		long planId = ((Number) JsonPath.read(plan, "$.id")).longValue();
		// so tun, als wäre der Plan schon weit fortgeschritten: nur noch 4 Wochen voraus geplant
		transactions.executeWithoutResult(status -> {
			var stored = plans.findById(planId).orElseThrow();
			stored.getWorkouts().removeIf(w -> w.getDate().isAfter(LocalDate.now().plusWeeks(4)));
		});

		String grown = mockMvc.perform(get("/me/plan").header("Authorization", token)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		List<String> dates = JsonPath.read(grown, "$.workouts[*].date");
		assertThat(dates.stream().map(LocalDate::parse).max(LocalDate::compareTo).orElseThrow())
				.isAfter(LocalDate.now().plusWeeks(10));
		assertThat(dates).doesNotHaveDuplicates();
	}


}
