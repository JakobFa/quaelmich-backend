package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.TrainingPlan;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;
import io.github.jakobfa.webtechproject.repository.TrainingPlanRepository;

@Service
public class TrainingPlanService {

	private final AthleteRepository athleteRepository;
	private final TrainingPlanRepository planRepository;
	private final PlanGenerator planGenerator;
	private final Clock clock;

	public TrainingPlanService(AthleteRepository athleteRepository, TrainingPlanRepository planRepository,
			PlanGenerator planGenerator, Clock clock) {
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
				planGenerator.generate(athlete, today).forEach(plan::addWorkout);
				return planRepository.save(plan);
			}

			TrainingPlan plan = existing.get();
			plan.getWorkouts().removeIf(planned -> !planned.getDate().isBefore(today) && !isDone(planned));
			Set<LocalDate> keptDays = plan.getWorkouts().stream().map(PlannedWorkout::getDate)
					.collect(Collectors.toSet());
			planGenerator.generate(athlete, plan.getStartDate()).stream()
					.filter(planned -> !planned.getDate().isBefore(today))
					.filter(planned -> !keptDays.contains(planned.getDate()))
					.forEach(plan::addWorkout);
			plan.sortByDate();
			return planRepository.save(plan);
		});
	}

	/** Abgehakt oder schon mit einer gefahrenen Einheit verknüpft */
	private static boolean isDone(PlannedWorkout planned) {
		return planned.isCompleted() || planned.getActivityId() != null;
	}

	@Transactional(readOnly = true)
	public Optional<TrainingPlan> findByAthlete(Long athleteId) {
		return planRepository.findByAthleteId(athleteId).map(plan -> {
			plan.getWorkouts().size(); // Workouts innerhalb der Transaktion laden
			return plan;
		});
	}

}
