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
	void rampIsScoredWithNormalizedPower() {
		// Rampe 0 → 100 %: Mittelwert von Intensität⁴ ist 1/5, NP = 0,67, TSS = 0,67² × 100 (30-s-Mittel drückt etwas)
		Workout workout = workoutWith(WorkoutStep.ramp(StepKind.WARMUP, 3600, 0, 100));

		assertThat(workout.getTss()).isEqualTo(44);
	}

	@Test
	void durationAndTssAddUpOverAllSteps() {
		Workout workout = workoutWith(
				WorkoutStep.steady(StepKind.INTERVAL, 1800, 100),
				WorkoutStep.steady(StepKind.RECOVERY, 1800, 50));

		assertThat(workout.getDurationInMinutes()).isEqualTo(60);
		// NP: vierte Wurzel aus (1⁴ + 0,5⁴) / 2 = 0,854, TSS = 0,854² × 100 (nach dem einfachen Mittel wären es 63)
		assertThat(workout.getTss()).isEqualTo(73);
	}

	@Test
	void steadyWorkoutKeepsItsTss() {
		// ohne Wechsel in der Leistung ist die Normalized Power gleich dem Mittel
		Workout workout = workoutWith(WorkoutStep.steady(StepKind.STEADY, 3600, 70));

		assertThat(workout.getTss()).isEqualTo(49);
	}

	@Test
	void vo2maxIntervalsAreScoredLikeTheRideThatHitThem() {
		// VO2max 4 × 4: 10 min 55 %, 4 × (4 min 115 % + 4 min 55 %), 10 min 50 %. Strava rechnete 64 TSS bei genau getroffenen
		// Intervallen; geplant standen 53 (einfaches Mittel) – das galt als "härter als geplant"
		var steps = new java.util.ArrayList<WorkoutStep>();
		steps.add(WorkoutStep.steady(StepKind.WARMUP, 600, 55));
		for (int i = 0; i < 4; i++) {
			steps.add(WorkoutStep.steady(StepKind.INTERVAL, 240, 115));
			steps.add(WorkoutStep.steady(StepKind.RECOVERY, 240, 55));
		}
		steps.add(WorkoutStep.steady(StepKind.COOLDOWN, 600, 50));
		Workout workout = workoutWith(steps.toArray(new WorkoutStep[0]));

		assertThat(workout.getTss()).isBetween(62, 68);
	}

}
