package io.github.jakobfa.webtechproject.integration.intervals;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

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
 * Die Verbindung eines Nutzers zu seinem intervals.icu-Konto.
 * Der API-Schlüssel liegt nur verschlüsselt in der Datenbank (siehe {@link SecretCipher}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class IntervalsConnection {

	@Id
	@GeneratedValue
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(unique = true)
	private AppUser owner;

	@Column(nullable = false, length = 512)
	private String encryptedApiKey;

	/** intervals.icu-Athleten-ID, z. B. "i12345"; "0" steht für das Konto, dem der Schlüssel gehört */
	@Column(nullable = false)
	private String intervalsAthleteId;

	private Instant connectedAt;

	/** Workouts automatisch senden: nach "Plan erstellen" und jede Nacht für die nächsten 14 Tage */
	@Column(nullable = false)
	@ColumnDefault("true")
	private boolean autoSync = true;

	private Instant lastSyncedAt;

}
