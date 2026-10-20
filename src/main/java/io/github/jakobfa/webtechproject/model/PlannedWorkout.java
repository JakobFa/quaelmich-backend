package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein Workout aus der Bibliothek, eingeplant an einem bestimmten Tag.
 * Speichert außerdem, ob es erledigt wurde und wie anstrengend es war.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlannedWorkout {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	private TrainingPlan plan;

	private LocalDate date;

	/** Verweis auf das Workout in der {@code WorkoutLibrary} */
	private Long workoutId;

	/** Abweichende Dauer bei Workouts mit variabler Länge (Grundlage, Regeneration, lange Ausfahrt) */
	private Integer durationMinutes;

	@Enumerated(EnumType.STRING)
	private Phase phase;

	private boolean completed;

	@Min(1)
	@Max(10)
	private Integer rating;

	public PlannedWorkout(LocalDate date, Workout workout, Integer durationMinutes, Phase phase) {
		this.date = date;
		this.workoutId = workout.getId();
		this.durationMinutes = durationMinutes;
		this.phase = phase;
	}

}
