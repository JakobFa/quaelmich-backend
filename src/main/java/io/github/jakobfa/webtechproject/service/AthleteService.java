package io.github.jakobfa.webtechproject.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;

@Service
public class AthleteService {

	private final AthleteRepository athleteRepository;

	public AthleteService(AthleteRepository athleteRepository) {
		this.athleteRepository = athleteRepository;
	}

	public Athlete create(Athlete athlete) {
		// IDs vergibt die Datenbank, mitgeschickte Werte werden ignoriert
		athlete.setId(null);
		athlete.getEvent().setId(null);
		return athleteRepository.save(athlete);
	}

	public Optional<Athlete> findById(Long id) {
		return athleteRepository.findById(id);
	}

	@Transactional
	public Optional<Athlete> update(Long id, Athlete changes) {
		return athleteRepository.findById(id).map(existing -> {
			existing.setFtp(changes.getFtp());
			existing.setWeightKg(changes.getWeightKg());
			existing.setLevel(changes.getLevel());
			existing.setAvailability(changes.getAvailability());

			var event = existing.getEvent();
			event.setName(changes.getEvent().getName());
			event.setDate(changes.getEvent().getDate());
			event.setDistanceKm(changes.getEvent().getDistanceKm());
			event.setElevationMeters(changes.getEvent().getElevationMeters());

			return athleteRepository.save(existing);
		});
	}

}
