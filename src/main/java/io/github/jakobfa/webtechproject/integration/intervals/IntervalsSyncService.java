package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.service.FitWorkoutExporter;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Überträgt die anstehenden Workouts eines Plans als .fit-Dateien in den intervals.icu-Kalender.
 * Von dort kann intervals.icu sie an Wahoo bzw. Garmin weitergeben.
 */
@Service
public class IntervalsSyncService {

	public record SyncResult(int uploaded, LocalDate from, LocalDate to) {
	}

	private final IntervalsIcuClient client;
	private final TrainingPlanService planService;
	private final WorkoutLibrary library;
	private final FitWorkoutExporter fitExporter;
	private final Clock clock;

	public IntervalsSyncService(IntervalsIcuClient client, TrainingPlanService planService, WorkoutLibrary library,
			FitWorkoutExporter fitExporter, Clock clock) {
		this.client = client;
		this.planService = planService;
		this.library = library;
		this.fitExporter = fitExporter;
		this.clock = clock;
	}

	/** Schreibt die Workouts der nächsten Tage in den Kalender des Nutzers. Leer, wenn es (noch) keinen Plan gibt. */
	public Optional<SyncResult> syncUpcoming(IntervalsCredentials credentials, Long athleteId, int days) {
		LocalDate from = LocalDate.now(clock);
		LocalDate to = from.plusDays(days - 1L);

		return planService.findByAthlete(athleteId).map(plan -> {
			List<IntervalsEvent> events = plan.getWorkouts().stream()
					.filter(planned -> !planned.getDate().isBefore(from) && !planned.getDate().isAfter(to))
					.map(this::toEvent)
					.toList();
			if (!events.isEmpty()) {
				client.upsertEvents(credentials, events);
			}
			return new SyncResult(events.size(), from, to);
		});
	}

	IntervalsEvent toEvent(PlannedWorkout planned) {
		Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		String fit = Base64.getEncoder().encodeToString(fitExporter.export(workout));
		return new IntervalsEvent(
				"WORKOUT",
				planned.getDate() + "T00:00:00",
				"Ride",
				workout.getTitle(),
				workout.getDescription(),
				FitWorkoutExporter.fileName(workout),
				fit,
				// Ein Workout pro Tag: gleiche ID → intervals.icu ersetzt das Workout bei erneutem Senden
				"quaelmich-" + planned.getDate());
	}

}
