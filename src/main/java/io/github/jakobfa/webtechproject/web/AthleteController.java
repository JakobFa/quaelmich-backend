package io.github.jakobfa.webtechproject.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.service.AthleteService;
import jakarta.validation.Valid;

@RestController
public class AthleteController {

	private final AthleteService athleteService;

	public AthleteController(AthleteService athleteService) {
		this.athleteService = athleteService;
	}

	@PostMapping(path = "/athletes")
	public ResponseEntity<Athlete> createAthlete(@Valid @RequestBody Athlete athlete) {
		Athlete created = athleteService.create(athlete);
		return ResponseEntity.created(URI.create("/athletes/" + created.getId())).body(created);
	}

	@GetMapping(path = "/athletes/{id}")
	public ResponseEntity<Athlete> getAthlete(@PathVariable Long id) {
		return athleteService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping(path = "/athletes/{id}")
	public ResponseEntity<Athlete> updateAthlete(@PathVariable Long id, @Valid @RequestBody Athlete athlete) {
		return athleteService.update(id, athlete)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

}
