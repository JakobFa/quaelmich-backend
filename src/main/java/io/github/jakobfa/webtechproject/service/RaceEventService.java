package io.github.jakobfa.webtechproject.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.repository.RaceEventRepository;

/**
 * Weitere Events (zusätzlich zum ersten im Profil). Nach jeder Änderung wird der Plan ab heute neu verteilt;
 * Erledigtes und Rückmeldungen bleiben. Pro Tag höchstens ein Event.
 */
@Service
public class RaceEventService {

	/** so viele weitere Events reichen für eine Saison */
	public static final int MAX_EVENTS = 12;

	/** Zu viele Events oder das Datum ist schon belegt */
	public static class ConflictException extends RuntimeException {
	}

	private final RaceEventRepository repository;
	private final AthleteService athletes;
	private final TrainingPlanService plans;
	private final IntervalsAutoSyncService autoSync;

	public RaceEventService(RaceEventRepository repository, AthleteService athletes, TrainingPlanService plans,
			IntervalsAutoSyncService autoSync) {
		this.repository = repository;
		this.athletes = athletes;
		this.plans = plans;
		this.autoSync = autoSync;
	}

	public Optional<List<RaceEvent>> list(Long userId) {
		return athletes.findByOwner(userId).map(athlete -> repository.findByAthleteIdOrderByDate(athlete.getId()));
	}

	@Transactional
	public Optional<RaceEvent> add(Long userId, RaceEvent request) {
		return athletes.findByOwner(userId).map(athlete -> {
			List<RaceEvent> existing = repository.findByAthleteIdOrderByDate(athlete.getId());
			if (existing.size() >= MAX_EVENTS || dateTaken(athlete, existing, request.getDate(), null)) {
				throw new ConflictException();
			}
			request.setId(null);
			request.setAthlete(athlete);
			RaceEvent saved = repository.save(request);
			replan(userId, athlete);
			return saved;
		});
	}

	@Transactional
	public Optional<RaceEvent> update(Long userId, Long id, RaceEvent request) {
		return owned(userId, id).map(event -> {
			Athlete athlete = event.getAthlete();
			if (dateTaken(athlete, repository.findByAthleteIdOrderByDate(athlete.getId()), request.getDate(), id)) {
				throw new ConflictException();
			}
			event.setName(request.getName());
			event.setDate(request.getDate());
			event.setDistanceKm(request.getDistanceKm());
			event.setElevationMeters(request.getElevationMeters());
			event.setType(request.getType());
			event.setGoal(request.getGoal());
			RaceEvent saved = repository.save(event);
			replan(userId, athlete);
			return saved;
		});
	}

	@Transactional
	public boolean delete(Long userId, Long id) {
		return owned(userId, id).map(event -> {
			Athlete athlete = event.getAthlete();
			repository.delete(event);
			repository.flush();
			replan(userId, athlete);
			return true;
		}).orElse(false);
	}

	private Optional<RaceEvent> owned(Long userId, Long id) {
		return athletes.findByOwner(userId)
				.flatMap(athlete -> repository.findById(id).filter(e -> e.getAthlete().getId().equals(athlete.getId())));
	}

	/** Pro Tag höchstens ein Event – auch nicht am Tag des ersten Events */
	private static boolean dateTaken(Athlete athlete, List<RaceEvent> existing, LocalDate date, Long ignoreId) {
		return (athlete.getEvent() != null && date.equals(athlete.getEvent().getDate()))
				|| existing.stream().anyMatch(e -> !e.getId().equals(ignoreId) && e.getDate().equals(date));
	}

	/** Plan ab heute neu verteilen (falls es einen gibt) und an intervals.icu senden */
	private void replan(Long userId, Athlete athlete) {
		if (plans.findByAthlete(athlete.getId()).isPresent()) {
			plans.generate(athlete.getId());
			autoSync.syncUserIfEnabled(userId);
		}
	}

}
