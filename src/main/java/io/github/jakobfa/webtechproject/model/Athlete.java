package io.github.jakobfa.webtechproject.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

	/** Der Nutzer, dem dieses Profil gehört (wird nie ans Frontend geschickt) */
	@JsonIgnore
	@OneToOne
	@JoinColumn(unique = true)
	private AppUser owner;

	/** Functional Threshold Power in Watt */
	@NotNull
	@Min(50)
	@Max(600)
	private Integer ftp;

	/** Maximalpuls in Schlägen pro Minute, optional – Grundlage für den Puls-TSS */
	@Min(120)
	@Max(230)
	private Integer maxHeartRate;

	/**
	 * Schwellenpuls (LTHR), optional – nur nötig, wenn er aus einem Test genau bekannt ist.
	 * Sonst gilt 90 % des Maximalpulses.
	 */
	@Min(100)
	@Max(220)
	private Integer thresholdHeartRate;

	/** Schwellenpuls ≈ 90 % des Maximalpulses (Faustregel) */
	public static final double THRESHOLD_SHARE_OF_MAX = 0.9;

	/** Eingetragener Schwellenpuls, sonst 90 % des Maximalpulses; null, wenn beides fehlt */
	@JsonIgnore
	public Integer getEffectiveThresholdHeartRate() {
		if (thresholdHeartRate != null) {
			return thresholdHeartRate;
		}
		return maxHeartRate == null ? null : (int) Math.round(maxHeartRate * THRESHOLD_SHARE_OF_MAX);
	}

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
