package io.github.jakobfa.webtechproject.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.jakobfa.webtechproject.mail.MailService;
import io.github.jakobfa.webtechproject.repository.AppUserRepository;

/**
 * "Passwort vergessen": verschickt einen Einmal-Link (1 Stunde gültig) und setzt damit ein neues Passwort.
 * Danach sind alle bisherigen Anmeldungen ungültig.
 */
@Service
public class PasswordResetService {

	static final Duration VALIDITY = Duration.ofHours(1);

	/** Wird geworfen, wenn der Link unbekannt, abgelaufen oder schon benutzt ist. */
	public static class InvalidTokenException extends RuntimeException {
	}

	private final AppUserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(AppUserRepository userRepository, PasswordResetTokenRepository tokenRepository,
			PasswordEncoder passwordEncoder, MailService mailService, Clock clock) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.clock = clock;
	}

	/**
	 * Schickt den Link, falls es ein Konto mit dieser Adresse gibt. Verrät bewusst nicht, ob es eins gibt.
	 * Ältere, noch offene Links werden ungültig.
	 */
	@Transactional
	public void requestReset(String email) {
		userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
			Instant now = clock.instant();
			tokenRepository.findByUserIdAndUsedAtIsNull(user.getId()).forEach(old -> old.setUsedAt(now));

			String token = newToken();
			tokenRepository.save(new PasswordResetToken(user, hash(token), now.plus(VALIDITY)));

			String link = mailService.frontendUrl("/passwort-zuruecksetzen?token=" + token);
			mailService.send(user.getEmail(), "Dein neues Passwort für quaelmich", text(link), html(link));
		});
	}

	/** Setzt das neue Passwort; der Link ist danach verbraucht und alte Anmeldungen gelten nicht mehr. */
	@Transactional
	public void resetPassword(String token, String newPassword) {
		Instant now = clock.instant();
		PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(token))
				.filter(candidate -> candidate.isUsable(now))
				.orElseThrow(InvalidTokenException::new);
		resetToken.setUsedAt(now);
		var user = resetToken.getUser();
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		// auf Sekunden abrunden, weil JWTs "issued at" nur sekundengenau speichern
		user.setPasswordChangedAt(now.truncatedTo(ChronoUnit.SECONDS));
	}

	private String newToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String text(String link) {
		return """
				Hallo,

				du möchtest ein neues Passwort für quaelmich? Über diesen Link kannst du es setzen:

				%s

				Der Link gilt 1 Stunde und nur einmal. Wenn du das nicht warst, ignorier diese Mail einfach –
				dein Passwort bleibt dann, wie es ist.

				Quäl dich!
				quaelmich
				""".formatted(link);
	}

	private static String html(String link) {
		return """
				<div style="font-family:Arial,sans-serif;max-width:520px;color:#1b1b1f">
				  <h2 style="margin:0 0 16px">Neues Passwort für quaelmich</h2>
				  <p>Du möchtest ein neues Passwort? Klick auf den Knopf und setz es neu:</p>
				  <p style="margin:24px 0">
				    <a href="%1$s" style="background:#f06bb0;color:#1b1b1f;padding:12px 20px;
				       text-decoration:none;font-weight:bold;display:inline-block">Passwort neu setzen</a>
				  </p>
				  <p style="font-size:13px;color:#555">Der Link gilt 1 Stunde und nur einmal.<br>
				  Wenn du das nicht warst, ignorier diese Mail einfach – dein Passwort bleibt, wie es ist.</p>
				  <p style="font-size:12px;color:#888;word-break:break-all">%1$s</p>
				</div>
				""".formatted(link);
	}

}
