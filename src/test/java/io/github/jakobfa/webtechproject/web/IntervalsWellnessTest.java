package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerNewUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsWellness;

/** Schlaf und Erholung von intervals.icu: nur mit Einwilligung, beim Öffnen aktuell, Widerruf löscht alles */
@SpringBootTest
@AutoConfigureMockMvc
class IntervalsWellnessTest {

	private static final String TODAY = LocalDate.now(ZoneId.of("Europe/Berlin")).toString();
	private static final String YESTERDAY = LocalDate.now(ZoneId.of("Europe/Berlin")).minusDays(1).toString();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IntervalsIcuClient client;

	private String auth;

	@BeforeEach
	void register() throws Exception {
		auth = registerNewUser(mockMvc);
		when(client.verify(any())).thenReturn(true);
		when(client.listWellness(any(), any(), any())).thenReturn(List.of(
				new IntervalsWellness(TODAY, 27000, 82.0, 48, 71.4, null, 9500),
				new IntervalsWellness(YESTERDAY, null, null, 50, null, 45.0, null),
				new IntervalsWellness("2001-01-01", null, null, null, null, null, null)));
	}

	private void connectIntervals() throws Exception {
		mockMvc.perform(put("/me/intervals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"apiKey\": \"key\", \"athleteId\": \"i42\" }")).andExpect(status().isOk());
	}

	private ResultActions enable(String body) throws Exception {
		return mockMvc.perform(post("/me/health/intervals").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void withoutIntervalsNothingToEnable() throws Exception {
		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.intervalsConnected").value(false))
				.andExpect(jsonPath("$.importEnabled").value(false));
		enable("{\"consent\":true}").andExpect(status().isNotFound());
	}

	@Test
	void onlyWithConsent() throws Exception {
		connectIntervals();
		enable("{}").andExpect(status().isBadRequest());
		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.intervalsConnected").value(true))
				.andExpect(jsonPath("$.importEnabled").value(false));
		verify(client, never()).listWellness(any(), any(), any());
	}

	@Test
	void enablingImportsWhateverSourceDeliveredToIntervals() throws Exception {
		connectIntervals();
		enable("{\"consent\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.importEnabled").value(true))
				.andExpect(jsonPath("$.consentAt").exists())
				.andExpect(jsonPath("$.lastImportAt").exists())
				.andExpect(jsonPath("$.days.length()").value(2))
				.andExpect(jsonPath("$.days[0].date").value(TODAY))
				.andExpect(jsonPath("$.days[0].sleepMinutes").value(450))
				.andExpect(jsonPath("$.days[0].sleepScore").value(82))
				.andExpect(jsonPath("$.days[0].restingHeartRate").value(48))
				.andExpect(jsonPath("$.days[0].hrvMs").value(71))
				.andExpect(jsonPath("$.days[0].steps").value(9500))
				.andExpect(jsonPath("$.days[1].hrvSdnnMs").value(45))
				.andExpect(jsonPath("$.days[1].sleepMinutes").doesNotExist());
	}

	@Test
	void openingTheProfileDoesNotRefetchWithinAnHour() throws Exception {
		connectIntervals();
		enable("{\"consent\":true}").andExpect(status().isOk());
		mockMvc.perform(get("/me/health").header("Authorization", auth)).andExpect(status().isOk());
		verify(client, times(1)).listWellness(any(), any(), any());

		mockMvc.perform(post("/me/health/intervals/import").header("Authorization", auth))
				.andExpect(status().isOk());
		verify(client, times(2)).listWellness(any(), any(), any());
	}

	@Test
	void disablingDeletesTheData() throws Exception {
		connectIntervals();
		enable("{\"consent\":true}").andExpect(status().isOk());

		mockMvc.perform(delete("/me/health").header("Authorization", auth)).andExpect(status().isNoContent());

		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.importEnabled").value(false)).andExpect(jsonPath("$.days").isEmpty());
		mockMvc.perform(post("/me/health/intervals/import").header("Authorization", auth))
				.andExpect(status().isNotFound());
	}

	@Test
	void disconnectingIntervalsEndsTheImport() throws Exception {
		connectIntervals();
		enable("{\"consent\":true}").andExpect(status().isOk());

		mockMvc.perform(delete("/me/intervals").header("Authorization", auth)).andExpect(status().isNoContent());

		mockMvc.perform(get("/me/health").header("Authorization", auth))
				.andExpect(jsonPath("$.importEnabled").value(false)).andExpect(jsonPath("$.days").isEmpty());
	}

	@Test
	void shortcutIsSwitchedOff() throws Exception {
		mockMvc.perform(post("/me/health/shortcut").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}")).andExpect(status().isNotFound());
		mockMvc.perform(post("/health-data").header("X-Health-Key", "x").contentType(MediaType.APPLICATION_JSON)
				.content("{\"steps\":1}")).andExpect(status().isNotFound());
	}

	@Test
	void deletingTheAccountEndsTheImport() throws Exception {
		connectIntervals();
		enable("{\"consent\":true}").andExpect(status().isOk());
		mockMvc.perform(post("/me/account/delete").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"geheim1234\" }"))
				.andExpect(status().isNoContent());
	}

}
