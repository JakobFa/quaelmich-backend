package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Der Kalender-Feed hat keinen Login: zu viele Abrufe desselben Links werden gebremst, falsche Links kosten nichts */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
class CalendarRateLimitTest {

	@Autowired
	private MockMvc mockMvc;

	private String subscribe(String auth) throws Exception {
		mockMvc.perform(post("/me/plan").header("Authorization", auth)).andExpect(status().isCreated());
		String body = mockMvc.perform(post("/me/calendar").header("Authorization", auth)).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	@Test
	void tooManyFetchesOfOneLinkAreRefusedButOtherLinksAreNot() throws Exception {
		String token = subscribe(registerWithProfile(mockMvc));
		String other = subscribe(registerWithProfile(mockMvc));

		for (int i = 0; i < CalendarController.FEED_LIMIT.attempts(); i++) {
			mockMvc.perform(get("/calendar/" + token + ".ics")).andExpect(status().isOk());
		}
		mockMvc.perform(get("/calendar/" + token + ".ics"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"));

		mockMvc.perform(get("/calendar/" + other + ".ics")).andExpect(status().isOk());
	}

	@Test
	void guessingLinksAlwaysGetsNotFoundNeverTooManyRequests() throws Exception {
		for (int i = 0; i < CalendarController.FEED_LIMIT.attempts() + 5; i++) {
			mockMvc.perform(get("/calendar/guess" + i + ".ics")).andExpect(status().isNotFound());
		}
	}
}
