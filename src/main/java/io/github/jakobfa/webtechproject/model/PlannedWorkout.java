package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
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

	// ---------- Tatsächlich gefahren (aus intervals.icu, z. B. von Wahoo oder Garmin) ----------

	/** ID der Aktivität in intervals.icu; gesetzt = automatisch mit einer echten Fahrt verknüpft */
	private String activityId;

	private Integer actualDurationMinutes;

	private Double actualDistanceKm;

	/** Trainingslast laut intervals.icu (entspricht dem TSS) */
	private Integer actualTss;

	/** Vom Nutzer bestätigt, dass die erkannte Fahrt das geplante Training war (null = noch nicht gefragt) */
	private Boolean activityConfirmed;

	/** Wurde der Plan wegen dieser Fahrt schon angepasst? (verhindert doppelte Anpassung) */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean adaptationApplied;

	// ---------- Anpassung (wenn quaelmich dieses Workout nachträglich ersetzt hat) ----------

	/** Begründung, z. B. "Angepasst: Am Di, 20.10. bist du 140 TSS statt 64 gefahren." */
	private String adjustmentNote;

	/** ursprünglich geplantes Workout (Titel), falls ersetzt */
	private String originalTitle;

	public PlannedWorkout(LocalDate date, Workout workout, Integer durationMinutes, Phase phase) {
		this.date = date;
		this.workoutId = workout.getId();
		this.durationMinutes = durationMinutes;
		this.phase = phase;
	}


	/**
	 * Entfernt die verknüpfte Fahrt (z. B. nach dem Trennen von Strava).
	 * Selbst bestätigte Workouts bleiben erledigt, automatisch abgehakte werden wieder offen.
	 */
	public void clearActivity() {
		completed = completed && Boolean.TRUE.equals(activityConfirmed);
		activityId = null;
		actualDurationMinutes = null;
		actualDistanceKm = null;
		actualTss = null;
		activityConfirmed = null;
		adaptationApplied = false;
	}

}
