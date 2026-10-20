package io.github.jakobfa.webtechproject.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ein strukturiertes Workout, bestehend aus mehreren Abschnitten.
 * Dauer und Trainingsstress werden aus den Abschnitten berechnet.
 */
@Getter
@AllArgsConstructor
public class Workout {

	private Long id;
	private String title;
	private WorkoutType type;
	private String description;
	private List<WorkoutStep> steps;

	public int getDurationInMinutes() {
		int seconds = steps.stream().mapToInt(WorkoutStep::durationSeconds).sum();
		return Math.round(seconds / 60f);
	}

	public int getTss() {
		double tss = steps.stream().mapToDouble(WorkoutStep::stressScore).sum();
		return (int) Math.round(tss);
	}

}
