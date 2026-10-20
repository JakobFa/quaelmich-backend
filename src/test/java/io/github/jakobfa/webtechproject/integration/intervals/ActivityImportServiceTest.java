package io.github.jakobfa.webtechproject.integration.intervals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Zuordnung gefahrener Einheiten zum Plan – mit gemockten Abhängigkeiten, ohne Datenbank und Netzwerk.
 */
class ActivityImportServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC);
	private static final IntervalsCredentials CREDENTIALS = new IntervalsCredentials("geheim", "i42");

	private final IntervalsConnectionService connectionService = mock(IntervalsConnectionService.class);
	private final IntervalsIcuClient client = mock(IntervalsIcuClient.class);
	private final AthleteService athleteService = mock(AthleteService.class);
	private final PlannedWorkoutRepository repository = mock(PlannedWorkoutRepository.class);
	private final ActivityImportService service = new ActivityImportService(connectionService, client,
			athleteService, repository, CLOCK);

	private final WorkoutLibrary library = new WorkoutLibrary();

	@BeforeEach
	void connectedUserWithProfile() {
		Athlete athlete = new Athlete();
		athlete.setId(7L);
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.of(CREDENTIALS));
		when(athleteService.findByOwner(1L)).thenReturn(Optional.of(athlete));
	}

	private PlannedWorkout planned(LocalDate date) {
		return new PlannedWorkout(date, library.findById(5L).orElseThrow(), null, Phase.BASE);
	}

	private static IntervalsActivity ride(String id, LocalDate date, int minutes, int load) {
		return new IntervalsActivity(id, date + "T17:00:00", "Ride", minutes * 60, minutes * 500.0, load, "WAHOO");
	}

	private void givenPlan(PlannedWorkout... workouts) {
		when(repository.findByPlanAthleteIdAndDateBetween(eq(7L), any(), any())).thenReturn(List.of(workouts));
	}

	@Test
	void ticksOffThePlannedWorkoutAndStoresActualValues() {
		PlannedWorkout yesterday = planned(TODAY.minusDays(1));
		yesterday.setRating(6);
		givenPlan(yesterday);
		when(client.listActivities(CREDENTIALS, TODAY.minusDays(14), TODAY))
				.thenReturn(List.of(ride("i1", TODAY.minusDays(1), 102, 78)));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.matched()).isEqualTo(1);
		assertThat(yesterday.isCompleted()).isTrue();
		assertThat(yesterday.getActivityId()).isEqualTo("i1");
		assertThat(yesterday.getActualDurationMinutes()).isEqualTo(102);
		assertThat(yesterday.getActualDistanceKm()).isEqualTo(51.0);
		assertThat(yesterday.getActualTss()).isEqualTo(78);
		assertThat(yesterday.getRating()).as("Bewertung bleibt beim Nutzer").isEqualTo(6);
	}

	@Test
	void usesTheLongestRideOfTheDay() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				ride("kurz", TODAY, 30, 20), ride("lang", TODAY, 90, 65)));

		service.importRecent(1L);

		assertThat(today.getActivityId()).isEqualTo("lang");
	}

	@Test
	void ignoresRunsAndStravaPlaceholders() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				new IntervalsActivity("r1", TODAY + "T07:00:00", "Run", 1800, 5000.0, 30, "GARMIN"),
				new IntervalsActivity("s1", null, null, null, null, null, "STRAVA")));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.matched()).isZero();
		assertThat(today.isCompleted()).isFalse();
	}

	@Test
	void daysWithoutRideStayOpen() {
		PlannedWorkout open = planned(TODAY.minusDays(2));
		givenPlan(open);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(ride("i1", TODAY.minusDays(1), 60, 40)));

		service.importRecent(1L);

		assertThat(open.isCompleted()).isFalse();
		assertThat(open.getActivityId()).isNull();
	}

	@Test
	void doesNothingWithoutConnection() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());

		assertThat(service.importRecent(1L)).isEmpty();
		verify(client, never()).listActivities(any(), any(), any());
	}

}
