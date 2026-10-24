package io.github.jakobfa.webtechproject.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.jakobfa.webtechproject.auth.Invitation;
import io.github.jakobfa.webtechproject.auth.InvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Einladungscodes zentral verwalten – nur für den Betreiber, per Terminal (scripts/invite.sh).
 * Ohne oder mit falschem X-Admin-Token antworten alle Routen mit 404, damit man nicht sieht, dass es sie gibt.
 */
@RestController
public class InternalInvitationController {

	public enum Status {
		OPEN, USED, EXPIRED, REVOKED
	}

	public record CreateRequest(@Size(max = 200) String note) {
	}

	/** code ist nur direkt nach dem Erstellen gesetzt – danach kennt ihn quaelmich selbst nicht mehr */
	public record InvitationResponse(Long id, String code, String lastChars, String note, Status status,
			Instant createdAt, Instant expiresAt, Instant usedAt, String usedBy) {
	}

	private final InvitationService invitations;
	private final AdminAccess access;
	private final Clock clock;

	public InternalInvitationController(InvitationService invitations, AdminAccess access, Clock clock) {
		this.invitations = invitations;
		this.access = access;
		this.clock = clock;
	}

	@PostMapping(path = "/internal/invitations")
	public ResponseEntity<InvitationResponse> create(@RequestHeader(name = "X-Admin-Token", required = false) String token,
			@Valid @RequestBody(required = false) CreateRequest request) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		var created = invitations.create(request == null ? null : request.note());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created.invitation(), created.code()));
	}

	@GetMapping(path = "/internal/invitations")
	public ResponseEntity<List<InvitationResponse>> list(
			@RequestHeader(name = "X-Admin-Token", required = false) String token) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(invitations.listAll().stream().map(i -> toResponse(i, null)).toList());
	}

	/** Offenen Code ungültig machen; 404 auch, wenn er schon benutzt oder abgelaufen ist */
	@DeleteMapping(path = "/internal/invitations/{id}")
	public ResponseEntity<InvitationResponse> revoke(@RequestHeader(name = "X-Admin-Token", required = false) String token,
			@PathVariable Long id) {
		if (!access.accepts(token)) {
			return ResponseEntity.notFound().build();
		}
		return invitations.revoke(id)
				.map(invitation -> ResponseEntity.ok(toResponse(invitation, null)))
				.orElse(ResponseEntity.notFound().build());
	}

	private InvitationResponse toResponse(Invitation invitation, String code) {
		Instant now = clock.instant();
		Status status = invitation.getUsedAt() != null ? Status.USED
				: invitation.getRevokedAt() != null ? Status.REVOKED
						: now.isBefore(invitation.getExpiresAt()) ? Status.OPEN : Status.EXPIRED;
		return new InvitationResponse(invitation.getId(), code, invitation.getLastChars(), invitation.getNote(), status,
				invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.getUsedAt(),
				invitation.getUsedBy() == null ? null : invitation.getUsedBy().getEmail());
	}

}
