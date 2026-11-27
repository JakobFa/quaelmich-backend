package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Apple Health über einen Kurzbefehl (eingeschaltet per Einstellung): Schlüssel, Upload, großzügiges Lesen, Widerruf */
@SpringBootTest(properties = "app.health.shortcut-enabled=true")
@AutoConfigureMockMvc
class AppleHealthTest {

	@Autowired
	private MockMvc mockMvc;

	private String auth;

	@BeforeEach
	void register() throws Exception {
		auth = registerNewUser(mockMvc);
	}

	private String connect() throws Exception {
		String body = mockMvc.perform(post("/me/health/shortcut").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.key");
	}

	private ResultActions upload(String key, String json) throws Exception {
		return mockMvc.perform(post("/health-data").header("X-Health-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void withoutConsentNoKey() throws Exception {
		mockMvc.perform(post("/me/health/shortcut").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	void uploadWithKeyShowsUpInProfile() throws Exception {
		String key = connect();
		upload(key, """
				{ "date": "%s", "sleepHours": "7,5", "steps": 8432, "activeEnergyKcal": "512.4",
				  "restingHeartRate": 52, "hrv": "61,7 ms" }
				""".formatted(LocalDate.now(ZoneId.of("Europe/Berlin")))).andExpect(status().isOk())
				.andExpect(jsonPath("$.sleepMinutes").value(450));

		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.days[0].sleepMinutes").value(450))
				.andExpect(jsonPath("$.days[0].steps").value(8432))
				.andExpect(jsonPath("$.days[0].activeEnergyKcal").value(512))
				.andExpect(jsonPath("$.days[0].restingHeartRate").value(52))
				.andExpect(jsonPath("$.days[0].hrvSdnnMs").value(62));
	}

	@Test
	void textWithThousandsSeparatorAndMissingDateMeansToday() throws Exception {
		String key = connect();
		upload(key, "{ \"steps\": \"8.432\" }").andExpect(status().isOk())
				.andExpect(jsonPath("$.steps").value(8432))
				.andExpect(jsonPath("$.date").value(LocalDate.now(ZoneId.of("Europe/Berlin")).toString()));
		// ein zweiter Upload am selben Tag ergänzt, statt zu überschreiben
		upload(key, "{ \"sleepMinutes\": 420 }").andExpect(jsonPath("$.steps").value(8432))
				.andExpect(jsonPath("$.sleepMinutes").value(420));
	}

	@Test
	void implausibleValuesAreRejectedWithAMessage() throws Exception {
		String key = connect();
		upload(key, "{ \"sleepMinutes\": 3000 }").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sleepMinutes")));
		upload(key, "{ \"steps\": \"viele\" }").andExpect(status().isBadRequest());
		upload(key, "{}").andExpect(status().isBadRequest());
		upload(key, "{ \"date\": \"2001-01-01\", \"steps\": 5 }").andExpect(status().isBadRequest());
	}

	@Test
	void unknownOrOldKeyGetsNothing() throws Exception {
		upload("geraten", "{ \"steps\": 5 }").andExpect(status().isNotFound());
		mockMvc.perform(post("/health-data").contentType(MediaType.APPLICATION_JSON).content("{\"steps\":5}"))
				.andExpect(status().isNotFound());
		String old = connect();
		String renewed = connect();
		assertThat(renewed).isNotEqualTo(old);
		upload(old, "{ \"steps\": 5 }").andExpect(status().isNotFound());
		upload(renewed, "{ \"steps\": 5 }").andExpect(status().isOk());
	}

	@Test
	void disconnectDeletesKeyAndData() throws Exception {
		String key = connect();
		upload(key, "{ \"steps\": 5 }").andExpect(status().isOk());

		mockMvc.perform(delete("/me/health").header("Authorization", auth)).andExpect(status().isNoContent());

		upload(key, "{ \"steps\": 5 }").andExpect(status().isNotFound());
		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.days").isEmpty());
	}

	@Test
	void exportContainsHealthDays() throws Exception {
		upload(connect(), "{ \"steps\": 1234 }").andExpect(status().isOk());
		mockMvc.perform(get("/me/account/export").header("Authorization", auth))
				.andExpect(jsonPath("$.gesundheit[0].steps").value(1234));
	}

	@Test
	void deletingTheAccountDeletesHealthData() throws Exception {
		String key = connect();
		upload(key, "{ \"steps\": 5 }").andExpect(status().isOk());

		mockMvc.perform(post("/me/account/delete").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"geheim1234\" }"))
				.andExpect(status().isNoContent());

		upload(key, "{ \"steps\": 5 }").andExpect(status().isNotFound());
	}

	@Test
	void sleepAsDurationTextOrSeconds() throws Exception {
		String key = connect();
		upload(key, "{ \"sleepHours\": \"7 Std. 30 Min.\" }").andExpect(jsonPath("$.sleepMinutes").value(450));
		upload(key, "{ \"sleepMinutes\": \"6:45\" }").andExpect(jsonPath("$.sleepMinutes").value(405));
		upload(key, "{ \"sleepSeconds\": 28800 }").andExpect(jsonPath("$.sleepMinutes").value(480));
		upload(key, "{ \"sleepHours\": \"7 hr 5 min\" }").andExpect(jsonPath("$.sleepMinutes").value(425));
	}

	@Test
	void sleepWithoutUnitIsGuessedFromItsSize() throws Exception {
		String key = connect();
		upload(key, "{ \"sleep\": 7.5 }").andExpect(jsonPath("$.sleepMinutes").value(450));
		upload(key, "{ \"sleep\": 420 }").andExpect(jsonPath("$.sleepMinutes").value(420));
		upload(key, "{ \"sleep\": \"27000\" }").andExpect(jsonPath("$.sleepMinutes").value(450));
		upload(key, "{ \"sleep\": \"7 Std. 12 Min.\" }").andExpect(jsonPath("$.sleepMinutes").value(432));
		upload(key, "{ \"sleep\": 200000 }").andExpect(status().isBadRequest());
	}

}
