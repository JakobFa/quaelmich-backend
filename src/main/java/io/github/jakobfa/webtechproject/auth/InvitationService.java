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


/**
 * Einladungscodes vom Betreiber: jeder Code gilt einmal und 14 Tage. Ist "nur mit Einladung" eingeschaltet
 * (REGISTRATION_INVITE_ONLY=true), braucht jede Registrierung einen offenen Code.
 */
@Service
public class InvitationService {

	static final Duration VALIDITY = Duration.ofDays(14);

	/** Ohne leicht verwechselbare Zeichen (0/O, 1/I/L) */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	/** Ein frisch erzeugter Code – nur in diesem Moment im Klartext sichtbar */
	public record CreatedInvitation(Invitation invitation, String code) {
	}

	private final InvitationRepository repository;
	private final Clock clock;
	private final boolean inviteOnly;
	private final SecureRandom random = new SecureRandom();

	public InvitationService(InvitationRepository repository, Clock clock,
			@Value("${app.registration.invite-only:false}") boolean inviteOnly) {
		this.repository = repository;
		this.clock = clock;
		this.inviteOnly = inviteOnly;
	}

	public boolean isInviteOnly() {
		return inviteOnly;
	}

	/** @param note z. B. "für Lina" – nur zur eigenen Übersicht */
	@Transactional
	public CreatedInvitation create(String note) {
		Instant now = clock.instant();
		String code = newCode();
		Invitation invitation = repository.save(new Invitation(note == null || note.isBlank() ? null : note.trim(),
				PasswordResetService.hash(normalize(code)), code.substring(code.length() - 4), now,
				now.plus(VALIDITY)));
		return new CreatedInvitation(invitation, code);
	}

	public List<Invitation> listAll() {
		return repository.findAllByOrderByCreatedAtDesc();
	}

	/** Zieht einen offenen Code zurück. Leer, wenn es ihn nicht gibt oder er nicht mehr offen ist. */
	@Transactional
	public Optional<Invitation> revoke(Long invitationId) {
		Instant now = clock.instant();
		return repository.findById(invitationId)
				.filter(invitation -> invitation.isOpen(now))
				.map(invitation -> {
					invitation.setRevokedAt(now);
					return invitation;
				});
	}

	/**
	 * Für die Registrierung: Ist kein Code nötig, leer. Sonst wird ein offener Code verbraucht –
	 * in derselben Transaktion wie das neue Konto, damit er nur einmal wirkt.
	 *
	 * @throws AuthService.InvalidInvitationException wenn ein Code nötig ist und fehlt, falsch oder verbraucht ist
	 */
	@Transactional
	public Optional<Invitation> redeemIfRequired(String code) {
		if (!inviteOnly) {
			return Optional.empty();
		}
		if (code == null || code.isBlank()) {
			throw new AuthService.InvalidInvitationException();
		}
		Instant now = clock.instant();
		Invitation invitation = repository.findByCodeHash(PasswordResetService.hash(normalize(code)))
				.filter(candidate -> candidate.isOpen(now))
				.orElseThrow(AuthService.InvalidInvitationException::new);
		invitation.setUsedAt(now);
		return Optional.of(invitation);
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
