package io.github.jakobfa.webtechproject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.TrainingPlan;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

	Optional<TrainingPlan> findByAthleteId(Long athleteId);

}
