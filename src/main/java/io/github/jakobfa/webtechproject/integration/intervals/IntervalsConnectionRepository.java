package io.github.jakobfa.webtechproject.integration.intervals;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntervalsConnectionRepository extends JpaRepository<IntervalsConnection, Long> {

	Optional<IntervalsConnection> findByOwnerId(Long ownerId);

}
