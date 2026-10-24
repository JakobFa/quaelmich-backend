package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein weiteres Event im Kalender – gleich wichtig wie das erste. Der Plan arbeitet der Reihe nach auf alle hin.
 * Gleiche Angaben wie {@link Event}, damit Dauer und Schwerpunkt genauso geschätzt werden.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RaceEvent {

	@Id
	@GeneratedValue
	private Long id;

	@JsonIgnore
	@ManyToOne(optional = false)
	private Athlete athlete;

	@NotBlank
	@Size(max = 80)
	@Column(length = 80, nullable = false)
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

	@Enumerated(EnumType.STRING)
	private EventType type;

	@Enumerated(EnumType.STRING)
	private EventGoal goal;

	/** als {@link Event}, damit der Plan-Generator damit rechnen kann */
	public Event toEvent() {
		Event event = new Event();
		event.setName(name);
		event.setDate(date);
		event.setDistanceKm(distanceKm);
		event.setElevationMeters(elevationMeters);
		event.setType(type);
		event.setGoal(goal);
		return event;
	}

}
