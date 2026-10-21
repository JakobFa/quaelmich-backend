package io.github.jakobfa.webtechproject.model;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Eine gemachte Einheit ohne passendes Workout im Plan: eine Ausfahrt am Ruhetag, aber auch Laufen,
 * Schwimmen, Krafttraining … – zählt als zusätzliche Belastung.
 * Kommt wie die übrigen Fahrten aus intervals.icu.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ExtraRide {

	/** Ab dieser Trainingslast zählt eine Zusatzfahrt als zusätzliche Belastung */
	public static final int HARDER_TSS = 50;
	public static final int MUCH_HARDER_TSS = 90;

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JsonIgnore
	private Athlete athlete;

	/** ID der Aktivität in intervals.icu */
	private String activityId;

	private LocalDate date;

	private Integer durationMinutes;

	private Double distanceKm;

	private Integer tss;

	/** TSS nur aus dem Puls geschätzt (ohne Wattmesser) */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean tssFromHeartRate;

	/** TSS nur grob aus Dauer und Sportart geschätzt (ohne Puls) */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean tssFromDuration;

	/** Sportart; ältere Einträge ohne Angabe sind Radfahrten */
	@Enumerated(EnumType.STRING)
	private Sport sport;

	public Sport getSport() {
		return sport == null ? Sport.RIDE : sport;
	}

	/** Wurde der Plan wegen dieser Fahrt schon angepasst? */
	private boolean adaptationApplied;

	public ExtraRide(Athlete athlete, String activityId) {
		this.athlete = athlete;
		this.activityId = activityId;
	}

	/**
	 * Zusätzliche Belastung: HARDER ab 50 TSS, MUCH_HARDER ab 90 TSS, sonst null (lockere Runde).
	 * Ohne TSS wird grob über die Dauer geschätzt (75 bzw. 135 Minuten).
	 */
	public Assessment assessment() {
		if (tss != null) {
			return tss >= MUCH_HARDER_TSS ? Assessment.MUCH_HARDER : tss >= HARDER_TSS ? Assessment.HARDER : null;
		}
		if (durationMinutes != null) {
			return durationMinutes >= 135 ? Assessment.MUCH_HARDER
					: durationMinutes >= 75 ? Assessment.HARDER : null;
		}
		return null;
	}

}
