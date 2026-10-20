package io.github.jakobfa.webtechproject.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AthleteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private static String athleteJson(int ftp, String eventName) {
		String eventDate = LocalDate.now().plusMonths(6).toString();
		return """
				{
				  "ftp": %d,
				  "weightKg": 72.5,
				  "level": "AMBITIOUS",
				  "event": { "name": "%s", "date": "%s", "distanceKm": 227, "elevationMeters": 5500 },
				  "availability": { "monday": 0, "tuesday": 1.5, "wednesday": 0, "thursday": 1.5,
				                    "friday": 0, "saturday": 3, "sunday": 2 }
				}
				""".formatted(ftp, eventName, eventDate);
	}

	private long createAthlete(String json) throws Exception {
		MvcResult result = mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void createsAndLoadsAthlete() throws Exception {
		long id = createAthlete(athleteJson(250, "Ötztaler Radmarathon"));

		mockMvc.perform(get("/athletes/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ftp").value(250))
				.andExpect(jsonPath("$.level").value("AMBITIOUS"))
				.andExpect(jsonPath("$.event.name").value("Ötztaler Radmarathon"))
				.andExpect(jsonPath("$.availability.saturday").value(3.0));
	}

	@Test
	void createReturnsLocationOfNewAthlete() throws Exception {
		mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(athleteJson(200, "Test")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/athletes/")));
	}

	@Test
	void updatesExistingAthlete() throws Exception {
		long id = createAthlete(athleteJson(250, "Ötztaler Radmarathon"));

		mockMvc.perform(put("/athletes/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(athleteJson(265, "Maratona dles Dolomites")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.ftp").value(265))
				.andExpect(jsonPath("$.event.name").value("Maratona dles Dolomites"));
	}

	@Test
	void rejectsInvalidFtp() throws Exception {
		mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(athleteJson(5000, "Test")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMissingEvent() throws Exception {
		String json = """
				{ "ftp": 250, "weightKg": 72, "level": "HOBBY",
				  "availability": { "monday": 1 } }
				""";
		mockMvc.perform(post("/athletes").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownAthleteIsNotFound() throws Exception {
		mockMvc.perform(get("/athletes/99999")).andExpect(status().isNotFound());
	}

}
