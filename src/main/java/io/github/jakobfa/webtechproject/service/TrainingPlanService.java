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
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.WeeklyAvailability;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.RaceEventRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

@Service
public class TrainingPlanService {

	private final AthleteRepository athleteRepository;
	private final TrainingPlanRepository planRepository;
	private final PlanGenerator planGenerator;
	private final RaceEventRepository raceEvents;
	private final WorkoutLibrary library;
	private final Clock clock;

	public TrainingPlanService(AthleteRepository athleteRepository, TrainingPlanRepository planRepository,
			PlanGenerator planGenerator, RaceEventRepository raceEvents, WorkoutLibrary library, Clock clock) {
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
		plan.getWorkouts().removeIf(planned -> inRange(planned.getDate(), from, to) && !isKept(planned));
		// Rad und Kraft getrennt: ein abgehaktes Krafttraining soll die Radeinheit am selben Tag nicht verdrängen
		Set<String> keptDays = plan.getWorkouts().stream().map(TrainingPlanService::slotOf)
				.collect(Collectors.toSet());
		planGenerator.generateSeason(plan.getAthlete(), plan.getStartDate(), plan.getWeekAvailability(),
				moreEventsOf(plan.getAthlete().getId()), horizon(), historyOf(plan)).stream()
				.filter(planned -> inRange(planned.getDate(), from, to))
				.filter(planned -> !keptDays.contains(slotOf(planned)))
				.forEach(plan::addWorkout);
		holdWhileSick(plan);
		plan.sortByDate();
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
	 */
	PlanHistory historyOf(TrainingPlan plan) {
		LocalDate thisMonday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Map<LocalDate, List<PlannedWorkout>> byWeek = plan.getWorkouts().stream()
				.filter(planned -> planned.getDate().isBefore(thisMonday))
				.collect(Collectors.groupingBy(
						planned -> planned.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));
		Map<LocalDate, PlanHistory.Week> weeks = new java.util.TreeMap<>();
		byWeek.forEach((monday, workouts) -> {
			int planned = 0;
			int actual = 0;
			int sickDays = 0;
			for (PlannedWorkout workout : workouts) {
				int minutes = library.resolve(workout.getWorkoutId(), workout.getDurationMinutes()).orElseThrow()
						.getDurationInMinutes();
				planned += minutes;
				if (workout.getSkipReason() != null) {
					sickDays += workout.getSkipReason().needsGentleReturn() ? 1 : 0;
				} else if (workout.getActivityId() != null) {
					actual += workout.getActualDurationMinutes() != null ? workout.getActualDurationMinutes() : minutes;
				} else if (workout.isCompleted()) {
					actual += minutes;
				}
			}
			weeks.put(monday, new PlanHistory.Week(planned, actual, sickDays));
		});
		LocalDate reentry = plan.getReentryWeeks() > 0 && plan.getReentryMonday() != null
				&& plan.getReentryMonday().plusWeeks(plan.getReentryWeeks()).isAfter(thisMonday)
						? plan.getReentryMonday() : null;
		return new PlanHistory(thisMonday, weeks, reentry, reentry == null ? 0 : plan.getReentryWeeks());
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

	private static String slotOf(PlannedWorkout planned) {
		return planned.getDate() + (WorkoutLibrary.isStrengthId(planned.getWorkoutId()) ? "/kraft" : "/rad");
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
				|| planned.isChosenByUser();
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
