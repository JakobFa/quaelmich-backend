package io.github.jakobfa.webtechproject.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.Athlete;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {

	Optional<Athlete> findByOwnerId(Long ownerId);

	/** Sperrt das Profil bis zum Ende der Transaktion – damit zwei Importe nicht gleichzeitig laufen */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Athlete a where a.id = :id")
	Optional<Athlete> lockById(@Param("id") Long id);

}
