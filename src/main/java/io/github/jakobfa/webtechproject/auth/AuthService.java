package io.github.jakobfa.webtechproject.auth;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.model.AppUser;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

@Service
public class AuthService {

	/** Wird geworfen, wenn die E-Mail-Adresse schon registriert ist. */
	public static class EmailAlreadyRegisteredException extends RuntimeException {
	}

	/** Wird geworfen, wenn ein nötiger Einladungscode fehlt, falsch oder schon benutzt ist. */
	public static class InvalidInvitationException extends RuntimeException {
	}

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final InvitationService invitations;

	public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
			InvitationService invitations) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.invitations = invitations;
	}

	public AppUser register(String email, String password) {
		return register(email, password, null);
	}

	/** Code und Konto in einer Transaktion: Schlägt das Konto fehl, bleibt der Code unbenutzt. */
	@Transactional
	public AppUser register(String email, String password, String inviteCode) {
		String normalized = normalize(email);
		if (userRepository.existsByEmail(normalized)) {
			throw new EmailAlreadyRegisteredException();
		}
		if (!invitations.redeemIfRequired(inviteCode)) {
			throw new InvalidInvitationException();
		}
		return userRepository.save(new AppUser(normalized, passwordEncoder.encode(password), clock.instant()));
	}

	/** Leer, wenn E-Mail unbekannt oder Passwort falsch – bewusst ohne zu verraten, was davon. */
	public Optional<AppUser> authenticate(String email, String password) {
		return userRepository.findByEmail(normalize(email))
				.filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
	}

	public Optional<AppUser> findById(Long id) {
		return userRepository.findById(id);
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
