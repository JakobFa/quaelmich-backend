package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Event;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.model.Sport;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.ExtraRideRepository;
import io.github.jakobfa.webtechproject.repository.RaceEventRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

@Service
public class TrainingPlanService {

	private final AthleteRepository athleteRepository;
	private final TrainingPlanRepository planRepository;
	private final PlanGenerator planGenerator;
	private final RaceEventRepository raceEvents;
	private final WorkoutLibrary library;
	private final ExtraRideRepository extraRides;
	private final Clock clock;

	public TrainingPlanService(AthleteRepository athleteRepository, TrainingPlanRepository planRepository,
			PlanGenerator planGenerator, RaceEventRepository raceEvents, WorkoutLibrary library,
			ExtraRideRepository extraRides, Clock clock) {
		this.extraRides = extraRides;
		this.library = library;
		this.raceEvents = raceEvents;
		this.athleteRepository = athleteRepository;
		this.planRepository = planRepository;
		this.planGenerator = planGenerator;
		this.clock = clock;
	}

	/**
	 * Erstellt den Plan ab heute – oder passt einen vorhandenen Plan an (z. B. nach geänderter Verfügbarkeit):
	 * Vergangenes bleibt mit Häkchen, Bewertungen und gefahrenen Werten erhalten, ebenso heute schon
	 * Erledigtes. Nur die offenen Workouts ab heute werden neu verteilt. Gerechnet wird weiter vom
	 * ursprünglichen Planstart aus, damit Phasen und Steigerung gleich bleiben.
	 */
	@Transactional
	public Optional<TrainingPlan> generate(Long athleteId) {
		return athleteRepository.findById(athleteId).map(athlete -> {
			LocalDate today = LocalDate.now(clock);
			var existing = planRepository.findByAthleteId(athleteId);
			if (existing.isEmpty()) {
				TrainingPlan plan = new TrainingPlan(athlete, today);
				planGenerator.generateSeason(athlete, today, Map.of(), moreEventsOf(athleteId), horizon())
						.forEach(plan::addWorkout);
				return planRepository.save(plan);
			}

			TrainingPlan plan = existing.get();
			// vergangene Wochen-Ausnahmen braucht es nicht mehr
			LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			plan.getWeekAvailability().keySet().removeIf(monday -> monday.isBefore(thisMonday));
			replan(plan, today, LocalDate.MAX);
			return planRepository.save(plan);
		});
	}

	/**
	 * "Diese Woche habe ich andere Zeit": nur diese Woche (ab heute) neu verteilen, die normale
	 * Verfügbarkeit bleibt. availability null = Ausnahme entfernen. Leer ohne Plan;
	 * IllegalArgumentException, wenn das kein Montag ist oder die Woche schon vorbei ist.
	 */
	@Transactional
	public Optional<TrainingPlan> setWeekAvailability(Long athleteId, LocalDate monday,
			WeeklyAvailability availability) {
		LocalDate today = LocalDate.now(clock);
		if (monday.getDayOfWeek() != DayOfWeek.MONDAY || monday.plusDays(6).isBefore(today)) {
			throw new IllegalArgumentException("keine aktuelle oder kommende Woche");
		}
		return planRepository.findByAthleteId(athleteId).map(plan -> {
			if (availability == null) {
				plan.getWeekAvailability().remove(monday);
			} else {
				plan.getWeekAvailability().put(monday, availability);
			}
			LocalDate from = monday.isBefore(today) ? today : monday;
			replan(plan, from, monday.plusDays(6));
			return planRepository.save(plan);
		});
	}

