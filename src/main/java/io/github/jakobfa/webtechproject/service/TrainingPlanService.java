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
	private final Clock clock;

	public TrainingPlanService(AthleteRepository athleteRepository, TrainingPlanRepository planRepository,
			PlanGenerator planGenerator, RaceEventRepository raceEvents, Clock clock) {
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
				moreEventsOf(plan.getAthlete().getId()), horizon()).stream()
				.filter(planned -> inRange(planned.getDate(), from, to))
				.filter(planned -> !keptDays.contains(slotOf(planned)))
				.forEach(plan::addWorkout);
		plan.sortByDate();
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

	@Transactional(readOnly = true)
	public Optional<TrainingPlan> findByAthlete(Long athleteId) {
		return planRepository.findByAthleteId(athleteId).map(plan -> {
			plan.getWorkouts().size(); // Workouts innerhalb der Transaktion laden
			return plan;
		});
	}

}
