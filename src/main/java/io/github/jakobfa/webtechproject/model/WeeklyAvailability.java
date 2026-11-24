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

	/** Dieselben Stunden, nur an einem Wochentag anders */
	public WeeklyAvailability with(DayOfWeek day, double hours) {
		WeeklyAvailability copy = new WeeklyAvailability();
		for (DayOfWeek each : DayOfWeek.values()) {
			copy.set(each, each == day ? hours : hoursOn(each));
		}
		return copy;
	}

	/** Gleiche Stunden an allen Wochentagen */
	public boolean sameHoursAs(WeeklyAvailability other) {
		for (DayOfWeek day : DayOfWeek.values()) {
			if (Double.compare(hoursOn(day), other.hoursOn(day)) != 0) {
				return false;
			}
		}
		return true;
	}

	private void set(DayOfWeek day, double hours) {
		switch (day) {
			case MONDAY -> monday = hours;
			case TUESDAY -> tuesday = hours;
			case WEDNESDAY -> wednesday = hours;
			case THURSDAY -> thursday = hours;
			case FRIDAY -> friday = hours;
			case SATURDAY -> saturday = hours;
			case SUNDAY -> sunday = hours;
		}
	}

	public double totalHours() {
		return monday + tuesday + wednesday + thursday + friday + saturday + sunday;
	}

}
