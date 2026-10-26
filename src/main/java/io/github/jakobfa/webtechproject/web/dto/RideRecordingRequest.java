package io.github.jakobfa.webtechproject.web.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Eine Fahrt, die im Workout-Player mit dem Trainer aufgezeichnet wurde: ein Messpunkt je gefahrener Sekunde.
 *
 * @param startedAt wann auf "Los" gedrückt wurde
 * @param title     Name des Workouts, nur für den Dateinamen
 * @param samples   nach Sekunde aufsteigend; Lücken sind Pausen
 */
public record RideRecordingRequest(@NotNull Instant startedAt, @Size(max = 200) String title,
		@NotNull @Size(min = 1, max = RideRecordingRequest.MAX_SAMPLES) List<@Valid @NotNull Sample> samples) {

	/** 8 Stunden Fahrzeit reichen für jede Indoor-Einheit */
	public static final int MAX_SAMPLES = 8 * 3600;

	/**
	 * @param second Sekunden seit startedAt (Uhrzeit, Pausen mitgezählt)
	 * @param power  Watt, null = kein Wert
	 */
	public record Sample(@Min(0) @Max(24 * 3600) int second, @Min(0) @Max(3000) Integer power,
			@Min(0) @Max(255) Integer cadence, @Min(0) @Max(255) Integer heartRate) {
	}

}
