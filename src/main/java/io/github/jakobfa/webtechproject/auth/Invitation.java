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
 * Ein Einladungscode, der genau einmal für eine Registrierung funktioniert.
 * Gespeichert wird nur der SHA-256-Hash; die letzten vier Zeichen helfen dem Ersteller beim Wiedererkennen.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Invitation {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private AppUser createdBy;

	@Column(nullable = false, unique = true, length = 64)
	private String codeHash;

	@Column(nullable = false, length = 4)
	private String lastChars;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	/** gesetzt, sobald sich jemand damit registriert hat */
	private Instant usedAt;

	/** das Konto, das mit diesem Code entstanden ist */
	@ManyToOne
	private AppUser usedBy;

	/** gesetzt, wenn der Ersteller den Code zurückgezogen hat */
	private Instant revokedAt;

	public Invitation(AppUser createdBy, String codeHash, String lastChars, Instant createdAt, Instant expiresAt) {
		this.createdBy = createdBy;
		this.codeHash = codeHash;
		this.lastChars = lastChars;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isOpen(Instant now) {
		return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
	}

}
