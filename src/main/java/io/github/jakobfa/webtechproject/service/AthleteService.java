package io.github.jakobfa.webtechproject.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.TrainingGoal;
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

	/** Wartet, bis kein anderer Import für dieses Profil mehr läuft (nur innerhalb einer Transaktion) */
	public void lockForImport(Long athleteId) {
		athleteRepository.lockById(athleteId);
	}

	/** "Ausblenden": diesen Vorschlag nicht wieder zeigen. Leer ohne Profil. */
	@Transactional
	public Optional<Athlete> dismissFtpSuggestion(Long userId) {
		return athleteRepository.findByOwnerId(userId).map(athlete -> {
			athlete.setDismissedSuggestedFtp(athlete.getSuggestedFtp());
			return athleteRepository.save(athlete);
		});
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
		if (athlete.getTrainingGoal() == null) {
			athlete.setTrainingGoal(TrainingGoal.EVENT);
		}
		if (!athlete.hasEventGoal()) {
			athlete.setEvent(null); // ohne Event-Ziel kein Event
		} else {
			athlete.getEvent().setId(null);
		}
		athlete.setOwner(userRepository.getReferenceById(userId));
		athlete.setSuggestedFtp(null); // setzt nur quaelmich
		athlete.setSuggestedFtpNote(null);
		athlete.setSuggestedFtpFromTest(null);
		athlete.setDismissedSuggestedFtp(null);
		if (athlete.getTrainingMode() == null) {
			athlete.setTrainingMode(TrainingMode.POWER);
		}
		return athleteRepository.save(athlete);
	}

	private Athlete update(Athlete existing, Athlete changes) {
		existing.setFtp(changes.getFtp());
		Integer suggested = existing.getSuggestedFtp();
		if (existing.getFtp() != null && suggested != null
				&& (Boolean.TRUE.equals(existing.getSuggestedFtpFromTest()) ? existing.getFtp().equals(suggested)
						: existing.getFtp() >= suggested)) {
			FtpSuggestions.clear(existing); // Vorschlag übernommen
		}
		existing.setThresholdHeartRate(changes.getThresholdHeartRate());
		existing.setMaxHeartRate(changes.getMaxHeartRate());
		existing.setHeartRateZoneStarts(changes.getHeartRateZoneStarts());
		existing.setPowerZoneStarts(changes.getPowerZoneStarts());
		existing.setTrainingMode(changes.getTrainingMode() == null ? TrainingMode.POWER : changes.getTrainingMode());
		existing.setWeightKg(changes.getWeightKg());
		existing.setLevel(changes.getLevel());
		existing.setAvailability(changes.getAvailability());

		existing.setTrainingGoal(changes.getTrainingGoal() == null ? TrainingGoal.EVENT : changes.getTrainingGoal());
		if (!existing.hasEventGoal()) {
			existing.setEvent(null); // Ziel ohne Termin: das alte Event fällt weg
		} else if (existing.getEvent() == null) {
			changes.getEvent().setId(null);
			existing.setEvent(changes.getEvent());
		} else {
			var event = existing.getEvent();
			event.setName(changes.getEvent().getName());
			event.setDate(changes.getEvent().getDate());
			event.setDistanceKm(changes.getEvent().getDistanceKm());
			event.setElevationMeters(changes.getEvent().getElevationMeters());
			event.setType(changes.getEvent().getType());
			event.setGoal(changes.getEvent().getGoal());
		}

		return athleteRepository.save(existing);
	}

}
