package io.github.jakobfa.webtechproject.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.RaceEvent;

public interface RaceEventRepository extends JpaRepository<RaceEvent, Long> {

	List<RaceEvent> findByAthleteIdOrderByDate(Long athleteId);

}
