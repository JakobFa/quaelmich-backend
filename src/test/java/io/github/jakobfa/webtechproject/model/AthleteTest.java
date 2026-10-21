package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AthleteTest {

	@Test
	void thresholdHeartRateIsNinetyPercentOfMaxUnlessKnown() {
		Athlete athlete = new Athlete();
		assertThat(athlete.getEffectiveThresholdHeartRate()).isNull();

		athlete.setMaxHeartRate(189);
		assertThat(athlete.getEffectiveThresholdHeartRate()).isEqualTo(170);

		athlete.setThresholdHeartRate(165); // aus einem eigenen Test
		assertThat(athlete.getEffectiveThresholdHeartRate()).isEqualTo(165);
	}

}
