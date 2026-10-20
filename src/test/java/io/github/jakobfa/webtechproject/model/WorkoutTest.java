package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorkoutTest {

	private static Workout workoutWith(WorkoutStep... steps) {
		return new Workout(1L, "Test", WorkoutType.ENDURANCE, "", List.of(steps));
	}

	@Test
	void oneHourAtFtpIsHundredTss() {
		Workout workout = workoutWith(WorkoutStep.steady(StepKind.STEADY, 3600, 100));

		assertThat(workout.getTss()).isEqualTo(100);
	}

	@Test
	void oneHourAtHalfFtpIsTwentyFiveTss() {
		Workout workout = workoutWith(WorkoutStep.steady(StepKind.STEADY, 3600, 50));

		assertThat(workout.getTss()).isEqualTo(25);
	}

	@Test
	void rampUsesMeanOfSquaredIntensity() {
		// Rampe 0 → 100 %: Mittelwert von Intensität² ist 1/3
		Workout workout = workoutWith(WorkoutStep.ramp(StepKind.WARMUP, 3600, 0, 100));

		assertThat(workout.getTss()).isEqualTo(33);
	}

	@Test
	void durationAndTssAddUpOverAllSteps() {
		Workout workout = workoutWith(
				WorkoutStep.steady(StepKind.INTERVAL, 1800, 100),
				WorkoutStep.steady(StepKind.RECOVERY, 1800, 50));

		assertThat(workout.getDurationInMinutes()).isEqualTo(60);
		assertThat(workout.getTss()).isEqualTo(63); // 50 + 12,5
	}

}
