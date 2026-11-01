package io.github.jakobfa.webtechproject.integration.strava;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import io.github.jakobfa.webtechproject.integration.intervals.SecretCipher;
import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Die Verbindung eines Nutzers zu seinem Strava-Konto (OAuth).
 * Beide Tokens liegen nur verschlüsselt in der Datenbank (siehe {@link SecretCipher}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class StravaConnection {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(unique = true)
	private AppUser owner;

	private Long stravaAthleteId;

	/** Vorname laut Strava, nur zur Anzeige ("Verbunden als …") */
	private String athleteName;

	@Column(nullable = false, length = 1024)
	private String encryptedAccessToken;

	@Column(nullable = false, length = 1024)
	private String encryptedRefreshToken;

	/** Der Access-Token gilt nur ca. 6 Stunden und wird danach mit dem Refresh-Token erneuert */
	private Instant expiresAt;

	private Instant connectedAt;

	/** Von Strava erteilte Erlaubnisse, z. B. "read,activity:read_all,profile:read_all" */
	private String scope;

	/** Training von quaelmich in die Beschreibung bestätigter Strava-Fahrten schreiben (nur auf Wunsch) */
	@ColumnDefault("false")
	private boolean writeDescriptions;

	/** Im Player aufgezeichnete Fahrten nach dem Training automatisch zu Strava hochladen (nur auf Wunsch) */
	@ColumnDefault("false")
	private boolean uploadRides;

	public boolean canReadProfile() {
		return scope != null && scope.contains("profile:read_all");
	}

	/** Darf quaelmich Aktivitäten bearbeiten (Beschreibung)? */
	public boolean canWriteActivities() {
		return scope != null && scope.contains("activity:write");
	}

}
