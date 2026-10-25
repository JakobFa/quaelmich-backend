package io.github.jakobfa.webtechproject.service;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;

/**
 * Schreibt das geplante Training in die Beschreibung der Strava-Fahrt – aber nur, wenn der Nutzer es
 * eingeschaltet hat und bestätigt ist, dass die Fahrt das Training war. Pendel- und Zusatzfahrten bleiben,
 * wie sie sind. Läuft im Hintergrund, damit Abhaken und Bewerten nicht auf Strava warten.
 */
@Service
public class StravaDescriptionService {

	private final PlannedWorkoutService plannedWorkoutService;
	private final WorkoutLibrary library;
	private final StravaService strava;
	private final IntervalAccuracyService intervalAccuracy;

	/** Eine Fahrt nicht gleichzeitig zweimal bearbeiten (schnelles Klicken bei der Bewertung) */
	private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

	public StravaDescriptionService(PlannedWorkoutService plannedWorkoutService, WorkoutLibrary library,
			StravaService strava, IntervalAccuracyService intervalAccuracy) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.library = library;
		this.strava = strava;
		this.intervalAccuracy = intervalAccuracy;
	}

	/** Nach Bestätigen, Zuordnen, Abhaken oder Bewerten: Block schreiben, aktualisieren oder entfernen */
	@Async
	@Transactional
	public void refresh(Long userId, Long plannedId) {
		if (!strava.writesDescriptions(userId)) {
			return;
		}
		plannedWorkoutService.findOwned(plannedId, userId)
				.filter(planned -> RecordedActivity.isFromStrava(planned.getActivityId()))
				.ifPresent(planned -> {
					long activityId = stravaId(planned.getActivityId());
					String block = isConfirmedTraining(planned) ? blockFor(planned, userId) : null;
					edit(userId, activityId, block);
				});
	}

	/** Die Fahrt war doch nicht das Training ("Nein") – Block wieder entfernen */
	@Async
	@Transactional
	public void remove(Long userId, String activityId) {
		if (RecordedActivity.isFromStrava(activityId) && strava.writesDescriptions(userId)) {
			edit(userId, stravaId(activityId), null);
		}
	}

	private void edit(Long userId, long activityId, String block) {
		synchronized (locks.computeIfAbsent(activityId, id -> new Object())) {
			strava.editDescription(userId, activityId, (description, commute) -> {
				if (commute && block != null) {
					return null; // Pendelfahrt: nichts reinschreiben
				}
				return StravaDescription.merge(description, block);
			});
		}
	}

	static boolean isConfirmedTraining(PlannedWorkout planned) {
		return planned.isCompleted() && Boolean.TRUE.equals(planned.getActivityConfirmed());
	}

	private String blockFor(PlannedWorkout planned, Long userId) {
		Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
		Athlete athlete = planned.getPlan().getAthlete();
		int week = (int) (ChronoUnit.DAYS.between(planned.getPlan().getStartDate(), planned.getDate()) / 7) + 1;
		var accuracy = intervalAccuracy.accuracy(planned.getId(), userId);
		return StravaDescription.block(new StravaDescription.Content(workout, planned.getPhase(), Math.max(week, 1),
				athlete.isHeartRateMode(), planned.getActualDurationMinutes(), planned.getActualTss(),
				accuracy.map(IntervalAccuracy.Result::score).orElse(null),
				accuracy.map(IntervalAccuracy.Result::uncertain).orElse(false), planned.getRating()));
	}

	private static long stravaId(String activityId) {
		return Long.parseLong(activityId.substring(RecordedActivity.STRAVA_PREFIX.length()));
	}

}
