package io.github.jakobfa.webtechproject.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Das Profil einer Radfahrerin bzw. eines Radfahrers mit Leistungsdaten, Ziel-Event und Verfügbarkeit.
 * Heißt bewusst nicht "User", weil "user" in Postgres ein reserviertes Wort ist.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Athlete {

	@Id
	@GeneratedValue
	private Long id;

	/** Functional Threshold Power in Watt */
	@NotNull
	@Min(50)
	@Max(600)
	private Integer ftp;

	@NotNull
	@DecimalMin("30")
	@DecimalMax("200")
	private Double weightKg;

	@NotNull
	@Enumerated(EnumType.STRING)
	private Level level;

	@Valid
	@NotNull
	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
	private Event event;

	@Valid
	@NotNull
	@Embedded
	private WeeklyAvailability availability;

}
