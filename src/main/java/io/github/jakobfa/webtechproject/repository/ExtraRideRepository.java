package io.github.jakobfa.webtechproject.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.ExtraRide;

public interface ExtraRideRepository extends JpaRepository<ExtraRide, Long> {

	List<ExtraRide> findByAthleteIdAndDateBetween(Long athleteId, LocalDate from, LocalDate to);

	List<ExtraRide> findByAthleteIdAndDateGreaterThanEqualOrderByDate(Long athleteId, LocalDate from);

	void deleteByAthleteIdAndActivityIdStartingWith(Long athleteId, String prefix);

}
