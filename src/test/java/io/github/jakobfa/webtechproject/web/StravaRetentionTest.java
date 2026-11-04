package io.github.jakobfa.webtechproject.web;

import static io.github.jakobfa.webtechproject.web.TestUsers.registerWithProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.StravaAthlete;
import io.github.jakobfa.webtechproject.integration.strava.StravaClient.TokenResponse;
import io.github.jakobfa.webtechproject.integration.strava.StravaConnection;
import io.github.jakobfa.webtechproject.integration.strava.StravaConnectionRepository;
import io.github.jakobfa.webtechproject.integration.strava.StravaRetentionService;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;

/** Strava erlaubt, Daten höchstens 7 Tage zwischenzuspeichern: Was nicht erneuert wurde, wird gelöscht */
@SpringBootTest
@AutoConfigureMockMvc
class StravaRetentionTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private StravaRetentionService retention;
	@Autowired
	private StravaService stravaService;
	@Autowired
	private StravaConnectionRepository connections;
	@Autowired
	private AthleteService athletes;
	@Autowired
	private ExtraRideRepository extraRides;
	@Autowired
	private TrainingPlanRepository plans;
	@Autowired
	private TrainingPlanService planService;
	@Autowired
	private Clock clock;
	@MockitoBean
	private StravaClient client;

	private StravaConnection connection;
	private Athlete athlete;

	@BeforeEach
	void connected() throws Exception {
		String auth = registerWithProfile(mockMvc);
		mockMvc.perform(post("/me/plan").header("Authorization", auth)).andExpect(status().isCreated());
		String name = "Retention-" + UUID.randomUUID();
		when(client.exchangeCode("abc")).thenReturn(new TokenResponse("acc", "ref", 4_000_000_000L,
				new StravaAthlete(System.nanoTime(), name)));
		mockMvc.perform(post("/me/strava").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"abc\", \"scope\": \"read,activity:read_all\" }")).andExpect(status().isOk());
		connection = connections.findAll().stream().filter(c -> name.equals(c.getAthleteName())).findFirst()
				.orElseThrow();
		athlete = athletes.findByOwner(connection.getOwner().getId()).orElseThrow();
	}

	private ExtraRide ride(String id, int daysAgo) {
		ExtraRide ride = new ExtraRide(athlete, RecordedActivity.STRAVA_PREFIX + id);
		ride.setDate(LocalDate.now(clock).minusDays(daysAgo));
		ride.setDurationMinutes(60);
		ride.setTss(50);
		return extraRides.save(ride);
	}

	private long strava() {
		return extraRides.findByAthleteId(athlete.getId()).stream().count();
	}

	private void syncedAt(Instant when) {
		connection = connections.findById(connection.getId()).orElseThrow();
		connection.setLastSyncAt(when);
		connections.save(connection);
	}

	@Test
	void afterARecentSyncOnlyRidesOutsideTheImportWindowAreDeleted() {
		ride(UUID.randomUUID().toString(), 3);
		ride(UUID.randomUUID().toString(), 60);
		syncedAt(Instant.now(clock).minusSeconds(3600));

		retention.purgeExpired();

		assertThat(strava()).isEqualTo(1);
		assertThat(extraRides.findByAthleteId(athlete.getId()).get(0).getDate()).isEqualTo(LocalDate.now(clock).minusDays(3));
	}

	@Test
	void everythingFromStravaGoesWhenTheLastSyncIsTooOldOrMissing() {
		ride(UUID.randomUUID().toString(), 3);
		ExtraRide own = new ExtraRide(athlete, "intervals-" + UUID.randomUUID()); // andere Quelle bleibt
		own.setDate(LocalDate.now(clock).minusDays(3));
		extraRides.save(own);

		syncedAt(Instant.now(clock).minus(java.time.Duration.ofDays(7)));
		retention.purgeExpired();
		assertThat(extraRides.findByAthleteId(athlete.getId())).extracting(ExtraRide::getActivityId)
				.containsExactly(own.getActivityId());

		ride(UUID.randomUUID().toString(), 2);
		syncedAt(null);
		retention.purgeExpired();
		assertThat(extraRides.findByAthleteId(athlete.getId())).hasSize(1);
	}

	@Test
	void thePlanDoesNotAdaptToMissingRidesUntilStravaWasAskedAgain() {
		syncedAt(null); // Werte gelöscht oder nie abgeholt
		planService.adaptToActuals(athlete.getId());
		assertThat(plans.findByAthleteId(athlete.getId()).orElseThrow().getActualsFingerprint()).isNull();

		stravaService.markSynced(connection.getOwner().getId());
		planService.adaptToActuals(athlete.getId());
		assertThat(plans.findByAthleteId(athlete.getId()).orElseThrow().getActualsFingerprint()).isNotNull();
	}
}
