package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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

/** "Diese Woche habe ich andere Zeit" – nur die eine Woche wird neu verteilt */
@SpringBootTest
@AutoConfigureMockMvc
class WeekAvailabilityTest {

	/** Normale Verfügbarkeit im Testprofil: Di 1,5 h, Do 1,5 h, Sa 3 h, So 2 h */
	private static final String ONLY_WEDNESDAY = """
			{ "monday": 0, "tuesday": 0, "wednesday": 1.5, "thursday": 0,
			  "friday": 0, "saturday": 0, "sunday": 0 }
			""";

	@Autowired
	private MockMvc mockMvc;

	private String token;
	private LocalDate nextMonday;

	@BeforeEach
	void createPlan() throws Exception {
		token = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", token));
		nextMonday = LocalDate.now(ZoneId.of("Europe/Berlin")).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}

	private ResultActions setWeek(LocalDate monday, String availability) throws Exception {
		return mockMvc.perform(put("/me/plan/weeks/" + monday + "/availability").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(availability));
	}

	private static List<String> datesInWeek(String plan, LocalDate monday) {
		List<String> dates = JsonPath.read(plan, "$.workouts[*].date");
		return dates.stream().filter(date -> !LocalDate.parse(date).isBefore(monday)
				&& LocalDate.parse(date).isBefore(monday.plusWeeks(1))).toList();
	}

	@Test
	void onlyThisWeekIsRedistributed() throws Exception {
		String plan = setWeek(nextMonday, ONLY_WEDNESDAY).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(datesInWeek(plan, nextMonday)).containsExactly(nextMonday.plusDays(2).toString());
		assertThat(datesInWeek(plan, nextMonday.plusWeeks(1))).as("Woche danach bleibt normal").hasSize(4);
		assertThat((Object) JsonPath.read(plan, "$.weekAvailability['" + nextMonday + "'].wednesday"))
				.isEqualTo(1.5);
	}

	@Test
	void backToNormal() throws Exception {
		setWeek(nextMonday, ONLY_WEDNESDAY);

		String plan = mockMvc.perform(delete("/me/plan/weeks/" + nextMonday + "/availability")
				.header("Authorization", token)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(datesInWeek(plan, nextMonday)).hasSize(4);
		assertThat((java.util.Map<?, ?>) JsonPath.read(plan, "$.weekAvailability")).isEmpty();
	}

	@Test
	void onlyMondaysOfCurrentOrComingWeeks() throws Exception {
		setWeek(nextMonday.plusDays(1), ONLY_WEDNESDAY).andExpect(status().isBadRequest());
		setWeek(nextMonday.minusWeeks(2), ONLY_WEDNESDAY).andExpect(status().isBadRequest());
		setWeek(nextMonday, "{ \"monday\": 20 }").andExpect(status().isBadRequest());
	}

}
