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

	/** So weit zurück darf eine Krankheit nachgetragen werden */
	static final int MAX_SICK_DAYS_BACK = 56;
	/** Ab so vielen Krankheitstagen gibt es eine lockere Wiedereinstiegswoche (ab 14 Tagen zwei) */
	static final int REENTRY_AFTER_DAYS = 7;
	static final int LONG_ILLNESS_DAYS = 14;

	/**
	 * Krank (oder verletzt) melden.
	 * <ul>
	 * <li>Ohne until: "ich bin krank" – der Plan steht still (alle offenen Einheiten ab from ausgesetzt, auch
	 * künftige), bis {@link #endSickness} aufgerufen wird. from darf in der Vergangenheit liegen: Man hat die App
	 * erst nach Krankheitsbeginn geöffnet.</li>
	 * <li>Mit until: ein abgeschlossener Zeitraum, auch rückwirkend – die Einheiten dazwischen entfallen, danach
	 * geht es sanft weiter.</li>
	 * </ul>
	 * NotPossibleException bei einem Datum zu weit in der Vergangenheit/Zukunft oder wenn schon eine offene Krankheit
	 * läuft und ein Zeitraum gemeldet wird. Leer ohne Plan.
	 */
	@Transactional
	public Optional<TrainingPlan> startSickness(Long userId, LocalDate from, LocalDate until) {
		LocalDate today = LocalDate.now(clock);
		if (from.isAfter(today) || ChronoUnit.DAYS.between(from, today) > MAX_SICK_DAYS_BACK
				|| (until != null && (until.isBefore(from) || until.isAfter(today.plusDays(MAX_SICK_DAYS_BACK))))) {
			throw new NotPossibleException();
		}
		return planOf(userId).map(plan -> {
			if (until == null) {
				LocalDate since = plan.getSickSince() == null || from.isBefore(plan.getSickSince()) ? from
						: plan.getSickSince();
				plan.setSickSince(since);
				markSick(plan, since, LocalDate.MAX);
				return plan;
			}
			if (plan.getSickSince() != null) {
				throw new NotPossibleException(); // erst "wieder gesund" melden
			}
			markSick(plan, from, until);
			LocalDate back = until.plusDays(1).isBefore(today) ? today : until.plusDays(1);
			returnAfter(plan, ChronoUnit.DAYS.between(from, until) + 1, back, until);
			return plan;
		});
	}

	/**
	 * "Wieder gesund": Der Plan läuft ab heute weiter (since = ab wann man schon wieder gesund war, nicht vor dem
	 * Krankheitsbeginn und nicht in der Zukunft). Die Tage dazwischen bleiben als krank markiert. Nach längerer
	 * Krankheit folgt ein lockerer Wiedereinstieg. Leer ohne Plan; NotPossibleException, wenn niemand krank ist.
	 */
	@Transactional
	public Optional<TrainingPlan> endSickness(Long userId, LocalDate since) {
		LocalDate today = LocalDate.now(clock);
		return planOf(userId).map(plan -> {
			LocalDate sickSince = plan.getSickSince();
			LocalDate healthy = since == null ? today : since;
			if (sickSince == null || healthy.isAfter(today) || healthy.isBefore(sickSince)) {
				throw new NotPossibleException();
			}
			plan.setSickSince(null);
			// ab dem ersten gesunden Tag gilt der Plan wieder; was als krank markiert war, wird frei
			plan.getWorkouts().stream()
					.filter(planned -> !planned.getDate().isBefore(healthy))
					.filter(planned -> planned.getSkipReason() == SkipReason.SICK && planned.getActivityId() == null)
					.forEach(planned -> planned.setSkipReason(null));
			LocalDate back = healthy.isBefore(today) ? today : healthy;
			returnAfter(plan, ChronoUnit.DAYS.between(sickSince, healthy), back, healthy.minusDays(1));
			return plan;
		});
	}

	/** Offene Einheiten im Zeitraum als krank markieren (Erledigtes und Gefahrenes bleibt unberührt) */
	private void markSick(TrainingPlan plan, LocalDate from, LocalDate until) {
		plan.getWorkouts().stream()
				.filter(planned -> !planned.getDate().isBefore(from) && !planned.getDate().isAfter(until))
				.filter(planned -> !planned.isCompleted() && planned.getActivityId() == null)
				.forEach(planned -> {
					planned.setSkipReason(SkipReason.SICK);
					planned.setRating(null);
				});
	}

	/**
	 * Nach der Krankheit: Plan ab heute an die Wirklichkeit anpassen. Ab 7 Tagen ein lockerer Wiedereinstieg
	 * (ab 14 Tagen zwei Wochen), ab 4 Tagen wird zumindest das erste harte Training sanfter.
	 */
	private void returnAfter(TrainingPlan plan, long sickDays, LocalDate back, LocalDate lastSickDay) {
		if (sickDays >= REENTRY_AFTER_DAYS) {
			int weeks = sickDays >= LONG_ILLNESS_DAYS ? 2 : 1;
			// Wer erst Freitag bis Sonntag zurückkommt, startet die lockere Woche mit der nächsten Woche
			if (back.getDayOfWeek().getValue() >= java.time.DayOfWeek.FRIDAY.getValue()) {
				weeks++;
			}
			plan.setReentryMonday(back.with(java.time.temporal.TemporalAdjusters.previousOrSame(
					java.time.DayOfWeek.MONDAY)));
			plan.setReentryWeeks(weeks);
		}
		planService.replanFromToday(plan);
		if (sickDays >= GENTLE_RETURN_AFTER_DAYS) {
			gentleReturnAfter(plan, lastSickDay,
					"Angepasst: Nach deiner Krankheit bis %s – sanfter Wiedereinstieg.".formatted(DAY.format(lastSickDay)));
		}
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
