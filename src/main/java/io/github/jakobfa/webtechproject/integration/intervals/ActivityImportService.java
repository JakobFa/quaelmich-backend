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
import io.github.jakobfa.webtechproject.service.IntervalAccuracy;
import io.github.jakobfa.webtechproject.service.IntervalAccuracyService;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;
import io.github.jakobfa.webtechproject.service.PlanAdaptationService;
import io.github.jakobfa.webtechproject.service.WorkoutLibrary;

/**
 * Holt gefahrene Einheiten aus intervals.icu (z. B. vom Wahoo oder Garmin) und Strava (z. B. von der
 * Apple Watch) und verknüpft sie mit dem Plan:
 * Automatisch abgehakt wird ein Workout nur von einer Fahrt auf der Rolle, die eng dazu passt – dort stellt der
 * Trainer die Watt ein, die Fahrt entspricht also fast genau dem Plan. Draußen weicht es fast immer ab
 * (Anfahrt, Ampeln, Wind): Solche Fahrten werden Zusatzfahrten, und der Nutzer ordnet sie selbst zu.
 * Bei mehreren passenden Fahrten zählt die längste. Alle übrigen Fahrten werden als Zusatzfahrten gespeichert.
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
	private final IntervalAccuracyService intervalAccuracy;
	private final Clock clock;

	public ActivityImportService(IntervalsConnectionService connectionService, IntervalsIcuClient client,
			StravaService stravaService, AthleteService athleteService,
			PlannedWorkoutRepository plannedWorkoutRepository, ExtraRideRepository extraRideRepository,
			PlanAdaptationService adaptation, WorkoutLibrary library, IntervalAccuracyService intervalAccuracy,
			Clock clock) {
		this.intervalAccuracy = intervalAccuracy;
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
		// Belastung immer mit den eigenen Profilwerten – nicht mit dem, was in intervals.icu eingestellt ist
		Integer ftp = athlete.get().getFtp();
		Integer thresholdHeartRate = athlete.get().getEffectiveThresholdHeartRate();
		Optional<List<RecordedActivity>> intervalsAnswer = credentials
				.flatMap(c -> intervalsRides(c, from, today, ftp, thresholdHeartRate));
		List<RecordedActivity> intervalsRides = intervalsAnswer.orElse(List.of());
		boolean intervalsAnswered = intervalsAnswer.isPresent();
		Optional<List<RecordedActivity>> stravaRides = strava
				? stravaService.recentRides(userId, from, today, athlete.get().getFtp(),
						athlete.get().getEffectiveThresholdHeartRate())
				: Optional.empty();
		// schon bekannte Fahrten (zugeordnet, abgelehnt, Zusatzfahrt) behalten ihre Quelle – sonst würden sie
		// als "neue" Fahrt gelten und den Plan ein zweites Mal anpassen
		Set<String> knownIds = new java.util.HashSet<>();
		plannedWorkoutRepository.findByPlanAthleteIdAndDateBetween(athleteId, from, today).forEach(planned -> {
			knownIds.add(planned.getActivityId());
			knownIds.add(planned.getRejectedActivityId());
		});
		extraRideRepository.findByAthleteIdAndDateBetween(athleteId, from, today)
				.forEach(ride -> knownIds.add(ride.getActivityId()));
		List<RecordedActivity> rides = merge(intervalsRides, stravaRides.orElse(List.of()), knownIds);

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
			Workout workout = library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
			if (WorkoutLibrary.isStrength(workout)) {
				continue; // Kraft hakt man selbst ab – eine Radfahrt ist nie das Krafttraining
			}
			List<RecordedActivity> candidates = ridesPerDay.getOrDefault(planned.getDate(), List.of()).stream()
					.filter(candidate -> !candidate.id().equals(planned.getRejectedActivityId()))
					.toList();
			// schon zugeordnet, aber noch unbeantwortet (auch noch nach den alten, lockeren Regeln): bleibt, solange
			// die Fahrt grob passt – sonst würden bereits abgehakte Workouts plötzlich wieder offen
			RecordedActivity kept = candidates.stream().filter(candidate -> candidate.id().equals(linkedId))
					.filter(candidate -> fitsWorkout(candidate, workout)).findFirst().orElse(null);
			if (kept != null) {
				updateValues(planned, kept); // z. B. TSS neu geschätzt, nachdem der Schwellenpuls eingetragen wurde
				continue;
			}
			if (workout.getType() == WorkoutType.FTP_TEST) {
				// schon als Rampentest erkannt: bleibt (die Sekundenwerte nicht bei jedem Abgleich neu holen)
				RecordedActivity known = candidates.stream().filter(candidate -> candidate.id().equals(linkedId))
						.filter(ActivityImportService::couldBeRampTest).findFirst().orElse(null);
				if (known != null) {
					updateValues(planned, known);
					continue;
				}
				// beim Rampentest hört jeder woanders auf: nicht nach Dauer zuordnen, sondern die Rampe erkennen
				var test = candidates.stream().filter(ActivityImportService::couldBeRampTest)
						.map(candidate -> intervalAccuracy.rampTestOf(candidate.id(), userId)
								.map(result -> Map.entry(candidate, result)))
						.flatMap(Optional::stream).findFirst();
				if (test.isPresent()) {
					apply(planned, test.get().getKey());
					FtpSuggestions.fromRampTest(athlete.get(), test.get().getValue(), planned.getDate());
					matched++;
				} else if (linkedId != null && sourceAnswered) {
					planned.clearActivity();
				}
				continue;
			}
			// neu automatisch zuordnen nur: die längste Rollenfahrt des Tages, die genau zum Workout passt
			RecordedActivity ride = candidates.stream()
					.filter(candidate -> fitsIndoorExactly(candidate, workout))
					.filter(candidate -> intervalsMatch(candidate, workout, userId, athlete.get()))
					.max(Comparator.comparingInt(RecordedActivity::movingSeconds))
					.orElse(null);
			if (ride == null) {
				// früher automatisch zugeordnet, passt aber nicht (mehr) → wieder freigeben, wird Zusatzfahrt
				if (linkedId != null && sourceAnswered) {
					planned.clearActivity();
				}
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
			LocalDate to, Integer ftp, Integer thresholdHeartRate) {
		try {
			return Optional.of(client.listActivities(credentials, from, to).stream()
					.filter(IntervalsActivity::isUsable)
					.map(activity -> activity.toRecordedActivity(ftp, thresholdHeartRate))
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

	/** Rolle: Dauer 90–115 % der geplanten */
	static final double INDOOR_MIN_DURATION_SHARE = 0.9;
	static final double INDOOR_MAX_DURATION_SHARE = 1.15;
	/** Rolle: TSS 85–115 % des geplanten */
	static final double INDOOR_MIN_TSS_SHARE = 0.85;
	static final double INDOOR_MAX_TSS_SHARE = 1.15;
	/** Rolle mit Intervallen: so genau müssen die Intervalle getroffen sein (im ERG-Modus meist über 95) */
	static final int INDOOR_MIN_ACCURACY = 85;

	/**
	 * Darf die Fahrt das Workout automatisch abhaken? Nur auf der Rolle, mit Wattmesser (Watt-TSS) und eng an
	 * Dauer und TSS des Plans. Ob die Intervalle wirklich drin sind, prüft {@link #intervalsMatch} danach.
	 */
	static boolean fitsIndoorExactly(RecordedActivity ride, Workout workout) {
		if (!ride.indoor() || ride.commute() || ride.normalizedPower() == null || ride.tss() == null
				|| ride.tssFromHeartRate() || ride.tssFromDuration()) {
			return false;
		}
		double durationShare = ride.movingSeconds() / (workout.getDurationInMinutes() * 60.0);
		double tssShare = (double) ride.tss() / workout.getTss();
		return durationShare >= INDOOR_MIN_DURATION_SHARE && durationShare <= INDOOR_MAX_DURATION_SHARE
				&& tssShare >= INDOOR_MIN_TSS_SHARE && tssShare <= INDOOR_MAX_TSS_SHARE;
	}

	/** Rampentest: mit Wattmesser, 15–75 min (Aufwärmen + Rampe + Ausfahren) – drinnen oder draußen */
	static boolean couldBeRampTest(RecordedActivity ride) {
		return ride.normalizedPower() != null && !ride.commute() && ride.movingMinutes() >= 15
				&& ride.movingMinutes() <= 75;
	}

	/** Workouts mit Intervallen: Sind sie in den Sekundenwerten klar und genau zu finden? Ohne Intervalle: ja */
	private boolean intervalsMatch(RecordedActivity ride, Workout workout, Long userId, Athlete athlete) {
		if (athlete.getFtp() == null) {
			return false; // ohne FTP ist der Watt-TSS nicht unser eigener – zu unsicher
		}
		if (!IntervalAccuracy.hasIntervals(workout)) {
			return true;
		}
		return intervalAccuracy.powerAccuracyOf(workout, ride.id(), userId, athlete.getFtp())
				.filter(result -> !result.uncertain() && result.score() >= INDOOR_MIN_ACCURACY)
				.isPresent();
	}

	/**
	 * Passt eine schon zugeordnete Fahrt noch grob zum Workout? (Die früheren, lockeren Regeln fürs automatische
	 * Abhaken – gelten nur noch fürs Behalten bestehender Zuordnungen.)
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

	/**
	 * Dieselbe Fahrt aus beiden Quellen zählt einmal – bevorzugt die Strava-Version: Deren TSS rechnet
	 * quaelmich mit der eigenen FTP, und Sekundenwerte und "View on Strava" gibt es nur dort.
	 * Ausnahme: Ist die intervals.icu-Version schon bekannt (zugeordnet, abgelehnt, Zusatzfahrt), bleibt sie.
	 */
	static List<RecordedActivity> merge(List<RecordedActivity> intervalsRides, List<RecordedActivity> stravaRides,
			Set<String> known) {
		List<RecordedActivity> merged = new ArrayList<>();
		Set<String> replaced = new java.util.HashSet<>();
		for (RecordedActivity strava : stravaRides) {
			RecordedActivity twin = intervalsRides.stream().filter(strava::isSameActivityAs)
					.filter(intervals -> !replaced.contains(intervals.id())).findFirst().orElse(null);
			if (twin != null) {
				replaced.add(twin.id());
			}
			boolean keepIntervals = twin != null && known.contains(twin.id()) && !known.contains(strava.id());
			merged.add(keepIntervals ? twin : strava);
		}
		intervalsRides.stream().filter(intervals -> !replaced.contains(intervals.id())).forEach(merged::add);
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
