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

/** Schlaf und Bewegung eines Tages aus Apple Health. Fehlende Werte bleiben leer. */
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

	/** Herzfrequenzvariabilität (SDNN) in Millisekunden */
	private Integer hrvMs;

	private Instant updatedAt;

	public DailyHealth(AppUser owner, LocalDate date) {
		this.owner = owner;
		this.date = date;
	}

}
