package io.github.jakobfa.webtechproject.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/** Erstellt den Plan neu ab heute. Ein vorhandener Plan des Athleten wird ersetzt. */
	@Transactional
	public Optional<TrainingPlan> generate(Long athleteId) {
		return athleteRepository.findById(athleteId).map(athlete -> {
			planRepository.findByAthleteId(athleteId).ifPresent(existing -> {
				planRepository.delete(existing);
				planRepository.flush();
			});

			LocalDate today = LocalDate.now(clock);
			TrainingPlan plan = new TrainingPlan(athlete, today);
			planGenerator.generate(athlete, today).forEach(plan::addWorkout);
			return planRepository.save(plan);
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
