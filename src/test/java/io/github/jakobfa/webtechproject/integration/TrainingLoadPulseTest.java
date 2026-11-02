package io.github.jakobfa.webtechproject.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TrainingLoadPulseTest {

	@Test
	void steadyPulseHasNoCorrection() {
		Integer[] steady = new Integer[600];
		Arrays.fill(steady, 130);

		assertThat(TrainingLoad.pulseVariability(steady, 170).getAsDouble()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
	}

	@Test
	void unsteadyPulseRaisesTheLoadALittle() {
		Integer[] unsteady = new Integer[600];
		for (int i = 0; i < unsteady.length; i++) {
			unsteady[i] = i % 2 == 0 ? 110 : 150; // Durchschnitt 130
		}

		double factor = TrainingLoad.pulseVariability(unsteady, 170).getAsDouble();

		assertThat(factor).isGreaterThan(1.0).isLessThanOrEqualTo(TrainingLoad.MAX_PULSE_VARIABILITY);
	}

	@Test
	void tooLittleOrNoDataGivesNoFactor() {
		assertThat(TrainingLoad.pulseVariability(new Integer[10], 170)).isEmpty();
		assertThat(TrainingLoad.pulseVariability(new Integer[] {}, 170)).isEmpty();
		assertThat(TrainingLoad.pulseVariability(new Integer[600], null)).isEmpty();
	}

}
