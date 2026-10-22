package io.github.jakobfa.webtechproject.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.InvitationService.TooManyOpenInvitationsException;

/** Freunde einladen: eigene Einladungscodes erstellen, ansehen und zurückziehen */
@RestController
public class InvitationController {

	public enum Status {
		OPEN, USED, EXPIRED, REVOKED
	}

	/** code ist nur direkt nach dem Erstellen gesetzt – danach kennt ihn quaelmich selbst nicht mehr */
	public record InvitationResponse(Long id, String code, String lastChars, Instant createdAt, Instant expiresAt,
			Status status) {
	}

	private final InvitationService invitationService;
	private final Clock clock;

	public InvitationController(InvitationService invitationService, Clock clock) {
		this.invitationService = invitationService;
		this.clock = clock;
	}

	@GetMapping(path = "/me/invitations")
	public List<InvitationResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return invitationService.listOwn(CurrentUser.id(jwt)).stream().map(i -> toResponse(i, null)).toList();
	}

	/** 409, wenn schon 3 offene Codes existieren */
	@PostMapping(path = "/me/invitations")
	public ResponseEntity<InvitationResponse> create(@AuthenticationPrincipal Jwt jwt) {
		try {
			var created = invitationService.create(CurrentUser.id(jwt));
			return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created.invitation(), created.code()));
		} catch (TooManyOpenInvitationsException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@DeleteMapping(path = "/me/invitations/{id}")
	public ResponseEntity<InvitationResponse> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return invitationService.revoke(CurrentUser.id(jwt), id)
				.map(invitation -> ResponseEntity.ok(toResponse(invitation, null)))
				.orElse(ResponseEntity.notFound().build());
	}

	private InvitationResponse toResponse(Invitation invitation, String code) {
		Instant now = clock.instant();
		Status status = invitation.getUsedAt() != null ? Status.USED
				: invitation.getRevokedAt() != null ? Status.REVOKED
						: now.isBefore(invitation.getExpiresAt()) ? Status.OPEN : Status.EXPIRED;
		return new InvitationResponse(invitation.getId(), code, invitation.getLastChars(), invitation.getCreatedAt(),
				invitation.getExpiresAt(), status);
	}

}