	/** Offene Workouts im Zeitraum neu verteilen; Erledigtes und Rückmeldungen ("nicht gemacht") bleiben */
	private void replan(TrainingPlan plan, LocalDate from, LocalDate to) {
		Set<LocalDate> keptDays = plan.getWorkouts().stream()
				.filter(planned -> !inRange(planned.getDate(), from, to) || isKept(planned))
				.map(PlannedWorkout::getDate).collect(Collectors.toSet());
		List<PlannedWorkout> fresh = planGenerator.generateSeason(plan.getAthlete(), plan.getStartDate(),
				plan.getWeekAvailability(), moreEventsOf(plan.getAthlete().getId()), horizon(), historyOf(plan))
				.stream()
				.filter(planned -> inRange(planned.getDate(), from, to))
				.filter(planned -> !keptDays.contains(planned.getDate()))
				.toList();
		List<PlannedWorkout> open = plan.getWorkouts().stream()
				.filter(planned -> inRange(planned.getDate(), from, to) && !isKept(planned)).toList();
		// Ändert sich nichts, bleiben die bestehenden Einheiten stehen (kein Austausch ohne Grund)
		if (!sameWorkouts(open, fresh)) {
			plan.getWorkouts().removeAll(open);
			fresh.forEach(plan::addWorkout);
		}
		alignPhasePerWeek(plan, fresh);
		holdWhileSick(plan);
		plan.sortByDate();
	}

	/**
	 * Eine Woche hat eine Phase. Wird sie bei der Anpassung geändert (z. B. Erholungswoche entfällt), bekommen auch
	 * die schon vergangenen Tage derselben Woche die neue Phase – sonst steht über der Woche "Erholung" und darunter
	 * Grundlage.
	 */
	private static void alignPhasePerWeek(TrainingPlan plan, List<PlannedWorkout> fresh) {
		Map<LocalDate, Phase> phaseOfWeek = new java.util.HashMap<>();
		fresh.forEach(planned -> phaseOfWeek.put(
				planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), planned.getPhase()));
		plan.getWorkouts().forEach(planned -> {
			Phase phase = phaseOfWeek.get(planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
			if (phase != null && planned.getPhase() != phase) {
				planned.setPhase(phase);
			}
		});
	}

	private static boolean sameWorkouts(List<PlannedWorkout> a, List<PlannedWorkout> b) {
		return signature(a).equals(signature(b));
	}

	private static List<String> signature(List<PlannedWorkout> workouts) {
		return workouts.stream()
				.map(planned -> planned.getDate() + "/" + planned.getWorkoutId() + "/" + planned.getDurationMinutes()
						+ "/" + planned.getPhase())
				.sorted().toList();
	}

	/** Solange jemand krank ist, bleibt alles ab dem ersten Krankheitstag ausgesetzt – auch neu geplante Tage */
	private static void holdWhileSick(TrainingPlan plan) {
		if (plan.getSickSince() == null) {
			return;
		}
		plan.getWorkouts().stream()
				.filter(planned -> !planned.getDate().isBefore(plan.getSickSince()))
				.filter(planned -> !planned.isCompleted() && planned.getActivityId() == null)
				.forEach(planned -> planned.setSkipReason(SkipReason.SICK));
	}

