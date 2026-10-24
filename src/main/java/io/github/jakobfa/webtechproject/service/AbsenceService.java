package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.PlannedWorkoutRepository;

/**
 * "Nicht gemacht" und Pausen: Auch ein ausgefallenes Training ist eine Rückmeldung.
 * Nach Krankheit, Verletzung oder einer längeren Pause wird das nächste harte Training sanfter.
 */
@Service
public class AbsenceService {

	/** Längste Pause am Stück */
	static final int MAX_PAUSE_DAYS = 56;
	/** Ab so vielen Tagen Pause wird der Wiedereinstieg sanfter */
	static final int GENTLE_RETURN_AFTER_DAYS = 4;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN);

	/** Wird geworfen, wenn das gerade nicht geht (z. B. Fahrt schon zugeordnet, Pause in der Vergangenheit) */
	public static class NotPossibleException extends RuntimeException {
	}

	private final PlannedWorkoutService plannedWorkoutService;
	private final PlannedWorkoutRepository repository;
	private final TrainingPlanService planService;
	private final AthleteService athleteService;
	private final PlanAdaptationService adaptation;
	private final Clock clock;

	public AbsenceService(PlannedWorkoutService plannedWorkoutService, PlannedWorkoutRepository repository,
			TrainingPlanService planService, AthleteService athleteService, PlanAdaptationService adaptation,
			Clock clock) {
		this.plannedWorkoutService = plannedWorkoutService;
		this.repository = repository;
		this.planService = planService;
		this.athleteService = athleteService;
		this.adaptation = adaptation;
		this.clock = clock;
	}

	/** Leer, wenn das Workout nicht existiert oder fremd ist */
	@Transactional
	public Optional<PlannedWorkout> skip(Long id, Long userId, SkipReason reason) {
		if (reason == SkipReason.PAUSE) {
			throw new NotPossibleException(); // Pausen gehen über pause()
		}
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			if (planned.getActivityId() != null) {
				throw new NotPossibleException(); // da ist schon eine Fahrt zugeordnet
			}
			planned.setSkipReason(reason);
			planned.setCompleted(false);
			planned.setRating(null);
			if (reason.needsGentleReturn()) {
				gentleReturnAfter(planned.getPlan(), planned.getDate(),
						"Angepasst: Am %s warst du %s – sanfter Wiedereinstieg."
								.formatted(DAY.format(planned.getDate()), reason.inGerman()));
			}
			return repository.save(planned);
		});
	}

	/** "Doch nicht": Rückmeldung zurücknehmen */
	@Transactional
	public Optional<PlannedWorkout> undoSkip(Long id, Long userId) {
		return plannedWorkoutService.findOwned(id, userId).map(planned -> {
			planned.setSkipReason(null);
			return repository.save(planned);
		});
	}

	/** Pausiert den Plan von bis (beide eingeschlossen). Leer ohne Plan. */
	@Transactional
	public Optional<TrainingPlan> pause(Long userId, LocalDate from, LocalDate until) {
		LocalDate today = LocalDate.now(clock);
		if (from.isBefore(today) || until.isBefore(from) || ChronoUnit.DAYS.between(from, until) >= MAX_PAUSE_DAYS) {
			throw new NotPossibleException();
		}
		return planOf(userId).map(plan -> {
			for (PlannedWorkout planned : plan.getWorkouts()) {
				boolean inPause = !planned.getDate().isBefore(from) && !planned.getDate().isAfter(until);
				if (inPause && !planned.isCompleted() && planned.getActivityId() == null) {
					planned.setSkipReason(SkipReason.PAUSE);
				}
			}
			if (ChronoUnit.DAYS.between(from, until) + 1 >= GENTLE_RETURN_AFTER_DAYS) {
				gentleReturnAfter(plan, until,
						"Angepasst: Nach deiner Pause bis %s – sanfter Wiedereinstieg.".formatted(DAY.format(until)));
			}
			return plan;
		});
	}

	/** Pause beenden: ab heute gilt der Plan wieder. Leer ohne Plan. */
	@Transactional
	public Optional<TrainingPlan> resume(Long userId) {
		LocalDate today = LocalDate.now(clock);
		return planOf(userId).map(plan -> {
			plan.getWorkouts().stream()
					.filter(planned -> planned.getSkipReason() == SkipReason.PAUSE && !planned.getDate().isBefore(today))
					.forEach(planned -> planned.setSkipReason(null));
			return plan;
		});
	}

	private Optional<TrainingPlan> planOf(Long userId) {
		return athleteService.findByOwner(userId).flatMap(athlete -> planService.findByAthlete(athlete.getId()));
	}

	private void gentleReturnAfter(TrainingPlan plan, LocalDate lastMissedDay, String note) {
		adaptation.adaptAfterAbsence(lastMissedDay, note, plan.getWorkouts().stream()
				.filter(next -> next.getSkipReason() == null)
				.toList());
	}

}
