package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;

/**
 * Holt gefahrene Einheiten aus intervals.icu (z. B. vom Wahoo oder Garmin) und verknüpft sie mit dem Plan:
 * Gibt es an einem Tag mit geplantem Workout eine Radfahrt, wird das Workout abgehakt und die
 * tatsächlichen Werte werden gespeichert. Bei mehreren Fahrten an einem Tag zählt die längste.
 * Alle übrigen Fahrten (z. B. am Ruhetag) werden als Zusatzfahrten gespeichert.
 * Die Bewertung der Anstrengung bleibt unverändert – die kennt nur der Nutzer.
 */
@Service
public class ActivityImportService {

	public static final int DAYS_BACK = 14;

	public record ImportResult(int activities, int matched, int extra, int adjusted) {
	}

	private final IntervalsConnectionService connectionService;
	private final IntervalsIcuClient client;
	private final AthleteService athleteService;
	private final PlannedWorkoutRepository plannedWorkoutRepository;
	private final ExtraRideRepository extraRideRepository;
	private final PlanAdaptationService adaptation;
	private final Clock clock;

	public ActivityImportService(IntervalsConnectionService connectionService, IntervalsIcuClient client,
			AthleteService athleteService, PlannedWorkoutRepository plannedWorkoutRepository,
			ExtraRideRepository extraRideRepository, PlanAdaptationService adaptation, Clock clock) {
		this.connectionService = connectionService;
		this.client = client;
		this.athleteService = athleteService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.extraRideRepository = extraRideRepository;
		this.adaptation = adaptation;
		this.clock = clock;
	}

	/** Leer, wenn der Nutzer nicht verbunden ist oder kein Profil hat. */
	@Transactional
	public Optional<ImportResult> importRecent(Long userId) {
		var credentials = connectionService.credentialsOf(userId);
		var athlete = athleteService.findByOwner(userId);
		if (credentials.isEmpty() || athlete.isEmpty()) {
			return Optional.empty();
		}
		Long athleteId = athlete.get().getId();

		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(DAYS_BACK);
		List<IntervalsActivity> rides = client.listActivities(credentials.get(), from, today).stream()
				.filter(IntervalsActivity::isUsableRide)
				.toList();

		// pro Tag die längste Fahrt
		Map<LocalDate, IntervalsActivity> longestRidePerDay = rides.stream().collect(Collectors.toMap(
				IntervalsActivity::date, Function.identity(),
				(a, b) -> a.movingTimeSeconds() >= b.movingTimeSeconds() ? a : b));

		// Workouts bis übermorgen laden: die vergangenen zum Zuordnen, die kommenden zum Anpassen
		List<PlannedWorkout> window = plannedWorkoutRepository.findByPlanAthleteIdAndDateBetween(athleteId, from,
				today.plusDays(2));

		int matched = 0;
		int adjusted = 0;
		for (PlannedWorkout planned : window) {
			IntervalsActivity ride = longestRidePerDay.get(planned.getDate());
			if (ride != null && !ride.id().equals(planned.getActivityId())) {
				apply(planned, ride);
				matched++;
				if (adaptation.adaptAfter(planned, window).isPresent()) {
					adjusted++;
				}
			}
		}

		// alle Fahrten, die keinem Workout zugeordnet sind, als Zusatzfahrten speichern
		Set<String> linked = window.stream().map(PlannedWorkout::getActivityId).filter(id -> id != null)
				.collect(Collectors.toSet());
		Map<String, ExtraRide> known = extraRideRepository.findByAthleteIdAndDateBetween(athleteId, from, today)
				.stream().collect(Collectors.toMap(ExtraRide::getActivityId, Function.identity(), (a, b) -> a));
		int extra = 0;
		for (IntervalsActivity ride : rides) {
			ExtraRide existing = known.remove(ride.id());
			if (linked.contains(ride.id())) {
				if (existing != null) {
					extraRideRepository.delete(existing); // gehört inzwischen zu einem Workout
				}
				continue;
			}
			ExtraRide extraRide = existing != null ? existing : new ExtraRide(athlete.get(), ride.id());
			apply(extraRide, ride);
			extraRideRepository.save(extraRide);
			if (existing == null) {
				extra++;
			}
			if (adaptation.adaptAfter(extraRide, window).isPresent()) {
				adjusted++;
			}
		}
		// in intervals.icu gelöschte Fahrten auch hier entfernen
		extraRideRepository.deleteAll(List.copyOf(known.values()));

		return Optional.of(new ImportResult(rides.size(), matched, extra, adjusted));
	}

	static void apply(ExtraRide extraRide, IntervalsActivity ride) {
		extraRide.setDate(ride.date());
		extraRide.setDurationMinutes(minutes(ride));
		extraRide.setDistanceKm(km(ride));
		extraRide.setTss(ride.trainingLoad());
	}

	private static int minutes(IntervalsActivity ride) {
		return Math.round(ride.movingTimeSeconds() / 60f);
	}

	private static Double km(IntervalsActivity ride) {
		return ride.distance() == null ? null : Math.round(ride.distance() / 100.0) / 10.0;
	}

	static void apply(PlannedWorkout planned, IntervalsActivity ride) {
		planned.setCompleted(true);
		planned.setActivityId(ride.id());
		planned.setActivityConfirmed(null); // neue Fahrt → neu nachfragen
		planned.setAdaptationApplied(false);
		planned.setActualDurationMinutes(minutes(ride));
		planned.setActualDistanceKm(km(ride));
		planned.setActualTss(ride.trainingLoad());
	}

}
