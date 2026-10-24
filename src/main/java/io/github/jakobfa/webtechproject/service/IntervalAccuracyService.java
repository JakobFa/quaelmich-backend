package io.github.jakobfa.webtechproject.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.integration.ActivityStream;
import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsConnectionService;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsIcuClient;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;

/** Holt die Sekundenwerte der zugeordneten Fahrt und bewertet, wie genau die Intervalle getroffen wurden */
@Service
public class IntervalAccuracyService {

	private static final Logger log = LoggerFactory.getLogger(IntervalAccuracyService.class);

	private final PlannedWorkoutService plannedWorkoutService;
	private final WorkoutLibrary library;
	private final StravaService strava;
	private final IntervalsConnectionService intervalsConnections;
	private final IntervalsIcuClient intervalsClient;

	public IntervalAccuracyService(PlannedWorkoutService plannedWorkoutService, WorkoutLibrary library,
			StravaService strava, IntervalsConnectionService intervalsConnections, IntervalsIcuClient intervalsClient) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.library = library;
		this.strava = strava;
		this.intervalsConnections = intervalsConnections;
		this.intervalsClient = intervalsClient;
	}

	/** Leer ohne zugeordnete Fahrt, ohne passende Daten oder bei Workouts ohne Intervalle */
	public Optional<IntervalAccuracy.Result> accuracy(Long plannedId, Long userId) {
		return plannedWorkoutService.findOwned(plannedId, userId)
				.filter(planned -> planned.getActivityId() != null)
				.flatMap(planned -> {
					Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes())
							.orElseThrow();
					if (IntervalAccuracy.blocksOf(workout).isEmpty()) {
						return Optional.empty();
					}
					return streamOf(planned, userId).flatMap(stream -> evaluate(workout, stream,
							planned.getPlan().getAthlete()));
				});
	}

	private static Optional<IntervalAccuracy.Result> evaluate(Workout workout, ActivityStream stream, Athlete athlete) {
		if (athlete.isHeartRateMode() && athlete.getHeartRateTargets() != null) {
			return IntervalAccuracy.byHeartRate(workout, stream, athlete.getHeartRateTargets());
		}
		if (athlete.getFtp() != null && stream.hasWatts()) {
			return IntervalAccuracy.byPower(workout, stream, athlete.getFtp());
		}
		// kein Wattmesser, aber Puls bekannt: dann eben nach Puls
		return athlete.getHeartRateTargets() == null ? Optional.empty()
				: IntervalAccuracy.byHeartRate(workout, stream, athlete.getHeartRateTargets());
	}

	private Optional<ActivityStream> streamOf(PlannedWorkout planned, Long userId) {
		String id = planned.getActivityId();
		if (RecordedActivity.isFromStrava(id)) {
			return strava.stream(userId, Long.parseLong(id.substring(RecordedActivity.STRAVA_PREFIX.length())));
		}
		return intervalsConnections.credentialsOf(userId).flatMap(credentials -> {
			try {
				List<IntervalsIcuClient.Stream> streams = intervalsClient.streams(credentials, id);
				return Optional.of(ActivityStream.of(data(streams, "time"), data(streams, "watts"),
						data(streams, "heartrate")));
			} catch (RestClientException e) {
				log.warn("intervals.icu-Datenstrom nicht abrufbar: {}", e.getMessage());
				return Optional.empty();
			}
		});
	}

	private static List<Double> data(List<IntervalsIcuClient.Stream> streams, String type) {
		return streams.stream().filter(stream -> type.equals(stream.type())).findFirst()
				.map(IntervalsIcuClient.Stream::data).orElse(null);
	}

}
