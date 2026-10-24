package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Das Ziel-Event, auf das hintrainiert wird (z. B. ein Radmarathon).
 */
@Entity
@Table(name = "target_event")
@Getter
@Setter
@NoArgsConstructor
public class Event {

	@Id
	@GeneratedValue
	private Long id;

	@NotBlank
	private String name;

	@NotNull
	@FutureOrPresent
	private LocalDate date;

	@NotNull
	@Positive
	private Double distanceKm;

	@NotNull
	@PositiveOrZero
	private Integer elevationMeters;

	/** Zielzeit in Minuten, optional – zeigt, wie ambitioniert das Ziel ist (sonst gilt die Schätzung) */
	@Positive
	@Max(60 * 24 * 14)
	private Integer targetMinutes;

	/** Art des Events, optional – ohne Angabe wird der Schwerpunkt aus Dauer und Höhenmetern geschätzt */
	@Enumerated(EnumType.STRING)
	private EventType type;

}
