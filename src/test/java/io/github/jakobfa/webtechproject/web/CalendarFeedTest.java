package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
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

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.service.CalendarFeed;

/** Kalender-Abo: geheimer Link, iCal-Inhalt, Ausfälle, Erneuern und Beenden */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarFeedTest {

	@Autowired
	private MockMvc mockMvc;

	private String auth;

	@BeforeEach
	void createPlan() throws Exception {
		auth = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", auth)).andExpect(status().isCreated());
	}

	private String subscribe() throws Exception {
		String body = mockMvc.perform(post("/me/calendar").header("Authorization", auth))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String feed(String token) throws Exception {
		return mockMvc.perform(get("/calendar/" + token + ".ics"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	void withoutSubscriptionThereIsNoToken() throws Exception {
		mockMvc.perform(get("/me/calendar").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void theFeedIsPublicByTokenAndListsThePlannedWorkoutsAsAllDayEvents() throws Exception {
		String ics = feed(subscribe());

		assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
		assertThat(ics).contains("REFRESH-INTERVAL;VALUE=DURATION:PT1H", "X-WR-CALNAME:quaelmich Training");
		assertThat(ics).contains("BEGIN:VEVENT", "UID:workout-", "DTSTART;VALUE=DATE:", "TRANSP:TRANSPARENT");
		assertThat(ics.split("BEGIN:VEVENT", -1).length - 1).isGreaterThan(5);
	}

	@Test
	void unknownTokenGetsNothing() throws Exception {
		mockMvc.perform(get("/calendar/does-not-exist.ics")).andExpect(status().isNotFound());
	}

	@Test
	void anotherLinkInvalidatesTheOldOne() throws Exception {
		String old = subscribe();
		String renewed = subscribe();

		assertThat(renewed).isNotEqualTo(old).hasSizeGreaterThan(30);
		mockMvc.perform(get("/calendar/" + old + ".ics")).andExpect(status().isNotFound());
		feed(renewed);
	}

	@Test
	void endingTheSubscriptionStopsTheFeed() throws Exception {
		String token = subscribe();
		mockMvc.perform(delete("/me/calendar").header("Authorization", auth)).andExpect(status().isNoContent());

		mockMvc.perform(get("/calendar/" + token + ".ics")).andExpect(status().isNotFound());
	}

	@Test
	void skippedFutureWorkoutsDisappearFromTheCalendar() throws Exception {
		String token = subscribe();
		int before = feed(token).split("BEGIN:VEVENT", -1).length - 1;
		String plan = mockMvc.perform(get("/me/plan").header("Authorization", auth)).andReturn().getResponse()
				.getContentAsString();
		long id = ((Number) ((java.util.List<?>) JsonPath.read(plan, "$.workouts[-1:].id")).get(0)).longValue(); // weit in der Zukunft

		mockMvc.perform(put("/planned-workouts/" + id + "/skip").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"reason\": \"SICK\" }"))
				.andExpect(status().isOk());

		int after = feed(token).split("BEGIN:VEVENT", -1).length - 1;
		assertThat(after).isEqualTo(before - 1);
	}

	@Test
	void pastSkippedDaysShowTheReasonInTheCalendarAsDiary() {
		var workout = new io.github.jakobfa.webtechproject.model.Workout(1L, "Grundlage",
				io.github.jakobfa.webtechproject.model.WorkoutType.ENDURANCE, "locker",
				java.util.List.of(io.github.jakobfa.webtechproject.model.WorkoutStep.steady(
						io.github.jakobfa.webtechproject.model.StepKind.STEADY, 3600, 65)));
		var now = java.time.ZonedDateTime.parse("2026-11-24T12:00:00+01:00[Europe/Berlin]");
		var sick = new io.github.jakobfa.webtechproject.model.PlannedWorkout(java.time.LocalDate.of(2026, 11, 20), workout,
				null, null);
		sick.setSkipReason(io.github.jakobfa.webtechproject.model.SkipReason.SICK);
		var tired = new io.github.jakobfa.webtechproject.model.PlannedWorkout(java.time.LocalDate.of(2026, 11, 21),
				workout, null, null);
		tired.setSkipReason(io.github.jakobfa.webtechproject.model.SkipReason.TIRED);
		var today = new io.github.jakobfa.webtechproject.model.PlannedWorkout(now.toLocalDate(), workout, null, null);
		today.setSkipReason(io.github.jakobfa.webtechproject.model.SkipReason.INJURED);
		var future = new io.github.jakobfa.webtechproject.model.PlannedWorkout(now.toLocalDate().plusDays(1), workout,
				null, null);
		future.setSkipReason(io.github.jakobfa.webtechproject.model.SkipReason.SICK);

		String ics = CalendarFeed.build(java.util.List.of(sick, tired, today, future), planned -> workout, now);

		assertThat(ics).contains("SUMMARY:Krank").contains("Geplant war: Grundlage")
				.contains("SUMMARY:Nicht gemacht (zu müde)").contains("SUMMARY:Verletzt");
		assertThat(ics.split("BEGIN:VEVENT", -1).length - 1).isEqualTo(3); // der kommende kranke Tag steht nicht drin
	}

	@Test
	void theFeedIsNotReachableForOtherUsersViaApi() throws Exception {
		subscribe();
		String other = registerWithProfile(mockMvc);
		mockMvc.perform(get("/me/calendar").header("Authorization", other))
				.andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	void escapingAndLineFoldingFollowTheStandard() {
		assertThat(CalendarFeed.escape("a, b; c\\d\nneu")).isEqualTo("a\\, b\\; c\\\\d\\nneu");

		String folded = CalendarFeed.fold("DESCRIPTION:" + "ä".repeat(100));
		for (String line : folded.split("\r\n")) {
			assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
		}
		// beim Zusammensetzen (Fortsetzungszeilen beginnen mit Leerzeichen) kommt der Originaltext heraus
		assertThat(folded.replace("\r\n ", "")).isEqualTo("DESCRIPTION:" + "ä".repeat(100));
	}
}
