package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StravaActivityTest {

	private static StravaActivity ride(String type, Integer weightedWatts, Boolean deviceWatts) {
		return new StravaActivity(1L, type, "2026-10-21T07:30:00Z", 3600, 30000.0, weightedWatts, deviceWatts);
	}

	@Test
	void calculatesTssFromWeightedPowerAndFtp() {
		// eine Stunde genau an der FTP = 100 TSS
		assertThat(ride("Ride", 250, true).tss(250)).isEqualTo(100);
		// eine Stunde bei 75 % FTP ≈ 56 TSS
		assertThat(ride("Ride", 188, true).tss(250)).isEqualTo(57);
	}

	@Test
	void noTssWithoutPowerMeterOrFtp() {
		assertThat(ride("Ride", null, null).tss(250)).isNull();
		assertThat(ride("Ride", 200, false).tss(250)).as("von Strava geschätzte Watt").isNull();
		assertThat(ride("Ride", 200, true).tss(null)).isNull();
	}

	@Test
	void onlyRidesWithoutMotorCount() {
		assertThat(ride("Ride", null, null).isUsableRide()).isTrue();
		assertThat(ride("GravelRide", null, null).isUsableRide()).isTrue();
		assertThat(ride("EBikeRide", null, null).isUsableRide()).isFalse();
		assertThat(ride("Run", null, null).isUsableRide()).isFalse();
	}

}
