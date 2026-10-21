package io.github.jakobfa.webtechproject.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein Nutzerkonto. Das Passwort wird nur als BCrypt-Hash gespeichert, nie im Klartext.
 * Die Tabelle heißt "app_user", weil "user" in Postgres ein reserviertes Wort ist.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	private Instant createdAt;

	/** Zeitpunkt der letzten Passwort-Änderung – davor ausgestellte Anmeldungen (JWTs) gelten nicht mehr */
	private Instant passwordChangedAt;

	public AppUser(String email, String passwordHash, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

}
