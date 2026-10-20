package io.github.jakobfa.webtechproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.PlannedWorkout;

public interface PlannedWorkoutRepository extends JpaRepository<PlannedWorkout, Long> {
}
