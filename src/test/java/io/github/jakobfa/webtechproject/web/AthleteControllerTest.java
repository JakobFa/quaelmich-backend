package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.athleteJson;
import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AthleteControllerTest {

	private static final LocalDate EVENT = LocalDate.now().plusMonths(6);

	@Autowired
	private MockMvc mockMvc;

	private String token;

	@BeforeEach
	void login() throws Exception {
		token = registerNewUser(mockMvc);
	}

	private ResultActions save(String json) throws Exception {
		return mockMvc.perform(put("/me/athlete").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void heartRateModeWorksWithoutFtp() throws Exception {
		String json = athleteJson(250, "Pendeln", EVENT)
				.replace("\"ftp\": 250,", "\"trainingMode\": \"HEART_RATE\", \"maxHeartRate\": 189,");

		save(json).andExpect(status().isOk())
				.andExpect(jsonPath("$.ftp").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.trainingMode").value("HEART_RATE"));

		save(json.replace("\"maxHeartRate\": 189,", "")).andExpect(status().isBadRequest());
	}

	@Test
	void olderClientsWithoutTrainingModeTrainByPower() throws Exception {
		save(athleteJson(250, "Ötztaler Radmarathon", EVENT))
				.andExpect(jsonPath("$.trainingMode").value("POWER"));
	}

	@Test
	void newUserHasNoProfileYet() throws Exception {
		mockMvc.perform(get("/me/athlete").header("Authorization", token)).andExpect(status().isNotFound());
	}

	@Test
	void createsAndLoadsOwnProfile() throws Exception {
		save(athleteJson(250, "Ötztaler Radmarathon", EVENT)).andExpect(status().isOk());

		mockMvc.perform(get("/me/athlete").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ftp").value(250))
				.andExpect(jsonPath("$.level").value("AMBITIOUS"))
				.andExpect(jsonPath("$.event.name").value("Ötztaler Radmarathon"))
				.andExpect(jsonPath("$.availability.saturday").value(3.0))
				.andExpect(jsonPath("$.owner").doesNotExist());
	}

	@Test
	void savingAgainUpdatesTheSameProfile() throws Exception {
		String first = save(athleteJson(250, "Ötztaler Radmarathon", EVENT)).andReturn().getResponse()
				.getContentAsString();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(first, "$.id")).longValue();

		save(athleteJson(265, "Maratona dles Dolomites", EVENT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.ftp").value(265))
				.andExpect(jsonPath("$.event.name").value("Maratona dles Dolomites"));
	}

	@Test
	void eventGoalIsSavedAndCanBeChanged() throws Exception {
		save(athleteJson(250, "Rund um Sachsen", EVENT));
		save(athleteJson(250, "Rund um Sachsen", EVENT).replace("\"elevationMeters\": 5500",
				"\"elevationMeters\": 5500, \"goal\": \"FINISH\""))
				.andExpect(jsonPath("$.event.goal").value("FINISH"));
	}

	@Test
	void eachUserOnlySeesTheirOwnProfile() throws Exception {
		save(athleteJson(250, "Mein Event", EVENT));
		String otherUser = registerNewUser(mockMvc);

		mockMvc.perform(get("/me/athlete").header("Authorization", otherUser)).andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidFtp() throws Exception {
		save(athleteJson(5000, "Test", EVENT)).andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMissingEvent() throws Exception {
		save("""
				{ "ftp": 250, "weightKg": 72, "level": "HOBBY", "availability": { "monday": 1 } }
				""").andExpect(status().isBadRequest());
	}

}
