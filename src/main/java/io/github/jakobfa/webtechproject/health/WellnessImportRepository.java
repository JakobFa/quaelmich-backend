package io.github.jakobfa.webtechproject.health;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WellnessImportRepository extends JpaRepository<WellnessImport, Long> {

	Optional<WellnessImport> findByOwnerId(Long ownerId);

}
