package io.github.jakobfa.webtechproject.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.CurrentUser;
import io.github.jakobfa.webtechproject.model.RaceEvent;
import io.github.jakobfa.webtechproject.service.RaceEventService;
import jakarta.validation.Valid;

/**
 * Weitere Events (zusätzlich zum ersten im Profil). Nach jeder Änderung wird der Plan ab heute neu verteilt;
 * Erledigtes und Rückmeldungen bleiben. 409 bei einem Datum, an dem schon ein Event ist.
 */
@RestController
public class RaceEventController {

	private final RaceEventService service;

	public RaceEventController(RaceEventService service) {
		this.service = service;
	}

	@GetMapping(path = "/me/events")
	public ResponseEntity<List<RaceEvent>> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(CurrentUser.id(jwt)).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PostMapping(path = "/me/events")
	public ResponseEntity<RaceEvent> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RaceEvent request) {
		try {
			return service.add(CurrentUser.id(jwt), request)
					.map(saved -> ResponseEntity.created(URI.create("/me/events/" + saved.getId())).body(saved))
					.orElse(ResponseEntity.notFound().build());
		} catch (RaceEventService.ConflictException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@PutMapping(path = "/me/events/{id}")
	public ResponseEntity<RaceEvent> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody RaceEvent request) {
		try {
			return service.update(CurrentUser.id(jwt), id, request).map(ResponseEntity::ok)
					.orElse(ResponseEntity.notFound().build());
		} catch (RaceEventService.ConflictException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@DeleteMapping(path = "/me/events/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.delete(CurrentUser.id(jwt), id) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

}
