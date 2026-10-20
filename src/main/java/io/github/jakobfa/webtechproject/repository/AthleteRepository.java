package io.github.jakobfa.webtechproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.jakobfa.webtechproject.model.Athlete;

public interface AthleteRepository extends JpaRepository<Athlete, Long> {
}
