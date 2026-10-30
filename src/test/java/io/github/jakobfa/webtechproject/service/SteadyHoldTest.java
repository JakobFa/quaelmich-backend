package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.integration.ActivityStream;
import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.service.IntervalAccuracy.Hold;

/** Einschätzung lockerer Fahrten (ohne Intervalle): Zielbereich gehalten? */
class SteadyHoldTest {

	private static final int FTP = 250;
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	private static final Workout ENDURANCE = LIBRARY.findById(2L).orElseThrow(); // Grundlage, Ziel 68 % = 170 W
	private static final Workout RECOVERY = LIBRARY.findById(1L).orElseThrow();

	/** Fahrt aus Abschnitten (Minuten, Watt, [Puls]) mit etwas Rauschen */
	private static ActivityStream ride(int[]... parts) {
		List<Integer> time = new ArrayList<>();
		List<Integer> watts = new ArrayList<>();
		List<Integer> heartRate = new ArrayList<>();
		int second = 0;
		for (int[] part : parts) {
			for (int i = 0; i < part[0] * 60; i++) {
				time.add(second++);
				watts.add(part[1] + (i % 7 == 0 ? -8 : 2));
				heartRate.add(part.length > 2 ? part[2] : 130);
			}
		}
		return ActivityStream.of(time, watts, heartRate);
	}

	@Test
	void steadyInTheZoneIsPraised() {
		var result = IntervalAccuracy.holdByPower(ENDURANCE, ride(new int[] { 90, 170 }), FTP).orElseThrow();

		assertThat(result.hold()).isEqualTo(Hold.STEADY);
		assertThat(result.targetLow()).isEqualTo(150);
		assertThat(result.targetHigh()).isEqualTo(190);
		assertThat(result.average()).isBetween(165, 175);
		assertThat(result.percentInZone()).isGreaterThan(90);
		assertThat(result.tip()).contains("gleichmäßig");
	}

	@Test
	void ridingTheWholeEasyDayTooHardIsFlagged() {
		var result = IntervalAccuracy.holdByPower(ENDURANCE, ride(new int[] { 90, 215 }), FTP).orElseThrow();

		assertThat(result.hold()).isEqualTo(Hold.TOO_HARD);
		assertThat(result.percentAbove()).isGreaterThan(90);
	}

	@Test
	void oftenAboveTheZoneIsABitHard() {
		var result = IntervalAccuracy.holdByPower(ENDURANCE, ride(new int[] { 60, 168 }, new int[] { 30, 210 }), FTP)
				.orElseThrow();

		assertThat(result.hold()).isEqualTo(Hold.A_BIT_HARD);
		assertThat(result.percentAbove()).isBetween(25, 45);
	}

	@Test
	void anEnduranceRideWellBelowTheZoneIsVeryEasy() {
		var result = IntervalAccuracy.holdByPower(ENDURANCE, ride(new int[] { 90, 125 }), FTP).orElseThrow();

		assertThat(result.hold()).isEqualTo(Hold.VERY_EASY);
	}

	@Test
	void aRecoveryRideStaysSteadyEvenWhenGentle() {
		// Ausrollen soll locker sein – "zu locker" gibt es da nicht
		var result = IntervalAccuracy.holdByPower(RECOVERY, ride(new int[] { 45, 100 }), FTP).orElseThrow();

		assertThat(result.hold()).isEqualTo(Hold.STEADY);
	}

	@Test
	void worksByHeartRateToo() {
		var targets = new HeartRateTargets(List.of(120, 140, 160, 175), 185); // Zone 2: 120–139
		var inZone = IntervalAccuracy.holdByHeartRate(ENDURANCE, ride(new int[] { 90, 0, 130 }), targets)
				.orElseThrow();
		assertThat(inZone.hold()).isEqualTo(Hold.STEADY);
		assertThat(inZone.targetLow()).isEqualTo(120);

		var tooHard = IntervalAccuracy.holdByHeartRate(ENDURANCE, ride(new int[] { 90, 0, 155 }), targets)
				.orElseThrow();
		assertThat(tooHard.hold()).isEqualTo(Hold.TOO_HARD);
	}

	@Test
	void noAssessmentForIntervalOrTestWorkouts() {
		var sweetSpot = LIBRARY.findById(5L).orElseThrow();
		var rampTest = LIBRARY.findById(11L).orElseThrow();

		assertThat(IntervalAccuracy.holdByPower(sweetSpot, ride(new int[] { 60, 180 }), FTP)).isEmpty();
		assertThat(IntervalAccuracy.holdByPower(rampTest, ride(new int[] { 36, 180 }), FTP)).isEmpty();
	}

	@Test
	void shortRidesAreNotAssessed() {
		assertThat(IntervalAccuracy.holdByPower(ENDURANCE, ride(new int[] { 15, 170 }), FTP)).isEmpty();
	}

}
