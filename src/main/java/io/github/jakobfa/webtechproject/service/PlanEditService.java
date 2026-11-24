package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService.NotPossibleException;

/**
 * Einheiten im Plan frei setzen: an einem beliebigen Tag anlegen (auch an einem freien Tag), auf einen anderen Tag
 * schieben oder entfernen. Was so gesetzt wird, gilt als selbst gewählt und bleibt, auch wenn der Plan neu verteilt
 * wird. Gedacht für die KI, die als Trainer den ganzen Plan ändern darf.
 */
@Service
public class PlanEditService {

	/** so weit voraus darf eine Einheit liegen */
	static final int MAX_DAYS_AHEAD = 365;

	/** Hinweis an der Einheit; beginnt mit "Von dir", damit sie als selbst gewählt gilt */
	static final String ADDED_NOTE = PlannedWorkout.USER_CHOICE_NOTE + " eingeplant.";
	static final String MOVED_NOTE = PlannedWorkout.USER_CHOICE_NOTE + " verschoben.";

	private final AthleteService athletes;
	private final TrainingPlanService plans;
	private final PlannedWorkoutService plannedWorkouts;
	private final WorkoutCustomizationService customization;
	private final PlannedWorkoutRepository repository;
	private final Clock clock;

	public PlanEditService(AthleteService athletes, TrainingPlanService plans, PlannedWorkoutService plannedWorkouts,
			WorkoutCustomizationService customization, PlannedWorkoutRepository repository, Clock clock) {
		this.athletes = athletes;
		this.plans = plans;
		this.plannedWorkouts = plannedWorkouts;
		this.customization = customization;
		this.repository = repository;
		this.clock = clock;
	}

	/** Neue Einheit an einem Tag (heute oder später). Leer ohne Plan; NotPossibleException, wenn das nicht geht. */
	@Transactional
	public Optional<PlannedWorkout> add(Long userId, LocalDate date, Long workoutId, Integer durationMinutes) {
		return planOf(userId).map(plan -> {
			checkDate(date);
			Workout chosen = customization.chosenWorkout(userId, workoutId);
			Integer minutes = customization.validMinutes(workoutId, chosen, durationMinutes);
			clearSlot(plan, date, null);
			PlannedWorkout created = new PlannedWorkout(date, chosen, minutes, phaseAround(plan, date));
			created.setAdjustmentNote(ADDED_NOTE);
			plan.addWorkout(created);
			plan.sortByDate();
			return repository.saveAndFlush(created); // die Id gibt es erst nach dem Speichern
		});
	}

	/** Eine offene Einheit auf einen anderen Tag (heute oder später) schieben */
	@Transactional
	public Optional<PlannedWorkout> move(Long userId, Long planId, LocalDate date) {
		return plannedWorkouts.findOwned(planId, userId).map(planned -> {
			ensureOpen(planned);
			checkDate(date);
			TrainingPlan plan = planned.getPlan();
			clearSlot(plan, date, planned);
			planned.setDate(date);
			planned.setPhase(phaseAround(plan, date));
			if (planned.getAdjustmentNote() == null) {
				planned.setAdjustmentNote(MOVED_NOTE);
			}
			plan.sortByDate();
			return planned;
		});
	}

	/** Eine offene Einheit entfernen (freier Tag). Eine Neuberechnung des Plans kann den Tag wieder füllen. */
	@Transactional
	public Optional<LocalDate> remove(Long userId, Long planId) {
		return plannedWorkouts.findOwned(planId, userId).map(planned -> {
			ensureOpen(planned);
			planned.getPlan().getWorkouts().remove(planned);
			return planned.getDate();
		});
	}

	/**
	 * Einen einzelnen Tag fest freihalten (free = true) oder wieder wie im Profil planen. Technisch eine Ausnahme für
	 * diese Woche, in der nur dieser Wochentag auf 0 Stunden steht; sie bleibt bei jeder Neuberechnung bestehen.
	 * Selbst gewählte Einheiten an diesem Tag bleiben stehen (vorher remove_workout). Leer ohne Plan oder Profil;
	 * NotPossibleException bei einem Tag vor heute oder weiter als ein Jahr voraus.
	 */
	@Transactional
	public Optional<TrainingPlan> setDayFree(Long userId, LocalDate date, boolean free) {
		checkDate(date);
		return athletes.findByOwner(userId).flatMap(athlete -> plans.findByAthlete(athlete.getId()).map(plan -> {
			LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			io.github.jakobfa.webtechproject.model.WeeklyAvailability normal = athlete.getAvailability();
			io.github.jakobfa.webtechproject.model.WeeklyAvailability week = plan.getWeekAvailability().getOrDefault(monday,
					normal);
			io.github.jakobfa.webtechproject.model.WeeklyAvailability changed = week.with(date.getDayOfWeek(),
					free ? 0 : normal.hoursOn(date.getDayOfWeek()));
			// wieder genau wie im Profil: keine Ausnahme mehr nötig
			return plans.setWeekAvailability(athlete.getId(), monday, changed.sameHoursAs(normal) ? null : changed)
					.orElseThrow();
		}));
	}

	private Optional<TrainingPlan> planOf(Long userId) {
		return athletes.findByOwner(userId).flatMap(athlete -> plans.findByAthlete(athlete.getId()));
	}

	private void ensureOpen(PlannedWorkout planned) {
		if (planned.isCompleted() || planned.getActivityId() != null || planned.getSkipReason() != null
				|| planned.getDate().isBefore(LocalDate.now(clock))) {
			throw new NotPossibleException();
		}
	}

	private void checkDate(LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
			throw new NotPossibleException();
		}
	}

	/**
	 * Macht den Platz an einem Tag frei: eine offene Einheit dort weicht, eine feststehende
	 * (abgehakt, gefahren oder ausgefallen) bleibt, dann geht es nicht. Eine selbst gewählte weicht, denn wer sie
	 * hier setzt, will es so.
	 */
	private void clearSlot(TrainingPlan plan, LocalDate date, PlannedWorkout except) {
		for (PlannedWorkout other : plan.getWorkouts().stream().filter(candidate -> candidate != except
				&& candidate.getDate().equals(date))
				.toList()) {
			boolean fixed = other.isCompleted() || other.getActivityId() != null || other.getSkipReason() != null;
			if (fixed) {
				throw new NotPossibleException();
			}
			plan.getWorkouts().remove(other);
		}
	}

	/** Phase der Woche: die der anderen Einheiten dieser Woche, sonst die nächstliegende */
	private static io.github.jakobfa.webtechproject.model.Phase phaseAround(TrainingPlan plan, LocalDate date) {
		LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return plan.getWorkouts().stream().filter(other -> other.getPhase() != null)
				.min(Comparator.comparingLong((PlannedWorkout other) -> Math.abs(
						other.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
								- monday.toEpochDay()))
						.thenComparingLong(other -> Math.abs(other.getDate().toEpochDay() - date.toEpochDay())))
				.map(PlannedWorkout::getPhase).orElse(null);
	}

}
