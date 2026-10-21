package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.integration.RecordedRide;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;

/**
 * Holt gefahrene Einheiten aus intervals.icu (z. B. vom Wahoo oder Garmin) und Strava (z. B. von der
 * Apple Watch) und verknüpft sie mit dem Plan:
 * Gibt es an einem Tag mit geplantem Workout eine Radfahrt, wird das Workout abgehakt und die
 * tatsächlichen Werte werden gespeichert. Bei mehreren Fahrten an einem Tag zählt die längste.
 * Alle übrigen Fahrten (z. B. am Ruhetag) werden als Zusatzfahrten gespeichert.
 * Kommt dieselbe Fahrt aus beiden Quellen, gewinnt intervals.icu (dort gibt es den TSS).
 * Die Bewertung der Anstrengung bleibt unverändert – die kennt nur der Nutzer.
 */
@Service
public class ActivityImportService {

	public static final int DAYS_BACK = 14;

	public record ImportResult(int activities, int matched, int extra, int adjusted) {
	}

	private final IntervalsConnectionService connectionService;
	private final IntervalsIcuClient client;
	private final StravaService stravaService;
	private final AthleteService athleteService;
	private final PlannedWorkoutRepository plannedWorkoutRepository;
	private final ExtraRideRepository extraRideRepository;
	private final PlanAdaptationService adaptation;
	private final Clock clock;

	public ActivityImportService(IntervalsConnectionService connectionService, IntervalsIcuClient client,
			StravaService stravaService, AthleteService athleteService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository,
			PlanAdaptationService adaptation, Clock clock) {
		this.connectionService = connectionService;
		this.client = client;
		this.stravaService = stravaService;
		this.athleteService = athleteService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.extraRideRepository = extraRideRepository;
		this.adaptation = adaptation;
		this.clock = clock;
	}

	/** Leer, wenn der Nutzer weder intervals.icu noch Strava verbunden hat oder kein Profil hat. */
	@Transactional
	public Optional<ImportResult> importRecent(Long userId) {
		var athlete = athleteService.findByOwner(userId);
		var credentials = connectionService.credentialsOf(userId);
		boolean strava = stravaService.isConnected(userId);
		if (athlete.isEmpty() || (credentials.isEmpty() && !strava)) {
			return Optional.empty();
		}
		Long athleteId = athlete.get().getId();

		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(DAYS_BACK);
		// intervals.icu-Fehler werden weitergereicht, Strava-Fehler ergeben nur eine leere Liste
		List<RecordedRide> intervalsRides = credentials
				.map(c -> client.listActivities(c, from, today).stream()
						.filter(IntervalsActivity::isUsableRide)
						.map(IntervalsActivity::toRecordedRide)
						.toList())
				.orElse(List.of());
		Optional<List<RecordedRide>> stravaRides = strava
				? stravaService.recentRides(userId, from, today, athlete.get().getFtp(),
						athlete.get().getThresholdHeartRate())
				: Optional.empty();
		List<RecordedRide> rides = merge(intervalsRides, stravaRides.orElse(List.of()));

		// pro Tag die längste Fahrt
		Map<LocalDate, RecordedRide> longestRidePerDay = rides.stream().collect(Collectors.toMap(
				RecordedRide::date, Function.identity(),
				(a, b) -> a.movingSeconds() >= b.movingSeconds() ? a : b));

		// Workouts bis übermorgen laden: die vergangenen zum Zuordnen, die kommenden zum Anpassen
		List<PlannedWorkout> window = plannedWorkoutRepository.findByPlanAthleteIdAndDateBetween(athleteId, from,
				today.plusDays(2));

		int matched = 0;
		int adjusted = 0;
		for (PlannedWorkout planned : window) {
			RecordedRide ride = longestRidePerDay.get(planned.getDate());
			if (ride == null) {
				continue;
			}
			if (ride.id().equals(planned.getActivityId())) {
				updateValues(planned, ride); // z. B. TSS neu geschätzt, nachdem der Schwellenpuls eingetragen wurde
				continue;
			}
			apply(planned, ride);
			matched++;
			if (adaptation.adaptAfter(planned, window).isPresent()) {
				adjusted++;
			}
		}

		// alle Fahrten, die keinem Workout zugeordnet sind, als Zusatzfahrten speichern
		Set<String> linked = window.stream().map(PlannedWorkout::getActivityId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<String, ExtraRide> known = extraRideRepository.findByAthleteIdAndDateBetween(athleteId, from, today)
				.stream().collect(Collectors.toMap(ExtraRide::getActivityId, Function.identity(), (a, b) -> a));
		int extra = 0;
		for (RecordedRide ride : rides) {
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
		// gelöschte (oder inzwischen doppelte) Fahrten entfernen – aber nur aus Quellen, die geantwortet haben
		extraRideRepository.deleteAll(known.values().stream()
				.filter(ride -> RecordedRide.isFromStrava(ride.getActivityId())
						? stravaRides.isPresent()
						: credentials.isPresent())
				.toList());

		return Optional.of(new ImportResult(rides.size(), matched, extra, adjusted));
	}

	/** Strava-Fahrten, die es schon aus intervals.icu gibt, fallen weg */
	static List<RecordedRide> merge(List<RecordedRide> intervalsRides, List<RecordedRide> stravaRides) {
		List<RecordedRide> merged = new ArrayList<>(intervalsRides);
		stravaRides.stream()
				.filter(strava -> intervalsRides.stream().noneMatch(strava::isSameRideAs))
				.forEach(merged::add);
		return merged;
	}

	static void apply(ExtraRide extraRide, RecordedRide ride) {
		extraRide.setDate(ride.date());
		extraRide.setDurationMinutes(ride.movingMinutes());
		extraRide.setDistanceKm(ride.distanceKm());
		extraRide.setTss(ride.tss());
		extraRide.setTssFromHeartRate(ride.tssFromHeartRate());
	}

	static void apply(PlannedWorkout planned, RecordedRide ride) {
		planned.setCompleted(true);
		planned.setActivityId(ride.id());
		planned.setActivityConfirmed(null); // neue Fahrt → neu nachfragen
		planned.setAdaptationApplied(false);
		updateValues(planned, ride);
	}

	/** Ist-Werte übernehmen, ohne Bestätigung und Anpassung zurückzusetzen */
	static void updateValues(PlannedWorkout planned, RecordedRide ride) {
		planned.setActualDurationMinutes(ride.movingMinutes());
		planned.setActualDistanceKm(ride.distanceKm());
		planned.setActualTss(ride.tss());
		planned.setActualTssFromHeartRate(ride.tssFromHeartRate());
	}

}
