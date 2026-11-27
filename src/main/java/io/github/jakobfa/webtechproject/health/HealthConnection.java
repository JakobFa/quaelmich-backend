package io.github.jakobfa.webtechproject.health;

import java.time.Instant;

import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Upload-Schlüssel für Apple Health: Ein iPhone-Kurzbefehl schickt damit Schlaf und Bewegung an quaelmich.
 * Gespeichert wird nur der SHA-256-Hash; der Schlüssel selbst wird genau einmal angezeigt.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class HealthConnection {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	private AppUser owner;

	@Column(nullable = false, unique = true, length = 64)
	private String keyHash;

	/** die letzten vier Zeichen, damit man den Schlüssel wiedererkennt */
	@Column(nullable = false, length = 4)
	private String lastChars;

	/** ausdrückliche Einwilligung in die Verarbeitung von Gesundheitsdaten (Art. 9 DSGVO) */
	@Column(nullable = false)
	private Instant consentAt;

	private Instant lastUploadAt;

}
