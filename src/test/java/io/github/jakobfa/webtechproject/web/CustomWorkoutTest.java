package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Eigene Workouts: bauen, ändern, löschen, einplanen – und nur für sich selbst */
@SpringBootTest
@AutoConfigureMockMvc
class CustomWorkoutTest {

	/** 10 min einrollen, 4 × 8 min bei 95 % mit 4 min Pause, 10 min ausfahren = 64 min */
	private static final String HILL_REPEATS = """
			{ "title": "Bergintervalle 4 × 8", "description": "Für den Hausberg", "type": "THRESHOLD",
			  "steps": [
			    { "kind": "WARMUP", "durationSeconds": 600, "powerStartPercent": 50, "powerEndPercent": 75 },
			    { "kind": "INTERVAL", "durationSeconds": 480, "powerStartPercent": 95, "powerEndPercent": 95 },
			    { "kind": "RECOVERY", "durationSeconds": 240, "powerStartPercent": 55, "powerEndPercent": 55 },
			    { "kind": "INTERVAL", "durationSeconds": 480, "powerStartPercent": 95, "powerEndPercent": 95 },
			    { "kind": "RECOVERY", "durationSeconds": 240, "powerStartPercent": 55, "powerEndPercent": 55 },
			    { "kind": "INTERVAL", "durationSeconds": 480, "powerStartPercent": 95, "powerEndPercent": 95 },
			    { "kind": "RECOVERY", "durationSeconds": 240, "powerStartPercent": 55, "powerEndPercent": 55 },
			    { "kind": "INTERVAL", "durationSeconds": 480, "powerStartPercent": 95, "powerEndPercent": 95 },
			    { "kind": "COOLDOWN", "durationSeconds": 600, "powerStartPercent": 60, "powerEndPercent": 45 }
			  ] }
			""";

	@Autowired
	private MockMvc mockMvc;

	private String token;

	@BeforeEach
	void login() throws Exception {
		token = registerWithProfile(mockMvc);
	}

	private ResultActions create(String authorization, String json) throws Exception {
		return mockMvc.perform(post("/me/workouts").header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private long createdId() throws Exception {
		String body = create(token, HILL_REPEATS).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void buildListAndDownload() throws Exception {
		create(token, HILL_REPEATS)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.durationInMinutes").value(64))
				.andExpect(jsonPath("$.steps.length()").value(9));

		mockMvc.perform(get("/me/workouts").header("Authorization", token))
				.andExpect(jsonPath("$[0].title").value("Bergintervalle 4 × 8"));
		long id = JsonPath.<Number>read(mockMvc.perform(get("/me/workouts").header("Authorization", token))
				.andReturn().getResponse().getContentAsString(), "$[0].id").longValue();
		mockMvc.perform(get("/me/workouts/" + id + "/fit").header("Authorization", token))
				.andExpect(status().isOk());
	}

	@Test
	void privateToTheOwner() throws Exception {
		long id = createdId();
		String other = registerWithProfile(mockMvc);

		mockMvc.perform(get("/me/workouts/" + id).header("Authorization", other)).andExpect(status().isNotFound());
		// nicht in der öffentlichen Bibliothek
		mockMvc.perform(get("/workouts/" + id)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/me/workouts/" + id).header("Authorization", other))
				.andExpect(status().isNotFound());
	}

	@Test
	void canBeChangedAndDeleted() throws Exception {
		long id = createdId();

		mockMvc.perform(put("/me/workouts/" + id).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(HILL_REPEATS.replace("4 × 8", "4 × 8 hart")))
				.andExpect(jsonPath("$.title").value("Bergintervalle 4 × 8 hart"));
		mockMvc.perform(delete("/me/workouts/" + id).header("Authorization", token))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/me/workouts").header("Authorization", token)).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void invalidWorkoutsAreRejected() throws Exception {
		create(token, HILL_REPEATS.replace("\"THRESHOLD\"", "\"FTP_TEST\"")).andExpect(status().isBadRequest());
		create(token, HILL_REPEATS.replace("\"powerStartPercent\": 95", "\"powerStartPercent\": 400"))
				.andExpect(status().isBadRequest());
		// länger als 6 h
		create(token, HILL_REPEATS.replace("\"durationSeconds\": 480", "\"durationSeconds\": 14000"))
				.andExpect(status().isBadRequest());
		create(token, HILL_REPEATS.replace("Bergintervalle 4 × 8", " ")).andExpect(status().isBadRequest());
	}

	@Test
	void ownWorkoutCanReplaceAPlannedDayButNotSomeoneElses() throws Exception {
		long own = createdId();
		String plan = mockMvc.perform(post("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString();
		Number plannedId = JsonPath.read(plan, "$.workouts[0].id");

		mockMvc.perform(put("/planned-workouts/" + plannedId + "/workout").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"workoutId\": %d }".formatted(own)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workout.title").value("Bergintervalle 4 × 8"));

		// gelöscht: der eingeplante Tag bleibt lesbar
		mockMvc.perform(delete("/me/workouts/" + own).header("Authorization", token));
		mockMvc.perform(get("/planned-workouts/" + plannedId).header("Authorization", token))
				.andExpect(jsonPath("$.workout.title").value("Bergintervalle 4 × 8"));

		String other = registerWithProfile(mockMvc);
		String foreign = create(other, HILL_REPEATS).andReturn().getResponse().getContentAsString();
		mockMvc.perform(put("/planned-workouts/" + plannedId + "/workout").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"workoutId\": %d }".formatted(((Number) JsonPath.read(foreign, "$.id")).longValue())))
				.andExpect(status().isConflict());
	}

}
