package io.github.jakobfa.webtechproject.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.jayway.jsonpath.JsonPath;

import io.github.jakobfa.webtechproject.health.DailyHealth;
import io.github.jakobfa.webtechproject.health.DailyHealthRepository;
import io.github.jakobfa.webtechproject.health.WellnessImportRepository;
import io.github.jakobfa.webtechproject.health.WellnessImportService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsWellness;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/** Verlauf von Schlaf und Erholung bis ein Jahr zurück: in Blöcken, fortsetzbar, regelmäßig nur die letzten Tage */
@SpringBootTest
@AutoConfigureMockMvc
class WellnessBackfillTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Clock clock;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private DailyHealthRepository days;

	@Autowired
	private WellnessImportRepository imports;

	@Autowired
	private WellnessImportService wellness;

	@MockitoBean
	private IntervalsIcuClient client;

	private String auth;
	private Long userId;
	private LocalDate today;
	private LocalDate target;

	/** intervals.icu hat an jedem ersten und letzten Tag einer Anfrage Ruhepuls und Schritte, aber keinen Schlaf */
	private static List<IntervalsWellness> partialDays(InvocationOnMock call) {
		LocalDate oldest = call.getArgument(1);
		LocalDate newest = call.getArgument(2);
		List<IntervalsWellness> out = new ArrayList<>();
		out.add(new IntervalsWellness(newest.toString(), null, null, 50, null, null, 8000));
		if (!oldest.equals(newest)) {
			out.add(new IntervalsWellness(oldest.toString(), null, null, 52, null, null, 6000));
		}
		return out;
	}

	@BeforeEach
	void register() throws Exception {
		String email = "backfill-%s@example.com".formatted(UUID.randomUUID());
		String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"%s\", \"password\": \"geheim1234\" }".formatted(email)))
				.andReturn().getResponse().getContentAsString();
		auth = "Bearer " + JsonPath.read(response, "$.token");
		userId = users.findByEmail(email).orElseThrow().getId();
		today = LocalDate.now(clock);
		target = today.minusDays(WellnessImportService.WELLNESS_BACKFILL_DAYS - 1L);
		when(client.verify(any())).thenReturn(true);
		when(client.listWellness(any(), any(), any())).thenAnswer(WellnessBackfillTest::partialDays);
		mockMvc.perform(put("/me/intervals").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"apiKey\": \"key\", \"athleteId\": \"i42\" }")).andExpect(status().isOk());
	}

	private void enable() throws Exception {
		mockMvc.perform(post("/me/health/intervals").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}")).andExpect(status().isOk());
	}

	private void importNow() throws Exception {
		mockMvc.perform(post("/me/health/intervals/import").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.lastImportAt").exists());
	}

	private LocalDate historyFrom() {
		return imports.findByOwnerId(userId).orElseThrow().getHistoryFrom();
	}

	@Test
	void enablingLoadsAYearInBlocksIncludingDaysWithoutSleep() throws Exception {
		enable();

		verify(client, times(5)).listWellness(any(), any(), any());
		verify(client).listWellness(any(), eq(today.minusDays(89)), eq(today));
		verify(client).listWellness(any(), eq(target), eq(target.plusDays(4)));
		DailyHealth oldest = days.findByOwnerIdAndDate(userId, target).orElseThrow();
		assertThat(oldest.getRestingHeartRate()).isEqualTo(52);
		assertThat(oldest.getSteps()).isEqualTo(6000);
		assertThat(oldest.getSleepMinutes()).isNull();
		assertThat(historyFrom()).isEqualTo(target);
	}

	@Test
	void withACompleteHistoryImportNowOnlyRefreshesTheLastWeeks() throws Exception {
		enable();
		clearInvocations(client);

		importNow();

		verify(client, times(1)).listWellness(any(), any(), any());
		verify(client).listWellness(any(), eq(today.minusDays(27)), eq(today));
	}

	@Test
	void anExistingUserGetsTheHistoryWithOneClick() throws Exception {
		enable();
		var settings = imports.findByOwnerId(userId).orElseThrow();
		settings.setHistoryFrom(null);
		imports.save(settings);
		days.deleteAll(days.findByOwnerIdAndDateBetweenOrderByDate(userId, target, today.minusDays(28)));
		clearInvocations(client);

		importNow();

		verify(client, times(5)).listWellness(any(), any(), any());
		assertThat(days.findByOwnerIdAndDate(userId, target)).isPresent();
		assertThat(historyFrom()).isEqualTo(target);
	}

	@Test
	void rateLimitOrServerErrorStopsAndTheNextImportContinuesThere() throws Exception {
		LocalDate thirdBlockEnd = today.minusDays(180);
		doThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null))
				.doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null))
				.doAnswer(WellnessBackfillTest::partialDays)
				.when(client).listWellness(any(), any(), eq(thirdBlockEnd));

		enable();
		verify(client, times(3)).listWellness(any(), any(), any());
		assertThat(historyFrom()).isEqualTo(today.minusDays(179));
		assertThat(days.findByOwnerIdAndDate(userId, target)).isEmpty();

		clearInvocations(client);
		importNow();
		// die letzten Wochen, dann wieder der dritte Block – 503, Abbruch ohne Wiederholung
		verify(client, times(2)).listWellness(any(), any(), any());
		assertThat(historyFrom()).isEqualTo(today.minusDays(179));

		clearInvocations(client);
		importNow();
		verify(client, times(4)).listWellness(any(), any(), any());
		verify(client).listWellness(any(), eq(today.minusDays(269)), eq(thirdBlockEnd));
		assertThat(historyFrom()).isEqualTo(target);
		assertThat(days.findByOwnerIdAndDate(userId, target)).isPresent();
	}

	@Test
	void theSameDayTwiceIsOneRowUpdatedAndNullOverwritesNothing() throws Exception {
		doReturn(List.of(new IntervalsWellness(today.toString(), 27000, 80.0, 48, 70.0, null, 9000)))
				.doReturn(List.of())
				.doReturn(List.of(new IntervalsWellness(today.toString(), null, null, 46, null, 40.0, null)))
				.when(client).listWellness(any(), any(), any());
		enable();
		wellness.syncRecent(userId);

		List<DailyHealth> rows = days.findByOwnerIdOrderByDate(userId);
		assertThat(rows).hasSize(1);
		DailyHealth day = rows.getFirst();
		assertThat(day.getRestingHeartRate()).isEqualTo(46);
		assertThat(day.getHrvSdnnMs()).isEqualTo(40);
		assertThat(day.getSleepMinutes()).isEqualTo(450);
		assertThat(day.getSleepScore()).isEqualTo(80);
		assertThat(day.getHrvMs()).isEqualTo(70);
		assertThat(day.getSteps()).isEqualTo(9000);
	}

	@Test
	void theRegularSyncOnlyAsksForSevenDays() throws Exception {
		enable();
		clearInvocations(client);

		wellness.syncRecent(userId);
		var settings = imports.findByOwnerId(userId).orElseThrow();
		settings.setLastImportAt(null);
		imports.save(settings);
		wellness.importIfStale(userId);

		verify(client, times(2)).listWellness(any(), eq(today.minusDays(6)), eq(today));
		verify(client, times(2)).listWellness(any(), any(), any());
	}

	@Test
	void readingRefetchesWhenTheLastImportIsOlderThanFifteenMinutes() throws Exception {
		enable();
		clearInvocations(client);
		var settings = imports.findByOwnerId(userId).orElseThrow();

		settings.setLastImportAt(clock.instant().minusSeconds(5 * 60));
		imports.save(settings);
		wellness.importIfStale(userId);
		verify(client, times(0)).listWellness(any(), any(), any());

		settings = imports.findByOwnerId(userId).orElseThrow();
		settings.setLastImportAt(clock.instant().minusSeconds(16 * 60));
		imports.save(settings);
		wellness.importIfStale(userId);
		verify(client).listWellness(any(), eq(today.minusDays(6)), eq(today));
	}

	@Test
	void endingTheImportDeletesTheWholeHistory() throws Exception {
		enable();
		assertThat(days.findByOwnerIdOrderByDate(userId)).hasSizeGreaterThan(5);

		mockMvc.perform(delete("/me/health").header("Authorization", auth)).andExpect(status().isNoContent());

		assertThat(days.findByOwnerIdOrderByDate(userId)).isEmpty();
		assertThat(imports.findByOwnerId(userId)).isEmpty();
	}

}
