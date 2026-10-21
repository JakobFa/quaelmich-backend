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

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
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
	private final ExtraRideRepository extraRides = mock(ExtraRideRepository.class);
	private final StravaService strava = mock(StravaService.class);
	private final WorkoutLibrary library = new WorkoutLibrary();
	private final ActivityImportService service = new ActivityImportService(connectionService, client,
			strava, athleteService, repository, extraRides, new PlanAdaptationService(library), library, CLOCK);

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
	void runsCountAsExtraLoadButNeverTickOffARideWorkout() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				new IntervalsActivity("r1", TODAY + "T07:00:00", "Run", 1800, 5000.0, 30, "GARMIN"),
				new IntervalsActivity("s1", null, null, null, null, null, "STRAVA")));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.matched()).isZero();
		assertThat(today.isCompleted()).isFalse();
		assertThat(result.extra()).as("Lauf als Zusatzeinheit, Strava-Platzhalter nicht").isEqualTo(1);
		var saved = org.mockito.ArgumentCaptor.forClass(ExtraRide.class);
		verify(extraRides).save(saved.capture());
		assertThat(saved.getValue().getSport()).isEqualTo(Sport.RUN);
		assertThat(saved.getValue().getTss()).as("Trainingslast von intervals.icu").isEqualTo(30);
	}

	@Test
	void hardRunMakesTheNextHardRideSessionEasier() {
		PlannedWorkout tomorrow = planned(TODAY.plusDays(1)); // Sweet Spot 3 × 12
		givenPlan(tomorrow);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				new IntervalsActivity("r2", TODAY + "T07:00:00", "Run", 5400, 15000.0, 95, "GARMIN")));

		service.importRecent(1L);

		assertThat(tomorrow.getWorkoutId()).isEqualTo(1L); // ≥ 90 TSS → Ausrollen
		assertThat(tomorrow.getAdjustmentNote()).contains("95 TSS beim Laufen");
	}

	@Test
	void swimWithoutLoadIsEstimatedFromDuration() {
		givenPlan();
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				new IntervalsActivity("w1", TODAY + "T06:30:00", "Swim", 3600, 2500.0, null, "GARMIN")));

		service.importRecent(1L);

		var saved = org.mockito.ArgumentCaptor.forClass(ExtraRide.class);
		verify(extraRides).save(saved.capture());
		assertThat(saved.getValue().getSport()).isEqualTo(Sport.SWIM);
		assertThat(saved.getValue().getTss()).isEqualTo(45);
		assertThat(saved.getValue().isTssFromDuration()).isTrue();
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
	void rideOnARestDayBecomesAnExtraRide() {
		givenPlan();
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(ride("i7", TODAY, 75, 40)));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.extra()).isEqualTo(1);
		var saved = org.mockito.ArgumentCaptor.forClass(ExtraRide.class);
		verify(extraRides).save(saved.capture());
		assertThat(saved.getValue().getActivityId()).isEqualTo("i7");
		assertThat(saved.getValue().getDate()).isEqualTo(TODAY);
		assertThat(saved.getValue().getTss()).isEqualTo(40);
		assertThat(saved.getValue().assessment()).as("lockere Runde").isNull();
	}

	@Test
	void secondRideOnAPlannedDayIsAlsoAnExtraRide() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(
				ride("kurz", TODAY, 30, 20), ride("lang", TODAY, 90, 65)));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(today.getActivityId()).isEqualTo("lang");
		assertThat(result.extra()).isEqualTo(1);
	}

	@Test
	void hardExtraRideMakesTheNextHardSessionEasier() {
		PlannedWorkout tomorrow = planned(TODAY.plusDays(1)); // Sweet Spot 3 × 12
		givenPlan(tomorrow);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(ride("i8", TODAY, 150, 110)));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.adjusted()).isEqualTo(1);
		assertThat(tomorrow.getWorkoutId()).isEqualTo(1L); // ≥ 90 TSS → Ausrollen
		assertThat(tomorrow.getAdjustmentNote()).contains("zusätzlich 110 TSS");
	}

	@Test
	void knownExtraRideIsUpdatedNotDuplicatedAndAdaptsOnlyOnce() {
		PlannedWorkout tomorrow = planned(TODAY.plusDays(1));
		givenPlan(tomorrow);
		ExtraRide known = new ExtraRide(new Athlete(), "i8");
		known.setDate(TODAY);
		known.setAdaptationApplied(true);
		when(extraRides.findByAthleteIdAndDateBetween(eq(7L), any(), any())).thenReturn(List.of(known));
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(ride("i8", TODAY, 150, 110)));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.extra()).isZero();
		assertThat(known.assessment()).isEqualTo(Assessment.MUCH_HARDER);
		assertThat(tomorrow.getAdjustmentNote()).isNull();
		verify(extraRides).save(known);
	}

	@Test
	void extraRideDeletedInIntervalsIsRemoved() {
		givenPlan();
		ExtraRide gone = new ExtraRide(new Athlete(), "weg");
		when(extraRides.findByAthleteIdAndDateBetween(eq(7L), any(), any())).thenReturn(List.of(gone));
		when(client.listActivities(any(), any(), any())).thenReturn(List.of());

		service.importRecent(1L);

		verify(extraRides).deleteAll(List.of(gone));
	}

	private static RecordedActivity stravaRide(String id, LocalDate date, String time, int minutes) {
		return new RecordedActivity("strava-" + id, Sport.RIDE, java.time.LocalDateTime.parse(date + "T" + time),
				minutes * 60, minutes * 400.0, null, false, false, false);
	}

	private void givenStrava(RecordedActivity... rides) {
		when(strava.isConnected(1L)).thenReturn(true);
		when(strava.recentRides(eq(1L), any(), any(), any(), any())).thenReturn(Optional.of(List.of(rides)));
	}

	@Test
	void stravaOnlyUserGetsRidesToo() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		givenStrava(stravaRide("5", TODAY, "07:30:00", 60));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.matched()).isEqualTo(1);
		assertThat(today.getActivityId()).isEqualTo("strava-5");
		assertThat(today.getActualTss()).isNull();
		verify(client, never()).listActivities(any(), any(), any());
	}

	@Test
	void sameRideFromBothSourcesCountsOnceAndIntervalsWins() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(client.listActivities(any(), any(), any())).thenReturn(List.of(ride("i1", TODAY, 60, 45)));
		// Wahoo lädt auch zu Strava: Start 3 Minuten versetzt, gleiche Fahrt
		givenStrava(stravaRide("6", TODAY, "17:03:00", 61), stravaRide("7", TODAY, "07:10:00", 25));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.activities()).as("Pendelfahrt + Wahoo-Fahrt").isEqualTo(2);
		assertThat(today.getActivityId()).isEqualTo("i1");
		assertThat(result.extra()).isEqualTo(1);
	}

	@Test
	void stravaOutageDoesNotDeleteStravaRides() {
		givenPlan();
		ExtraRide commute = new ExtraRide(new Athlete(), "strava-8");
		when(extraRides.findByAthleteIdAndDateBetween(eq(7L), any(), any())).thenReturn(List.of(commute));
		when(client.listActivities(any(), any(), any())).thenReturn(List.of());
		when(strava.isConnected(1L)).thenReturn(true);
		when(strava.recentRides(eq(1L), any(), any(), any(), any())).thenReturn(Optional.empty());

		service.importRecent(1L);

		verify(extraRides).deleteAll(List.of());
	}

	@Test
	void sameRideAgainUpdatesValuesButKeepsTheConfirmation() {
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		givenStrava(stravaRide("5", TODAY, "07:30:00", 60));
		service.importRecent(1L);
		today.setActivityConfirmed(true);

		// Schwellenpuls nachgetragen → jetzt mit geschätztem TSS
		when(strava.recentRides(eq(1L), any(), any(), any(), any())).thenReturn(Optional.of(List.of(
				new RecordedActivity("strava-5", Sport.RIDE, java.time.LocalDateTime.parse(TODAY + "T07:30:00"), 3600,
						12000.0, 32, true, false, false))));
		var result = service.importRecent(1L).orElseThrow();

		assertThat(result.matched()).isZero();
		assertThat(today.getActualTss()).isEqualTo(32);
		assertThat(today.isActualTssFromHeartRate()).isTrue();
		assertThat(today.getActivityConfirmed()).isTrue();
	}

	@Test
	void commuteNeverTicksOffAWorkout() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		PlannedWorkout today = planned(TODAY);
		givenPlan(today);
		givenStrava(new RecordedActivity("strava-9", Sport.RIDE, java.time.LocalDateTime.parse(TODAY + "T07:30:00"),
				3600, 20000.0, 55, false, false, true));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(today.getActivityId()).isNull();
		assertThat(result.extra()).isEqualTo(1);
	}

	@Test
	void easyRideDoesNotCountAsAHardWorkout() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		PlannedWorkout today = planned(TODAY); // Sweet Spot 3 × 12, 71 min
		givenPlan(today);
		// lang genug, aber viel zu locker (≈25 statt ≈70 TSS)
		givenStrava(new RecordedActivity("strava-10", Sport.RIDE, java.time.LocalDateTime.parse(TODAY + "T07:30:00"),
				3300, 20000.0, 25, false, false, false));

		service.importRecent(1L);

		assertThat(today.getActivityId()).isNull();
		assertThat(today.isCompleted()).isFalse();
	}

	@Test
	void earlierAutomaticMatchThatNoLongerFitsIsReleased() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		PlannedWorkout today = planned(TODAY);
		today.setActivityId("strava-11");
		today.setCompleted(true);
		today.setActualTss(25);
		givenPlan(today);
		givenStrava(new RecordedActivity("strava-11", Sport.RIDE, java.time.LocalDateTime.parse(TODAY + "T07:30:00"),
				3300, 20000.0, 25, false, false, false));

		var result = service.importRecent(1L).orElseThrow();

		assertThat(today.getActivityId()).isNull();
		assertThat(today.isCompleted()).isFalse();
		assertThat(result.extra()).as("wird zur Zusatzfahrt").isEqualTo(1);
	}

	@Test
	void confirmedMatchStaysEvenIfItDoesNotFit() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());
		PlannedWorkout today = planned(TODAY);
		today.setActivityId("strava-12");
		today.setCompleted(true);
		today.setActivityConfirmed(true);
		givenPlan(today);
		givenStrava(new RecordedActivity("strava-12", Sport.RIDE, java.time.LocalDateTime.parse(TODAY + "T07:30:00"),
				1800, 10000.0, 20, false, false, true));

		service.importRecent(1L);

		assertThat(today.getActivityId()).isEqualTo("strava-12");
		assertThat(today.isCompleted()).isTrue();
	}

	@Test
	void doesNothingWithoutConnection() {
		when(connectionService.credentialsOf(1L)).thenReturn(Optional.empty());

		assertThat(service.importRecent(1L)).isEmpty();
		verify(client, never()).listActivities(any(), any(), any());
	}

}
