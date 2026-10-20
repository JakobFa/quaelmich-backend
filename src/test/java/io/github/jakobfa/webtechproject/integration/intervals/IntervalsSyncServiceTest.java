package io.github.jakobfa.webtechproject.integration.intervals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.garmin.fit.Decode;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

class IntervalsSyncServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC);

	private final IntervalsIcuClient client = mock(IntervalsIcuClient.class);
	private final TrainingPlanService planService = mock(TrainingPlanService.class);
	private final WorkoutLibrary library = new WorkoutLibrary();
	private final IntervalsSyncService service = new IntervalsSyncService(
			new IntervalsProperties("https://intervals.icu", "geheim", "0", 7L),
			client, planService, library, new FitWorkoutExporter(CLOCK), CLOCK);

	private TrainingPlan planWith(LocalDate... dates) {
		TrainingPlan plan = new TrainingPlan(new Athlete(), TODAY.minusDays(7));
		for (LocalDate date : dates) {
			plan.addWorkout(new PlannedWorkout(date, library.findById(5L).orElseThrow(), null, Phase.BASE));
		}
		return plan;
	}

	@SuppressWarnings("unchecked")
	private List<IntervalsEvent> sentEvents() {
		ArgumentCaptor<List<IntervalsEvent>> captor = ArgumentCaptor.forClass(List.class);
		verify(client).upsertEvents(captor.capture());
		return captor.getValue();
	}

	@Test
	void onlyTheConfiguredProfileMaySync() {
		assertThat(service.isAvailableFor(7L)).isTrue();
		assertThat(service.isAvailableFor(8L)).isFalse();
	}

	@Test
	void uploadsOnlyWorkoutsOfTheNextDays() {
		when(planService.findByAthlete(7L)).thenReturn(Optional.of(
				planWith(TODAY.minusDays(1), TODAY, TODAY.plusDays(13), TODAY.plusDays(14))));

		var result = service.syncUpcoming(7L, 14).orElseThrow();

		assertThat(result.uploaded()).isEqualTo(2);
		assertThat(result.to()).isEqualTo(TODAY.plusDays(13));
		assertThat(sentEvents()).extracting(IntervalsEvent::externalId)
				.containsExactly("quaelmich-2026-10-21", "quaelmich-2026-11-03");
	}

	@Test
	void sendsAValidFitFileAsBase64() {
		when(planService.findByAthlete(7L)).thenReturn(Optional.of(planWith(TODAY)));

		service.syncUpcoming(7L, 14);

		IntervalsEvent event = sentEvents().getFirst();
		assertThat(event.category()).isEqualTo("WORKOUT");
		assertThat(event.type()).isEqualTo("Ride");
		assertThat(event.name()).isEqualTo("Sweet Spot 3 × 12");
		assertThat(event.startDateLocal()).isEqualTo("2026-10-21T00:00:00");
		byte[] fit = Base64.getDecoder().decode(event.fileContentsBase64());
		assertThat(new Decode().checkFileIntegrity(new ByteArrayInputStream(fit))).isTrue();
	}

	@Test
	void doesNotCallIntervalsWhenNothingIsPlanned() {
		when(planService.findByAthlete(7L)).thenReturn(Optional.of(planWith(TODAY.plusDays(30))));

		assertThat(service.syncUpcoming(7L, 14).orElseThrow().uploaded()).isZero();
		verify(client, never()).upsertEvents(any());
	}

	@Test
	void isEmptyWithoutPlan() {
		when(planService.findByAthlete(7L)).thenReturn(Optional.empty());

		assertThat(service.syncUpcoming(7L, 14)).isEmpty();
	}

}
