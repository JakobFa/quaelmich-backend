package io.github.jakobfa.webtechproject.model;

import java.time.DayOfWeek;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Verfügbare Trainingsstunden pro Wochentag. Wird direkt in der Athleten-Tabelle gespeichert.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class WeeklyAvailability {

	@DecimalMin("0") @DecimalMax("12")
	private double monday;

	@DecimalMin("0") @DecimalMax("12")
	private double tuesday;

	@DecimalMin("0") @DecimalMax("12")
	private double wednesday;

	@DecimalMin("0") @DecimalMax("12")
	private double thursday;

	@DecimalMin("0") @DecimalMax("12")
	private double friday;

	@DecimalMin("0") @DecimalMax("12")
	private double saturday;

	@DecimalMin("0") @DecimalMax("12")
	private double sunday;

	public double hoursOn(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> monday;
			case TUESDAY -> tuesday;
			case WEDNESDAY -> wednesday;
			case THURSDAY -> thursday;
			case FRIDAY -> friday;
			case SATURDAY -> saturday;
			case SUNDAY -> sunday;
		};
	}

	public double totalHours() {
		return monday + tuesday + wednesday + thursday + friday + saturday + sunday;
	}

}
