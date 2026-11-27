package io.github.jakobfa.webtechproject.health;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Long> {

	Optional<HealthConnection> findByOwnerId(Long ownerId);

	Optional<HealthConnection> findByKeyHash(String keyHash);

}
