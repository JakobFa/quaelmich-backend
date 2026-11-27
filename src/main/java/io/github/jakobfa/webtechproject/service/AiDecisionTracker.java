package io.github.jakobfa.webtechproject.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

/**
 * Merkt sich, welche Einheiten die KI entschieden hat: Vor einem Werkzeug-Aufruf wird der Plan festgehalten, danach
 * bekommt jede neue oder geänderte Einheit das KI-Zeichen. Ändert der Nutzer in der App eine Einheit, verliert sie es.
 * "Geändert" heißt: anderer Tag, anderes Workout, andere Dauer oder eine neue Anpassung – Rückmeldungen (erledigt,
 * ausgelassen, Fahrt zugeordnet) zählen nicht, die ändern nichts an der Entscheidung.
 */
@Service
public class AiDecisionTracker {

	/** Stand des Plans vor einer Änderung; planId null = es gab noch keinen Plan */
	public record Snapshot(Long planId, Map<Long, String> signatures) {
	}

	private final AthleteService athletes;
	private final TrainingPlanRepository plans;
	private final java.time.Clock clock;

	public AiDecisionTracker(AthleteService athletes, TrainingPlanRepository plans, java.time.Clock clock) {
		this.athletes = athletes;
		this.plans = plans;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Snapshot snapshot(Long userId) {
		return planOf(userId).map(plan -> {
			Map<Long, String> signatures = new HashMap<>();
			plan.getWorkouts().forEach(workout -> signatures.put(workout.getId(), signature(workout)));
			return new Snapshot(plan.getId(), signatures);
		}).orElse(new Snapshot(null, Map.of()));
	}

	/** Neue oder geänderte Einheiten seit dem Snapshot: byAi = true → KI-Zeichen, false → Entscheidung des Nutzers */
	@Transactional
	public java.util.Set<Long> markChanges(Long userId, Snapshot before, boolean byAi) {
		Optional<TrainingPlan> plan = planOf(userId);
		if (plan.isEmpty()) {
			return java.util.Set.of();
		}
		boolean samePlan = Objects.equals(plan.get().getId(), before.planId());
		java.util.Set<Long> changed = new java.util.HashSet<>();
		for (PlannedWorkout workout : plan.get().getWorkouts()) {
			String previous = samePlan ? before.signatures().get(workout.getId()) : null;
			if ((previous == null || !previous.equals(signature(workout))) && workout.isAiDecided() != byAi) {
				workout.setAiDecided(byAi);
				changed.add(workout.getId());
			}
		}
		if (!changed.isEmpty()) {
			plans.save(plan.get());
		}
		return changed;
	}

	/** Die KI hat den ganzen Plan neu erstellt: alles ab heute ist ihre Entscheidung, auch was gleich geblieben ist */
	@Transactional
	public void markAllFromToday(Long userId) {
		java.time.LocalDate from = java.time.LocalDate.now(clock);
		planOf(userId).ifPresent(plan -> {
			plan.getWorkouts().stream().filter(workout -> !workout.getDate().isBefore(from))
					.forEach(workout -> workout.setAiDecided(true));
			plans.save(plan);
		});
	}

	private Optional<TrainingPlan> planOf(Long userId) {
		return athletes.findByOwner(userId).flatMap(athlete -> plans.findByAthleteId(athlete.getId()));
	}

	private static String signature(PlannedWorkout workout) {
		return workout.getDate() + "|" + workout.getWorkoutId() + "|" + workout.getDurationMinutes() + "|"
				+ workout.getAdjustmentNote() + "|" + workout.getOriginalWorkoutId();
	}

}
