package io.github.jakobfa.webtechproject.auth;

import java.time.Instant;

import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein Einmal-Link zum Zurücksetzen des Passworts. Gespeichert wird nur der SHA-256-Hash des Tokens –
 * wer die Datenbank liest, kann damit kein Passwort zurücksetzen.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private AppUser user;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	/** gesetzt, sobald der Link benutzt oder durch einen neueren ersetzt wurde */
	private Instant usedAt;

	public PasswordResetToken(AppUser user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

}
