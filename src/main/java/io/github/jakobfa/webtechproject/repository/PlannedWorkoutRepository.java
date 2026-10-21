package io.github.jakobfa.webtechproject.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;

public interface PlannedWorkoutRepository extends JpaRepository<PlannedWorkout, Long> {

	List<PlannedWorkout> findByPlanAthleteIdAndDateBetween(Long athleteId, LocalDate from, LocalDate to);

	List<PlannedWorkout> findByPlanAthleteIdAndActivityIdStartingWith(Long athleteId, String prefix);

}
