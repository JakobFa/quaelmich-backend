package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
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

/** Weitere, gleich wichtige Events: Der Plan arbeitet der Reihe nach auf alle hin */
@SpringBootTest
@AutoConfigureMockMvc
class RaceEventTest {

	@Autowired
	private MockMvc mockMvc;

	private String token;
	/** Erstes Event im Testprofil: in 10 Wochen */
	private final LocalDate firstEvent = LocalDate.now().plusWeeks(10);

	@BeforeEach
	void profileWithPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
	}

	private ResultActions addEvent(LocalDate date) throws Exception {
		return mockMvc.perform(post("/me/events").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Herbstklassiker", "date": "%s", "distanceKm": 160, "elevationMeters": 1800 }
						""".formatted(date)));
	}

	private List<String> plannedDates() throws Exception {
		return JsonPath.read(mockMvc.perform(get("/me/plan").header("Authorization", token)).andReturn().getResponse()
				.getContentAsString(), "$.workouts[*].date");
	}

	@Test
	void aLaterEventExtendsThePlanAndRemovingItBringsTheOldEndBack() throws Exception {
		List<String> before = plannedDates();
		LocalDate second = firstEvent.plusWeeks(6);

		String created = addEvent(second).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(created, "$.id");

		List<String> after = plannedDates();
		assertThat(after).anyMatch(date -> LocalDate.parse(date).isAfter(firstEvent));
		assertThat(after).noneMatch(date -> LocalDate.parse(date).isAfter(second.minusDays(1)));
		assertThat(after.stream().filter(d -> !LocalDate.parse(d).isAfter(firstEvent)))
				.as("bis zum ersten Event bleibt alles wie vorher").containsExactlyElementsOf(before);
		mockMvc.perform(get("/me/plan").header("Authorization", token))
				.andExpect(jsonPath("$.moreEvents[0].name").value("Herbstklassiker"));

		mockMvc.perform(delete("/me/events/" + id).header("Authorization", token)).andExpect(status().isNoContent());
		assertThat(plannedDates()).containsExactlyElementsOf(before);
	}

	@Test
	void oneEventPerDayAndOnlyForTheOwner() throws Exception {
		addEvent(firstEvent).andExpect(status().isConflict());
		String created = addEvent(firstEvent.plusWeeks(4)).andReturn().getResponse().getContentAsString();
		addEvent(firstEvent.plusWeeks(4)).andExpect(status().isConflict());

		String other = registerWithProfile(mockMvc);
		mockMvc.perform(delete("/me/events/" + JsonPath.read(created, "$.id")).header("Authorization", other))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/me/events/" + JsonPath.read(created, "$.id")).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{ "name": "Umbenannt", "date": "%s", "distanceKm": 160, "elevationMeters": 1800 }
						""".formatted(firstEvent.plusWeeks(5))))
				.andExpect(jsonPath("$.name").value("Umbenannt"));
		addEvent(LocalDate.now().minusDays(1)).andExpect(status().isBadRequest());
	}

}
