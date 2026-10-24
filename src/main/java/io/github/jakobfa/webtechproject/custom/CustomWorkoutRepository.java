package io.github.jakobfa.webtechproject.custom;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomWorkoutRepository extends JpaRepository<CustomWorkout, Long> {

	List<CustomWorkout> findByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(Long ownerId);


	List<CustomWorkout> findByOwnerId(Long ownerId);

}
