package io.github.jakobfa.webtechproject.health;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyHealthRepository extends JpaRepository<DailyHealth, Long> {

	Optional<DailyHealth> findByOwnerIdAndDate(Long ownerId, LocalDate date);

	List<DailyHealth> findByOwnerIdAndDateGreaterThanEqualOrderByDateDesc(Long ownerId, LocalDate from);

	List<DailyHealth> findByOwnerIdOrderByDate(Long ownerId);

	List<DailyHealth> findByOwnerIdAndDateBetweenOrderByDate(Long ownerId, LocalDate from, LocalDate to);

}
