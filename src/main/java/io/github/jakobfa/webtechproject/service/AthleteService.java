package io.github.jakobfa.webtechproject.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.TrainingMode;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;
import io.github.jakobfa.webtechproject.repository.AthleteRepository;

@Service
public class AthleteService {

	private final AthleteRepository athleteRepository;
	private final AppUserRepository userRepository;

	public AthleteService(AthleteRepository athleteRepository, AppUserRepository userRepository) {
		this.athleteRepository = athleteRepository;
		this.userRepository = userRepository;
	}

	public Optional<Athlete> findByOwner(Long userId) {
		return athleteRepository.findByOwnerId(userId);
	}

	/** Legt das Profil des Nutzers an oder aktualisiert es, falls es schon existiert. */
	@Transactional
	public Athlete saveForOwner(Long userId, Athlete changes) {
		return athleteRepository.findByOwnerId(userId)
				.map(existing -> update(existing, changes))
				.orElseGet(() -> create(userId, changes));
	}

	private Athlete create(Long userId, Athlete athlete) {
		// IDs vergibt die Datenbank, mitgeschickte Werte werden ignoriert
		athlete.setId(null);
		athlete.getEvent().setId(null);
		athlete.setOwner(userRepository.getReferenceById(userId));
		if (athlete.getTrainingMode() == null) {
			athlete.setTrainingMode(TrainingMode.POWER);
		}
		return athleteRepository.save(athlete);
	}

	private Athlete update(Athlete existing, Athlete changes) {
		existing.setFtp(changes.getFtp());
		existing.setThresholdHeartRate(changes.getThresholdHeartRate());
		existing.setMaxHeartRate(changes.getMaxHeartRate());
		existing.setHeartRateZoneStarts(changes.getHeartRateZoneStarts());
		existing.setTrainingMode(changes.getTrainingMode() == null ? TrainingMode.POWER : changes.getTrainingMode());
		existing.setWeightKg(changes.getWeightKg());
		existing.setLevel(changes.getLevel());
		existing.setAvailability(changes.getAvailability());

		var event = existing.getEvent();
		event.setName(changes.getEvent().getName());
		event.setDate(changes.getEvent().getDate());
		event.setDistanceKm(changes.getEvent().getDistanceKm());
		event.setElevationMeters(changes.getEvent().getElevationMeters());

		return athleteRepository.save(existing);
	}

}
