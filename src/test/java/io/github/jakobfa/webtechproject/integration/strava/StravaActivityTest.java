package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StravaActivityTest {

	private static StravaActivity ride(String type, Integer weightedWatts, Boolean deviceWatts) {
		return new StravaActivity(1L, type, "2026-10-21T07:30:00Z", 3600, 30000.0, weightedWatts, deviceWatts, null);
	}

	private static StravaActivity commute(Double averageHeartRate) {
		return new StravaActivity(2L, "Ride", "2026-10-21T07:30:00Z", 1800, 9000.0, null, null, averageHeartRate);
	}

	@Test
	void estimatesTssFromHeartRateWithoutPowerMeter() {
		// 30 min bei 136 Schlägen, Schwellenpuls 170 → 0,5 h × 0,8² × 100 = 32
		var ride = commute(136.0).toRecordedRide(250, 170);

		assertThat(ride.tss()).isEqualTo(32);
		assertThat(ride.tssFromHeartRate()).isTrue();
	}

	@Test
	void powerBeatsHeartRate() {
		var withPower = new StravaActivity(3L, "Ride", "2026-10-21T07:30:00Z", 3600, 30000.0, 250, true, 150.0)
				.toRecordedRide(250, 170);

		assertThat(withPower.tss()).isEqualTo(100);
		assertThat(withPower.tssFromHeartRate()).isFalse();
	}

	@Test
	void noEstimateWithoutHeartRateOrThreshold() {
		assertThat(commute(null).toRecordedRide(250, 170).tss()).isNull();
		assertThat(commute(136.0).toRecordedRide(250, null).tss()).isNull();
		assertThat(commute(136.0).toRecordedRide(250, null).tssFromHeartRate()).isFalse();
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
