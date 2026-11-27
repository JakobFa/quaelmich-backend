package io.github.jakobfa.webtechproject.health;

import java.time.Instant;
import java.time.LocalDate;

import io.github.jakobfa.webtechproject.model.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Schlaf und Erholung eines Tages (aus intervals.icu oder dem Apple-Health-Kurzbefehl). Fehlende Werte bleiben leer. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "owner_id", "date" }))
@Getter
@Setter
@NoArgsConstructor
public class DailyHealth {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private AppUser owner;

	/** Schlaf zählt zum Morgen, an dem man aufwacht */
	@Column(nullable = false)
	private LocalDate date;

	private Integer sleepMinutes;

	private Integer steps;

	private Integer activeEnergyKcal;

	private Integer restingHeartRate;

	/** Schlafbewertung 0–100, so wie die Quelle sie liefert (z. B. Garmin, Oura) */
	private Integer sleepScore;

	/** Herzfrequenzvariabilität als rMSSD in Millisekunden (Garmin, Oura, Whoop …) */
	private Integer hrvMs;

	/** Herzfrequenzvariabilität als SDNN in Millisekunden (Apple Watch) */
	private Integer hrvSdnnMs;

	private Instant updatedAt;

	public DailyHealth(AppUser owner, LocalDate date) {
		this.owner = owner;
		this.date = date;
	}

}
