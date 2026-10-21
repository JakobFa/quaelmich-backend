package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HeartRateTargetsTest {

	/** Strava-Standardzonen bei Maximalpuls 189 */
	private final HeartRateTargets targets = new HeartRateTargets(List.of(123, 153, 168, 183), 189);

	@Test
	void mapsPowerZonesToHeartRateZones() {
		assertThat(HeartRateTargets.zoneFor(50)).isEqualTo(1);
		assertThat(HeartRateTargets.zoneFor(65)).isEqualTo(2);
		assertThat(HeartRateTargets.zoneFor(88)).isEqualTo(3);
		assertThat(HeartRateTargets.zoneFor(100)).isEqualTo(4);
		assertThat(HeartRateTargets.zoneFor(120)).isEqualTo(5);
	}

	@Test
	void rangesFollowTheZoneBoundaries() {
		assertThat(targets.range(1)).isEqualTo(new HeartRateTargets.Range(1, 60, 122));
		assertThat(targets.range(2)).isEqualTo(new HeartRateTargets.Range(2, 123, 152));
		assertThat(targets.range(4)).isEqualTo(new HeartRateTargets.Range(4, 168, 182));
		assertThat(targets.range(5)).isEqualTo(new HeartRateTargets.Range(5, 183, 189));
	}

	@Test
	void rampsUseTheirAverageAndShortHardStepsHaveNoTarget() {
		assertThat(targets.forStep(new WorkoutStep(StepKind.WARMUP, 600, 50, 75)).zone()).isEqualTo(2);
		assertThat(targets.forStep(new WorkoutStep(StepKind.INTERVAL, 60, 150, 150))).as("1 min Vollgas").isNull();
		assertThat(targets.forStep(new WorkoutStep(StepKind.INTERVAL, 240, 115, 115)).zone()).isEqualTo(5);
	}

}
