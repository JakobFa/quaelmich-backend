package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.athleteJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;

/** Konto löschen und Daten exportieren */
@SpringBootTest
@AutoConfigureMockMvc
class AccountTest {

	private static final String PASSWORD = "geheim1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private AthleteRepository athletes;

	private String email;
	private String token;

	@BeforeEach
	void userWithPlanWorkoutAndFeedback() throws Exception {
		email = "konto-" + UUID.randomUUID() + "@example.com";
		String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
				.andReturn().getResponse().getContentAsString();
		token = "Bearer " + JsonPath.read(response, "$.token");
		mockMvc.perform(put("/me/athlete").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(athleteJson(240, "Testevent", LocalDate.now().plusWeeks(10))));
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		mockMvc.perform(post("/me/workouts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "title": "Mein Workout", "type": "TEMPO", "steps": [
						  { "kind": "STEADY", "durationSeconds": 1800, "powerStartPercent": 80, "powerEndPercent": 80 } ] }
						"""));
		mockMvc.perform(post("/me/feedback").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"message\": \"Gefällt mir\", \"page\": \"/plan\" }"));
	}

	private ResultActions delete(String password) throws Exception {
		return mockMvc.perform(post("/me/account/delete").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"%s\" }".formatted(password)));
	}

	@Test
	void exportContainsEverythingButNoSecrets() throws Exception {
		String body = mockMvc.perform(get("/me/account/export").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"quaelmich-daten.json\""))
				.andExpect(jsonPath("$.konto.email").value(email))
				.andExpect(jsonPath("$.profil.ftp").value(240))
				.andExpect(jsonPath("$.plan.workouts").isNotEmpty())
				.andExpect(jsonPath("$.eigeneWorkouts[0].title").value("Mein Workout"))
				.andExpect(jsonPath("$.feedback[0].text").value("Gefällt mir"))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$");
	}

	@Test
	void wrongPasswordDeletesNothing() throws Exception {
		delete("falsch").andExpect(status().isForbidden());
		assertThat(users.findByEmail(email)).isPresent();
	}

	@Test
	void deletingRemovesTheAccountAndAllData() throws Exception {
		Long userId = users.findByEmail(email).orElseThrow().getId();

		delete(PASSWORD).andExpect(status().isNoContent());

		assertThat(users.findByEmail(email)).isEmpty();
		assertThat(athletes.findByOwnerId(userId)).isEmpty();
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
				.andExpect(status().isUnauthorized());
	}

}
