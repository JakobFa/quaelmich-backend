package io.github.jakobfa.webtechproject.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * Einladungscodes: jeder Code gilt einmal und 14 Tage. Ist "nur mit Einladung" eingeschaltet
 * (REGISTRATION_INVITE_ONLY=true), braucht jede Registrierung einen offenen Code.
 */
@Service
public class InvitationService {

	static final Duration VALIDITY = Duration.ofDays(14);
	static final int MAX_OPEN_PER_USER = 3;

	/** Ohne leicht verwechselbare Zeichen (0/O, 1/I/L) */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	/** Wird geworfen, wenn jemand schon die erlaubte Zahl offener Codes hat. */
	public static class TooManyOpenInvitationsException extends RuntimeException {
	}

	/** Ein frisch erzeugter Code – nur in diesem Moment im Klartext sichtbar */
	public record CreatedInvitation(Invitation invitation, String code) {
	}

	private final InvitationRepository repository;
	private final AppUserRepository userRepository;
	private final Clock clock;
	private final boolean inviteOnly;
	private final SecureRandom random = new SecureRandom();

	public InvitationService(InvitationRepository repository, AppUserRepository userRepository, Clock clock,
			@Value("${app.registration.invite-only:false}") boolean inviteOnly) {
		this.repository = repository;
		this.userRepository = userRepository;
		this.clock = clock;
		this.inviteOnly = inviteOnly;
	}

	public boolean isInviteOnly() {
		return inviteOnly;
	}

	@Transactional
	public CreatedInvitation create(Long userId) {
		Instant now = clock.instant();
		long open = repository.findByCreatedByIdOrderByCreatedAtDesc(userId).stream()
				.filter(invitation -> invitation.isOpen(now)).count();
		if (open >= MAX_OPEN_PER_USER) {
			throw new TooManyOpenInvitationsException();
		}
		String code = newCode();
		Invitation invitation = repository.save(new Invitation(userRepository.getReferenceById(userId),
				PasswordResetService.hash(normalize(code)), code.substring(code.length() - 4), now,
				now.plus(VALIDITY)));
		return new CreatedInvitation(invitation, code);
	}

	public List<Invitation> listOwn(Long userId) {
		return repository.findByCreatedByIdOrderByCreatedAtDesc(userId);
	}

	/** Zieht einen offenen Code zurück. Leer, wenn er fremd ist oder nicht mehr offen. */
	@Transactional
	public Optional<Invitation> revoke(Long userId, Long invitationId) {
		Instant now = clock.instant();
		return repository.findById(invitationId)
				.filter(invitation -> invitation.getCreatedBy().getId().equals(userId))
				.filter(invitation -> invitation.isOpen(now))
				.map(invitation -> {
					invitation.setRevokedAt(now);
					return invitation;
				});
	}

	/**
	 * Für die Registrierung: Ist kein Code nötig, true. Sonst wird ein offener Code verbraucht –
	 * in derselben Transaktion wie das neue Konto, damit er nur einmal wirkt.
	 */
	@Transactional
	public boolean redeemIfRequired(String code) {
		if (!inviteOnly) {
			return true;
		}
		if (code == null || code.isBlank()) {
			return false;
		}
		Instant now = clock.instant();
		return repository.findByCodeHash(PasswordResetService.hash(normalize(code)))
				.filter(invitation -> invitation.isOpen(now))
				.map(invitation -> {
					invitation.setUsedAt(now);
					return true;
				})
				.orElse(false);
	}

	/** z. B. "QUAL-7KX4-M9PD" */
	private String newCode() {
		StringBuilder code = new StringBuilder("QUAL");
		for (int block = 0; block < 2; block++) {
			code.append('-');
			for (int i = 0; i < 4; i++) {
				code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
			}
		}
		return code.toString();
	}

	/** Groß-/Kleinschreibung, Leerzeichen und Bindestriche spielen beim Eintippen keine Rolle */
	static String normalize(String code) {
		return code.toUpperCase().replaceAll("[^A-Z0-9]", "");
	}

}
