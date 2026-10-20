package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;

/**
 * Holt gefahrene Einheiten aus intervals.icu (z. B. vom Wahoo oder Garmin) und verknüpft sie mit dem Plan:
 * Gibt es an einem Tag mit geplantem Workout eine Radfahrt, wird das Workout abgehakt und die
 * tatsächlichen Werte werden gespeichert. Bei mehreren Fahrten an einem Tag zählt die längste.
 * Die Bewertung der Anstrengung bleibt unverändert – die kennt nur der Nutzer.
 */
@Service
public class ActivityImportService {

	public static final int DAYS_BACK = 14;

	public record ImportResult(int activities, int matched) {
	}

	private final IntervalsConnectionService connectionService;
	private final IntervalsIcuClient client;
	private final AthleteService athleteService;
	private final PlannedWorkoutRepository plannedWorkoutRepository;
	private final Clock clock;

	public ActivityImportService(IntervalsConnectionService connectionService, IntervalsIcuClient client,
			AthleteService athleteService, PlannedWorkoutRepository plannedWorkoutRepository, Clock clock) {
		this.connectionService = connectionService;
		this.client = client;
		this.athleteService = athleteService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.clock = clock;
	}

	/** Leer, wenn der Nutzer nicht verbunden ist oder kein Profil hat. */
	@Transactional
	public Optional<ImportResult> importRecent(Long userId) {
		var credentials = connectionService.credentialsOf(userId);
		var athleteId = athleteService.findByOwner(userId).map(Athlete::getId);
		if (credentials.isEmpty() || athleteId.isEmpty()) {
			return Optional.empty();
		}

		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(DAYS_BACK);
		List<IntervalsActivity> rides = client.listActivities(credentials.get(), from, today).stream()
				.filter(IntervalsActivity::isUsableRide)
				.toList();

		// pro Tag die längste Fahrt
		Map<LocalDate, IntervalsActivity> longestRidePerDay = rides.stream().collect(Collectors.toMap(
				IntervalsActivity::date, Function.identity(),
				(a, b) -> a.movingTimeSeconds() >= b.movingTimeSeconds() ? a : b));

		int matched = 0;
		for (PlannedWorkout planned : plannedWorkoutRepository.findByPlanAthleteIdAndDateBetween(athleteId.get(), from,
				today)) {
			IntervalsActivity ride = longestRidePerDay.get(planned.getDate());
			if (ride != null && !ride.id().equals(planned.getActivityId())) {
				apply(planned, ride);
				matched++;
			}
		}
		return Optional.of(new ImportResult(rides.size(), matched));
	}

	static void apply(PlannedWorkout planned, IntervalsActivity ride) {
		planned.setCompleted(true);
		planned.setActivityId(ride.id());
		planned.setActualDurationMinutes(Math.round(ride.movingTimeSeconds() / 60f));
		planned.setActualDistanceKm(ride.distance() == null ? null : Math.round(ride.distance() / 100.0) / 10.0);
		planned.setActualTss(ride.trainingLoad());
	}

}