	/**
	 * Was in den vergangenen Wochen wirklich gefahren wurde (gegenüber dem Plan), dazu der lockere Wiedereinstieg
	 * nach Krankheit. Der Generator richtet den Plan danach aus.
	 *
	 * Zählt wird, was einer geplanten Einheit zugeordnet oder abgehakt ist, und Zusatzfahrten (Rad) – in Minuten und
	 * in Belastung (TSS). Für die laufende Woche gilt: gefahren bis heute, geplant die ganze Woche.
	 */
	public PlanHistory historyOf(TrainingPlan plan) {
		LocalDate today = LocalDate.now(clock);
		LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Map<LocalDate, List<PlannedWorkout>> workoutsByWeek = plan.getWorkouts().stream()
				.filter(planned -> !planned.getDate().isAfter(thisMonday.plusDays(6)))
				.collect(Collectors.groupingBy(
						planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));
		// Fahrten auch vor dem Planstart zählen für den Ausgangswert (die letzten 6 Wochen davor)
		List<ExtraRide> allRides = extraRides.findByAthleteIdAndDateGreaterThanEqualOrderByDate(
				plan.getAthlete().getId(), plan.getStartDate().minusWeeks(6));
		Map<LocalDate, List<ExtraRide>> ridesByWeek = allRides
				.stream().filter(ride -> ride.getDate() != null && !ride.getDate().isAfter(today)
						&& !ride.getDate().isBefore(plan.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))))
				.collect(Collectors.groupingBy(
						ride -> ride.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));

		Map<LocalDate, PlanHistory.Week> weeks = new java.util.TreeMap<>();
		PlanHistory.Week currentWeek = null;
		for (var entry : workoutsByWeek.entrySet()) {
			LocalDate monday = entry.getKey();
			boolean current = monday.equals(thisMonday);
			PlanHistory.Week week = weekOf(entry.getValue(), ridesByWeek.getOrDefault(monday, List.of()),
					current ? today : null);
			if (current) {
				currentWeek = week;
			} else {
				weeks.put(monday, week);
			}
		}
		if (currentWeek == null) {
			currentWeek = weekOf(List.of(), ridesByWeek.getOrDefault(thisMonday, List.of()), today);
		}
		LocalDate reentry = plan.getReentryWeeks() > 0 && plan.getReentryMonday() != null
				&& plan.getReentryMonday().plusWeeks(plan.getReentryWeeks()).isAfter(thisMonday)
						? plan.getReentryMonday() : null;
		Map<LocalDate, Integer> daily = dailyMinutes(plan, allRides, today);
		LocalDate dataStart = daily.entrySet().stream().filter(entry -> entry.getValue() > 0)
				.map(Map.Entry::getKey).min(LocalDate::compareTo).orElse(plan.getStartDate());
		if (plan.getStartDate().isBefore(dataStart)) {
			dataStart = plan.getStartDate();
		}
		Set<LocalDate> recoveryDays = plan.getWorkouts().stream()
				.filter(planned -> planned.getPhase() == Phase.RECOVERY && planned.getDate().isBefore(today))
				.flatMap(planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
						.datesUntil(planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)))
				.filter(day -> day.isBefore(today))
				.collect(Collectors.toSet());
		return new PlanHistory(thisMonday, weeks, reentry, reentry == null ? 0 : plan.getReentryWeeks(), today,
				currentWeek, daily, dataStart, recoveryDays);
	}

	/**
	 * Minuten je vergangenem Tag (vor heute), die für die Belastung zählen: Gefahrenes (zugeordnete Fahrten,
	 * abgehakte Einheiten, Zusatzfahrten Rad). Wurde ein Tag ohne Krankheit, Verletzung oder Pause ausgelassen, zählt
	 * mindestens das Geplante – sonst würde eine schwache Woche den Plan dauerhaft nach unten ziehen. Der Zeitraum
	 * reicht auch vor den Planstart zurück, damit der Plan bei der echten Form beginnt.
	 */
	static final double SKIPPED_SHARE = 0.7;
	static final int MAX_SKIPPED_FLOOR_MINUTES = 140;

	private Map<LocalDate, Integer> dailyMinutes(TrainingPlan plan, List<ExtraRide> rides, LocalDate today) {
		Map<LocalDate, Integer> actual = new java.util.HashMap<>();
		Map<LocalDate, Integer> planned = new java.util.HashMap<>();
		Set<LocalDate> excused = new java.util.HashSet<>();
		for (PlannedWorkout workout : plan.getWorkouts()) {
			if (!workout.getDate().isBefore(today)) {
				continue;
			}
			int minutes = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes()).orElseThrow()
					.getDurationInMinutes();
			planned.merge(workout.getDate(), minutes, Integer::sum);
			if (workout.getSkipReason() == SkipReason.SICK || workout.getSkipReason() == SkipReason.INJURED
					|| workout.getSkipReason() == SkipReason.PAUSE) {
				excused.add(workout.getDate());
			} else if (workout.getSkipReason() == null && workout.getActivityId() != null) {
				actual.merge(workout.getDate(), workout.getActualDurationMinutes() != null
						? workout.getActualDurationMinutes() : minutes, Integer::sum);
			} else if (workout.getSkipReason() == null && workout.isCompleted()) {
				actual.merge(workout.getDate(), minutes, Integer::sum);
			}
		}
		for (ExtraRide ride : rides) {
			if (ride.getDate() != null && ride.getDate().isBefore(today)
					&& (ride.getSport() == null || ride.getSport() == Sport.RIDE) && ride.getDurationMinutes() != null) {
				actual.merge(ride.getDate(), ride.getDurationMinutes(), Integer::sum);
			}
		}
		Map<LocalDate, Integer> result = new java.util.HashMap<>();
		Set<LocalDate> days = new java.util.HashSet<>(actual.keySet());
		days.addAll(planned.keySet());
		for (LocalDate day : days) {
			int done = actual.getOrDefault(day, 0);
			// Ausgelassene Einheiten zählen nur zu 70 % und höchstens bis 2:20 h (sonst bläht eine unrealistisch lange
			// geplante Fahrt, die nie gefahren wurde, den Schnitt auf)
			int floor = (int) Math.min(planned.getOrDefault(day, 0) * SKIPPED_SHARE, MAX_SKIPPED_FLOOR_MINUTES);
			result.put(day, excused.contains(day) ? done : Math.max(done, floor));
		}
		return result;
	}

	/**
	 * Eine Woche in Zahlen. doneUntil = nur Gefahrenes bis zu diesem Tag zählt (laufende Woche); geplant ist immer
	 * die ganze Woche.
	 */
	private PlanHistory.Week weekOf(List<PlannedWorkout> workouts, List<ExtraRide> rides, LocalDate doneUntil) {
		int plannedMinutes = 0;
		int plannedTss = 0;
		int actualMinutes = 0;
		int actualTss = 0;
		int sickDays = 0;
		for (PlannedWorkout workout : workouts) {
			var resolved = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes()).orElseThrow();
			int minutes = resolved.getDurationInMinutes();
			plannedMinutes += minutes;
			plannedTss += resolved.getTss();
			if (workout.getSkipReason() != null) {
				sickDays += workout.getSkipReason().needsGentleReturn() ? 1 : 0;
			} else if (doneUntil == null || !workout.getDate().isAfter(doneUntil)) {
				if (workout.getActivityId() != null) {
					actualMinutes += workout.getActualDurationMinutes() != null ? workout.getActualDurationMinutes()
							: minutes;
					actualTss += workout.getActualTss() != null ? workout.getActualTss()
							: workout.getActualDurationMinutes() != null
									? estimateTss(workout.getActualDurationMinutes()) : resolved.getTss();
				} else if (workout.isCompleted()) {
					actualMinutes += minutes;
					actualTss += resolved.getTss();
				}
			}
		}
		for (ExtraRide ride : rides) {
			// Radfahrten zählen für Umfang (Minuten) und Belastung; andere Sportarten (Laufen, Schwimmen, Kraft …)
			// nur mit ihrer Belastung (TSS) – ihre Minuten sagen nichts über den Radumfang
			if (ride.getSport() == null || ride.getSport() == Sport.RIDE) {
				actualMinutes += ride.getDurationMinutes() == null ? 0 : ride.getDurationMinutes();
			}
			actualTss += ride.getTss() != null ? ride.getTss()
					: ride.getDurationMinutes() == null ? 0 : estimateTss(ride.getDurationMinutes());
		}
		return new PlanHistory.Week(plannedMinutes, actualMinutes, sickDays, plannedTss, actualTss);
	}

	/** Ohne gemessene Belastung: ruhige Schätzung aus der Dauer (wie bei den Erfahrungspunkten) */
	private static int estimateTss(int minutes) {
		return (int) Math.round(minutes * ProgressService.XP_PER_MINUTE_WITHOUT_TSS);
	}

	/**
	 * Gleicht den Plan mit dem ab, was gefahren wurde – sofort nach einer neuen Fahrt, einem Häkchen oder einer
	 * Rückmeldung, nicht erst beim nächsten Öffnen. Ab morgen wird neu angepasst (heute bleibt, wie es ist), und nur,
	 * wenn sich seit dem letzten Abgleich etwas an den gefahrenen Werten geändert hat. Wer krank gemeldet ist,
	 * bleibt angehalten.
	 */
	@Transactional
	public void adaptToActuals(Long athleteId) {
		planRepository.findByAthleteId(athleteId).ifPresent(plan -> {
			if (plan.getSickSince() != null) {
				return;
			}
			PlanHistory history = historyOf(plan);
			Integer fingerprint = java.util.Objects.hash(history.weeks(), history.currentWeek(), history.dailyMinutes(),
					history.reentryMonday(), history.reentryWeeks());
			if (fingerprint.equals(plan.getActualsFingerprint())) {
				return;
			}
			plan.setActualsFingerprint(fingerprint);
			replan(plan, LocalDate.now(clock).plusDays(1), LocalDate.MAX);
			planRepository.save(plan);
		});
	}

	/**
	 * Plant alles Offene ab heute neu, ausgerichtet an dem, was bisher gefahren wurde (z. B. nach Krankheit).
	 * Erledigtes und Rückmeldungen bleiben.
	 */
	@Transactional
	public void replanFromToday(TrainingPlan plan) {
		replan(plan, LocalDate.now(clock), LocalDate.MAX);
		planRepository.save(plan);
	}

	/** weitere Events (zusätzlich zum ersten im Profil), als Event für den Generator */
	private List<Event> moreEventsOf(Long athleteId) {
		return raceEvents.findByAthleteIdOrderByDate(athleteId).stream().map(RaceEvent::toEvent).toList();
	}

	private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
		return !date.isBefore(from) && !date.isAfter(to);
	}

	/**
	 * Abgehakt, mit einer gefahrenen Einheit verknüpft, mit Rückmeldung (nicht gemacht, Pause) oder vom Nutzer
	 * selbst ausgesucht – z. B. nach einer neuen FTP bleibt das getauschte Workout für heute stehen
	 */
	private static boolean isKept(PlannedWorkout planned) {
		return planned.isCompleted() || planned.getActivityId() != null || planned.getSkipReason() != null
				|| planned.isChosenByUser() || planned.getAdjustmentNote() != null;
	}

	/** Bis wann ein Plan ohne Event geplant ist: immer etwa 12 Wochen ab heute */
	private LocalDate horizon() {
		return LocalDate.now(clock).plusWeeks(PlanGenerator.OPEN_HORIZON_WEEKS);
	}

	/** Verlängert wird, sobald weniger als so viele Wochen im Voraus geplant sind */
	static final int EXTEND_WHEN_LESS_THAN_WEEKS = 10;

	/**
	 * Der Plan zum Anzeigen. Ein Plan ohne Event (Ziel ohne Termin) hat kein Ende: Sind nur noch weniger als
	 * 10 Wochen geplant, werden die nächsten Wochen bis zum Horizont angehängt – im selben Zyklus weiter.
	 */
	@Transactional
	public Optional<TrainingPlan> currentPlan(Long athleteId) {
		return planRepository.findByAthleteId(athleteId).map(plan -> {
			adaptOncePerWeek(plan);
			if (!plan.getAthlete().hasEventGoal()) {
				LocalDate lastPlanned = plan.getWorkouts().stream().map(PlannedWorkout::getDate)
						.max(LocalDate::compareTo).orElse(LocalDate.now(clock));
				if (lastPlanned.isBefore(LocalDate.now(clock).plusWeeks(EXTEND_WHEN_LESS_THAN_WEEKS))) {
					replan(plan, lastPlanned.plusDays(1), horizon());
					planRepository.save(plan);
				}
			}
			plan.getWorkouts().size(); // Workouts innerhalb der Transaktion laden
			return plan;
		});
	}

	/**
	 * Einmal pro Woche (beim ersten Öffnen): War die letzte Woche deutlich schwächer als geplant oder läuft ein
	 * Wiedereinstieg, wird der Rest des Plans an die gefahrenen Werte angepasst.
	 */
	private void adaptOncePerWeek(TrainingPlan plan) {
		LocalDate thisMonday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		if (thisMonday.equals(plan.getLastAdaptedMonday())) {
			return;
		}
		plan.setLastAdaptedMonday(thisMonday);
		PlanHistory history = historyOf(plan);
		var lastWeek = history.before(thisMonday);
		boolean weak = lastWeek != null && lastWeek.fulfilment() < PlanHistory.MIN_FULFILMENT;
		if (weak || history.reentryMonday() != null) {
			replan(plan, LocalDate.now(clock), LocalDate.MAX);
		}
		planRepository.save(plan);
	}

	@Transactional(readOnly = true)
	public Optional<TrainingPlan> findByAthlete(Long athleteId) {
		return planRepository.findByAthleteId(athleteId).map(plan -> {
			plan.getWorkouts().size(); // Workouts innerhalb der Transaktion laden
			return plan;
		});
	}

}
