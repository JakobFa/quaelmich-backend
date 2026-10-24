package io.github.jakobfa.webtechproject.integration.strava;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Sport;

class StravaActivityTest {

	private static StravaActivity ride(String type, Integer weightedWatts, Boolean deviceWatts) {
		return new StravaActivity(1L, type, "2026-10-21T07:30:00Z", 3600, 30000.0, weightedWatts, deviceWatts, null, null, null);
	}

	private static StravaActivity commute(Double averageHeartRate) {
		return new StravaActivity(2L, "Ride", "2026-10-21T07:30:00Z", 1800, 9000.0, null, null, averageHeartRate, null, null);
	}

	@Test
	void estimatesTssFromHeartRateWithoutPowerMeter() {
		// 30 min bei 136 Schlägen, Schwellenpuls 170, Ruhepuls 60 → 0,5 h × (76/110)² × 100 ≈ 24
		var ride = commute(136.0).toRecordedActivity(250, 170);

		assertThat(ride.tss()).isEqualTo(24);
		assertThat(ride.tssFromHeartRate()).isTrue();
	}

	@Test
	void powerBeatsHeartRate() {
		var withPower = new StravaActivity(3L, "Ride", "2026-10-21T07:30:00Z", 3600, 30000.0, 250, true, 150.0, null, null)
				.toRecordedActivity(250, 170);

		assertThat(withPower.tss()).isEqualTo(100);
		assertThat(withPower.tssFromHeartRate()).isFalse();
	}

	@Test
	void noEstimateWithoutHeartRateOrThreshold() {
		assertThat(commute(null).toRecordedActivity(250, 170).tss()).isNull();
		assertThat(commute(136.0).toRecordedActivity(250, null).tss()).isNull();
		assertThat(commute(136.0).toRecordedActivity(250, null).tssFromHeartRate()).isFalse();
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
	void allSportsCountButOnlyRidesAreRides() {
		assertThat(ride("Ride", null, null).sport()).isEqualTo(Sport.RIDE);
		assertThat(ride("GravelRide", null, null).sport()).isEqualTo(Sport.RIDE);
		assertThat(ride("EBikeRide", null, null).sport()).as("E-Bike ist kein Training aufs Rad-Ziel")
				.isEqualTo(Sport.OTHER);
		assertThat(ride("Run", null, null).isUsable()).isTrue();
		assertThat(ride("Run", null, null).sport()).isEqualTo(Sport.RUN);
	}

	@Test
	void runWithoutHeartRateIsEstimatedRideIsNot() {
		// eine Stunde Laufen ohne Puls ≈ 60 TSS, als Schätzung markiert
		var run = ride("Run", null, null).toRecordedActivity(250, 170);
		assertThat(run.tss()).isEqualTo(60);
		assertThat(run.tssFromDuration()).isTrue();

		var rideWithoutData = ride("Ride", null, null).toRecordedActivity(250, 170);
		assertThat(rideWithoutData.tss()).isNull();
		assertThat(rideWithoutData.tssFromDuration()).isFalse();
	}

	@Test
	void runWithHeartRateUsesHeartRateTss() {
		var run = new StravaActivity(4L, "Run", "2026-10-21T07:30:00Z", 3600, 10000.0, 250, true, 153.0, null, null)
				.toRecordedActivity(250, 170);

		assertThat(run.tss()).as("Watt zählen beim Laufen nicht, der Puls schon").isEqualTo(71);
		assertThat(run.tssFromHeartRate()).isTrue();
	}

	@Test
	void longEasyRideIsNotRatedAsHard() {
		// die Gravel-Runde vom 24.10.: 2:34 h, Ø 120 bpm, Schwellenpuls 170 → früher 127 TSS, jetzt ≈ 76
		var gravel = new StravaActivity(5L, "GravelRide", "2026-10-24T11:53:44Z", 9213, 62695.0, null, false, 119.9,
				154.0, false);
		assertThat(gravel.heartRateTss(170)).isBetween(70, 80);
	}

}
