package io.github.jakobfa.webtechproject.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.integration.intervals.IntervalsAutoSyncService;
import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.repository.RaceEventRepository;
import io.github.jakobfa.webtechproject.service.AthleteService;
import io.github.jakobfa.webtechproject.service.TrainingPlanService;
import jakarta.validation.Valid;

/**
 * Weitere Events (zusätzlich zum ersten im Profil). Nach jeder Änderung wird der Plan ab heute neu verteilt;
 * Erledigtes und Rückmeldungen bleiben. 409 bei einem Datum, an dem schon ein Event ist.
 */
@RestController
public class RaceEventController {

	/** so viele weitere Events reichen für eine Saison */
	static final int MAX_EVENTS = 12;

	private final RaceEventRepository repository;
	private final AthleteService athleteService;
	private final TrainingPlanService planService;
	private final IntervalsAutoSyncService autoSync;

	public RaceEventController(RaceEventRepository repository, AthleteService athleteService,
			TrainingPlanService planService, IntervalsAutoSyncService autoSync) {
		this.repository = repository;
		this.athleteService = athleteService;
		this.planService = planService;
		this.autoSync = autoSync;
	}

	@GetMapping(path = "/me/events")
	public ResponseEntity<List<RaceEvent>> list(@AuthenticationPrincipal Jwt jwt) {
		return athleteService.findByOwner(CurrentUser.id(jwt))
				.map(athlete -> ResponseEntity.ok(repository.findByAthleteIdOrderByDate(athlete.getId())))
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping(path = "/me/events")
	@Transactional
	public ResponseEntity<RaceEvent> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RaceEvent request) {
		Long userId = CurrentUser.id(jwt);
		Optional<Athlete> athlete = athleteService.findByOwner(userId);
		if (athlete.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		List<RaceEvent> existing = repository.findByAthleteIdOrderByDate(athlete.get().getId());
		if (existing.size() >= MAX_EVENTS || dateTaken(athlete.get(), existing, request.getDate(), null)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		request.setId(null);
		request.setAthlete(athlete.get());
		RaceEvent saved = repository.save(request);
		replan(userId, athlete.get());
		return ResponseEntity.created(URI.create("/me/events/" + saved.getId())).body(saved);
	}

	@PutMapping(path = "/me/events/{id}")
	@Transactional
	public ResponseEntity<RaceEvent> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody RaceEvent request) {
		Long userId = CurrentUser.id(jwt);
		return owned(userId, id).map(event -> {
			Athlete athlete = event.getAthlete();
			if (dateTaken(athlete, repository.findByAthleteIdOrderByDate(athlete.getId()), request.getDate(), id)) {
				return ResponseEntity.status(HttpStatus.CONFLICT).<RaceEvent>build();
			}
			event.setName(request.getName());
			event.setDate(request.getDate());
			event.setDistanceKm(request.getDistanceKm());
			event.setElevationMeters(request.getElevationMeters());
			event.setType(request.getType());
			event.setGoal(request.getGoal());
			RaceEvent saved = repository.save(event);
			replan(userId, athlete);
			return ResponseEntity.ok(saved);
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping(path = "/me/events/{id}")
	@Transactional
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		Long userId = CurrentUser.id(jwt);
		return owned(userId, id).map(event -> {
			Athlete athlete = event.getAthlete();
			repository.delete(event);
			repository.flush();
			replan(userId, athlete);
			return ResponseEntity.noContent().<Void>build();
		}).orElse(ResponseEntity.notFound().build());
	}

	private Optional<RaceEvent> owned(Long userId, Long id) {
		return athleteService.findByOwner(userId)
				.flatMap(athlete -> repository.findById(id).filter(e -> e.getAthlete().getId().equals(athlete.getId())));
	}

	/** Pro Tag höchstens ein Event – auch nicht am Tag des ersten Events */
	private static boolean dateTaken(Athlete athlete, List<RaceEvent> existing, LocalDate date, Long ignoreId) {
		return date.equals(athlete.getEvent().getDate())
				|| existing.stream().anyMatch(e -> !e.getId().equals(ignoreId) && e.getDate().equals(date));
	}

	/** Plan ab heute neu verteilen (falls es einen gibt) und an intervals.icu senden */
	private void replan(Long userId, Athlete athlete) {
		if (planService.findByAthlete(athlete.getId()).isPresent()) {
			planService.generate(athlete.getId());
			autoSync.syncUserIfEnabled(userId);
		}
	}

}
