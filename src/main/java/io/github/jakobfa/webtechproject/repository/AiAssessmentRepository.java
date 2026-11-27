package io.github.jakobfa.webtechproject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.AiAssessment;

public interface AiAssessmentRepository extends JpaRepository<AiAssessment, Long> {

	Optional<AiAssessment> findByOwnerId(Long ownerId);

}
