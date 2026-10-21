package io.github.jakobfa.webtechproject.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

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


	@Test
	void heartRateZonesMustBeFourRisingValues() {
		Athlete athlete = new Athlete();
		assertThat(athlete.isHeartRateZoneStartsValid()).as("keine Zonen").isTrue();

		athlete.setHeartRateZoneStarts(List.of(123, 153, 168, 183));
		assertThat(athlete.isHeartRateZoneStartsValid()).isTrue();

		athlete.setHeartRateZoneStarts(List.of(123, 153, 150, 183));
		assertThat(athlete.isHeartRateZoneStartsValid()).as("nicht aufsteigend").isFalse();

		athlete.setHeartRateZoneStarts(List.of(123, 153, 168));
		assertThat(athlete.isHeartRateZoneStartsValid()).as("zu wenige").isFalse();

		athlete.setHeartRateZoneStarts(Arrays.asList(123, null, 168, 183));
		assertThat(athlete.isHeartRateZoneStartsValid()).as("Lücke").isFalse();
	}


	@Test
	void powerModeNeedsFtpHeartRateModeNeedsMaxHeartRateOrZones() {
		Athlete athlete = new Athlete();
		assertThat(athlete.isTrainingModeComplete()).as("Watt ohne FTP").isFalse();
		athlete.setFtp(250);
		assertThat(athlete.isTrainingModeComplete()).isTrue();

		athlete.setFtp(null);
		athlete.setTrainingMode(TrainingMode.HEART_RATE);
		assertThat(athlete.isTrainingModeComplete()).as("Puls ohne Maximalpuls").isFalse();
		athlete.setMaxHeartRate(189);
		assertThat(athlete.isTrainingModeComplete()).as("Puls ohne FTP ist ok").isTrue();
	}

	@Test
	void heartRateZonesComeFromMaxHeartRateOrOwnZones() {
		Athlete athlete = new Athlete();
		athlete.setMaxHeartRate(189);
		assertThat(athlete.getHeartRateTargets().zoneStarts()).containsExactly(123, 153, 168, 183);

		athlete.setHeartRateZoneStarts(List.of(120, 150, 165, 178));
		assertThat(athlete.getHeartRateTargets().zoneStarts()).containsExactly(120, 150, 165, 178);
		assertThat(athlete.getHeartRateTargets().maxHeartRate()).isEqualTo(189);
	}

}
