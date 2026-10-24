package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import io.github.jakobfa.webtechproject.integration.RecordedActivity;
import io.github.jakobfa.webtechproject.integration.strava.StravaService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.FtpSuggestions;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Holt gefahrene Einheiten aus intervals.icu (z. B. vom Wahoo oder Garmin) und Strava (z. B. von der
 * Apple Watch) und verknüpft sie mit dem Plan:
 * Gibt es an einem Tag mit geplantem Workout eine Radfahrt, die dazu passt, wird das Workout abgehakt und die
 * tatsächlichen Werte werden gespeichert. Bei mehreren passenden Fahrten zählt die längste.
 * Alle übrigen Fahrten (z. B. am Ruhetag) werden als Zusatzfahrten gespeichert.
 * Kommt dieselbe Fahrt aus beiden Quellen, gewinnt intervals.icu (dort gibt es den TSS).
 * Die Bewertung der Anstrengung bleibt unverändert – die kennt nur der Nutzer.
 */
@Service
public class ActivityImportService {

	private static final Logger log = LoggerFactory.getLogger(ActivityImportService.class);

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
	private final WorkoutLibrary library;
	private final Clock clock;

	public ActivityImportService(IntervalsConnectionService connectionService, IntervalsIcuClient client,
			StravaService stravaService, AthleteService athleteService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository,
			PlanAdaptationService adaptation, WorkoutLibrary library, Clock clock) {
		this.connectionService = connectionService;
		this.client = client;
		this.stravaService = stravaService;
		this.athleteService = athleteService;
		this.plannedWorkoutRepository = plannedWorkoutRepository;
		this.extraRideRepository = extraRideRepository;
		this.adaptation = adaptation;
		this.library = library;
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
		// Plan-Seite zweimal schnell geöffnet → zwei Importe; ohne Sperre würde jede Fahrt doppelt gespeichert
		athleteService.lockForImport(athleteId);

		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(DAYS_BACK);
		// Fällt eine Quelle aus, kommen die Fahrten der anderen trotzdem an
		Optional<List<RecordedActivity>> intervalsAnswer = credentials.flatMap(c -> intervalsRides(c, from, today));
		List<RecordedActivity> intervalsRides = intervalsAnswer.orElse(List.of());
		boolean intervalsAnswered = intervalsAnswer.isPresent();
		Optional<List<RecordedActivity>> stravaRides = strava
				? stravaService.recentRides(userId, from, today, athlete.get().getFtp(),
						athlete.get().getEffectiveThresholdHeartRate())
				: Optional.empty();
		List<RecordedActivity> rides = merge(intervalsRides, stravaRides.orElse(List.of()));

		// sehr sichere Hinweise, dass die eingetragene FTP zu niedrig ist
		FtpSuggestions.evaluate(athlete.get(), rides);

		Map<LocalDate, List<RecordedActivity>> ridesPerDay = rides.stream().filter(RecordedActivity::isRide)
				.collect(Collectors.groupingBy(RecordedActivity::date));
		Map<String, RecordedActivity> byId = rides.stream()
				.collect(Collectors.toMap(RecordedActivity::id, Function.identity(), (a, b) -> a));

		// Workouts bis übermorgen laden: die vergangenen zum Zuordnen, die kommenden zum Anpassen
		List<PlannedWorkout> window = plannedWorkoutRepository.findByPlanAthleteIdAndDateBetween(athleteId, from,
				today.plusDays(2));

		int matched = 0;
		int adjusted = 0;
		for (PlannedWorkout planned : window) {
			String linkedId = planned.getActivityId();
			// nur lösen, wenn die Quelle der Fahrt gerade geantwortet hat – sonst wäre die Fahrt kurz unsichtbar
			boolean sourceAnswered = RecordedActivity.isFromStrava(linkedId) ? stravaRides.isPresent()
					: intervalsAnswered;
			// früher hieß "Nein, anderes Training": Fahrt bleibt am Workout hängen → jetzt als Ablehnung merken
			// und lösen, damit die Fahrt unten zur Zusatzfahrt wird
			if (linkedId != null && Boolean.FALSE.equals(planned.getActivityConfirmed()) && sourceAnswered) {
				planned.setRejectedActivityId(linkedId);
				planned.clearActivity();
				planned.setCompleted(false);
				continue;
			}
			// vom Nutzer bestätigt ("Ja, das war's" oder selbst zugeordnet) → Zuordnung bleibt
			if (linkedId != null && planned.getActivityConfirmed() != null) {
				if (byId.containsKey(linkedId)) {
					updateValues(planned, byId.get(linkedId));
				}
				continue;
			}
			// die längste Fahrt des Tages, die zum Workout passt
			Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
			RecordedActivity ride = ridesPerDay.getOrDefault(planned.getDate(), List.of()).stream()
					.filter(candidate -> !candidate.id().equals(planned.getRejectedActivityId()))
					.filter(candidate -> fitsWorkout(candidate, workout))
					.max(Comparator.comparingInt(RecordedActivity::movingSeconds))
					.orElse(null);
			if (ride == null) {
				// früher automatisch zugeordnet, passt aber nicht (mehr) → wieder freigeben, wird Zusatzfahrt
				if (linkedId != null && sourceAnswered) {
					planned.clearActivity();
				}
				continue;
			}
			if (ride.id().equals(linkedId)) {
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
		Map<String, ExtraRide> known = new HashMap<>();
		List<ExtraRide> duplicates = new ArrayList<>();
		for (ExtraRide stored : extraRideRepository.findByAthleteIdAndDateBetween(athleteId, from, today)) {
			if (known.putIfAbsent(stored.getActivityId(), stored) != null) {
				duplicates.add(stored); // dieselbe Fahrt doppelt gespeichert (früher bei gleichzeitigen Importen)
			}
		}
		if (!duplicates.isEmpty()) {
			extraRideRepository.deleteAll(duplicates);
		}
		int extra = 0;
		for (RecordedActivity ride : rides) {
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
				.filter(ride -> RecordedActivity.isFromStrava(ride.getActivityId())
						? stravaRides.isPresent()
						: intervalsAnswered)
				.toList());

		return Optional.of(new ImportResult(rides.size(), matched, extra, adjusted));
	}

	private Optional<List<RecordedActivity>> intervalsRides(IntervalsCredentials credentials, LocalDate from,
			LocalDate to) {
		try {
			return Optional.of(client.listActivities(credentials, from, to).stream()
					.filter(IntervalsActivity::isUsable)
					.map(IntervalsActivity::toRecordedActivity)
					.toList());
		} catch (RestClientException e) {
			log.warn("intervals.icu-Fahrten nicht abrufbar: {}", e.getMessage());
			return Optional.empty();
		}
	}

	/** Anteil von Dauer bzw. TSS des Workouts, den eine Fahrt mindestens haben muss, um es abzuhaken */
	/** Erlaubte Abweichung von der geplanten Dauer (80–130 %) */
	static final double MIN_DURATION_SHARE = 0.8;
	static final double MAX_DURATION_SHARE = 1.3;
	/** Erlaubte Abweichung vom geplanten TSS (75–135 %) */
	static final double MIN_TSS_SHARE = 0.75;
	static final double MAX_TSS_SHARE = 1.35;

	private static final Set<WorkoutType> EASY_TYPES = Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE,
			WorkoutType.LONG_RIDE);

	/**
	 * Ist die Fahrt sehr wahrscheinlich das geplante Workout? Lieber einmal zu wenig automatisch abhaken als
	 * falsch – der Nutzer kann eine Fahrt jederzeit selbst zuordnen.
	 * - Dauer: 80–130 % der geplanten Dauer
	 * - TSS: 75–135 % des geplanten TSS
	 * - ohne TSS (weder Watt noch Puls) nur lockere Workouts, harte lassen sich so nicht prüfen
	 * - Pendelfahrten nie
	 */
	static boolean fitsWorkout(RecordedActivity ride, Workout workout) {
		if (ride.commute()) {
			return false;
		}
		double durationShare = ride.movingSeconds() / (workout.getDurationInMinutes() * 60.0);
		if (durationShare < MIN_DURATION_SHARE || durationShare > MAX_DURATION_SHARE) {
			return false;
		}
		if (ride.tss() == null) {
			return EASY_TYPES.contains(workout.getType());
		}
		double tssShare = (double) ride.tss() / workout.getTss();
		return tssShare >= MIN_TSS_SHARE && tssShare <= MAX_TSS_SHARE;
	}

	/** Strava-Fahrten, die es schon aus intervals.icu gibt, fallen weg */
	static List<RecordedActivity> merge(List<RecordedActivity> intervalsRides, List<RecordedActivity> stravaRides) {
		List<RecordedActivity> merged = new ArrayList<>(intervalsRides);
		stravaRides.stream()
				.filter(strava -> intervalsRides.stream().noneMatch(strava::isSameActivityAs))
				.forEach(merged::add);
		return merged;
	}

	static void apply(ExtraRide extraRide, RecordedActivity ride) {
		extraRide.setDate(ride.date());
		extraRide.setDurationMinutes(ride.movingMinutes());
		extraRide.setDistanceKm(ride.distanceKm());
		extraRide.setTss(ride.tss());
		extraRide.setTssFromHeartRate(ride.tssFromHeartRate());
		extraRide.setTssFromDuration(ride.tssFromDuration());
		extraRide.setSport(ride.sport());
	}

	static void apply(PlannedWorkout planned, RecordedActivity ride) {
		planned.setCompleted(true);
		planned.setSkipReason(null); // doch gefahren
		planned.setActivityId(ride.id());
		planned.setActivityConfirmed(null); // neue Fahrt → neu nachfragen
		planned.setAdaptationApplied(false);
		updateValues(planned, ride);
	}

	/** Ist-Werte übernehmen, ohne Bestätigung und Anpassung zurückzusetzen */
	static void updateValues(PlannedWorkout planned, RecordedActivity ride) {
		planned.setActualDurationMinutes(ride.movingMinutes());
		planned.setActualDistanceKm(ride.distanceKm());
		planned.setActualTss(ride.tss());
		planned.setActualTssFromHeartRate(ride.tssFromHeartRate());
	}

}
