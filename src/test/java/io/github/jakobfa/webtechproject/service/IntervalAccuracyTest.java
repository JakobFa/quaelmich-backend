package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.integration.ActivityStream;
import io.github.jakobfa.webtechproject.model.HeartRateTargets;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.service.IntervalAccuracy.Verdict;

class IntervalAccuracyTest {

	private static final int FTP = 250;
	private static final WorkoutLibrary LIBRARY = new WorkoutLibrary();
	/** Sweet Spot 3 × 12: je 12 min bei 90 % (225 W), 5 min Pause */
	private static final Workout SWEET_SPOT = LIBRARY.findById(5L).orElseThrow();

	/** Baut eine Fahrt aus Abschnitten (Minuten, Watt, Puls) – mit etwas "Rauschen" wie draußen */
	private static ActivityStream ride(int[]... parts) {
		List<Integer> time = new ArrayList<>();
		List<Integer> watts = new ArrayList<>();
		List<Integer> heartRate = new ArrayList<>();
		int second = 0;
		for (int[] part : parts) {
			for (int i = 0; i < part[0] * 60; i++) {
				time.add(second++);
				watts.add(part[1] + (i % 7 == 0 ? -15 : 3)); // kurze Einbrüche, z. B. Kurven
				heartRate.add(part.length > 2 ? part[2] : 120);
			}
		}
		return ActivityStream.of(time, watts, heartRate);
	}

	/** Längeres Einrollen als geplant, dann 3 Intervalle mit den angegebenen Watt */
	private static ActivityStream sweetSpotRide(int first, int second, int third) {
		return ride(new int[] { 18, 150 }, new int[] { 12, first }, new int[] { 5, 140 }, new int[] { 12, second },
				new int[] { 5, 140 }, new int[] { 12, third }, new int[] { 15, 130 });
	}

	@Test
	void findsTheIntervalsEvenWithALongerWarmUp() {
		var result = IntervalAccuracy.byPower(SWEET_SPOT, sweetSpotRide(226, 224, 227), FTP).orElseThrow();

		assertThat(result.blocks()).hasSize(3).allMatch(block -> block.verdict() == Verdict.ON_TARGET);
		assertThat(result.onTarget()).isEqualTo(3);
		assertThat(result.score()).isGreaterThanOrEqualTo(95);
		assertThat(result.uncertain()).isFalse();
		assertThat(result.tip()).isEqualTo("Sehr präzise gefahren.");
	}

	@Test
	void findsTheIntervalsInTheMiddleOfARideWithApproachAndWayBack() {
		// 30 min Anfahrt mit einem harten 12-min-Anstieg, Intervalle, 30 min zurück
		ActivityStream stream = ride(new int[] { 10, 170 }, new int[] { 12, 265 }, new int[] { 8, 170 },
				new int[] { 10, 150 }, new int[] { 12, 226 }, new int[] { 5, 140 }, new int[] { 12, 224 },
				new int[] { 5, 140 }, new int[] { 12, 227 }, new int[] { 30, 175 });

		var result = IntervalAccuracy.byPower(SWEET_SPOT, stream, FTP).orElseThrow();

		assertThat(result.blocks()).hasSize(3).allMatch(block -> block.verdict() == Verdict.ON_TARGET);
		assertThat(result.uncertain()).isFalse();
	}

	@Test
	void findsIntervalsStartedEarlierThanPlanned() {
		ActivityStream stream = ride(new int[] { 5, 150 }, new int[] { 12, 226 }, new int[] { 5, 140 },
				new int[] { 12, 224 }, new int[] { 5, 140 }, new int[] { 12, 227 }, new int[] { 10, 130 });

		var result = IntervalAccuracy.byPower(SWEET_SPOT, stream, FTP).orElseThrow();

		assertThat(result.blocks()).allMatch(block -> block.verdict() == Verdict.ON_TARGET);
	}

	@Test
	void tooHardEverywhere() {
		var result = IntervalAccuracy.byPower(SWEET_SPOT, sweetSpotRide(262, 260, 258), FTP).orElseThrow();

		assertThat(result.blocks()).allMatch(block -> block.verdict() == Verdict.HARD);
		assertThat(result.score()).isLessThan(80);
		assertThat(result.tip()).contains("über dem Ziel");
	}

	@Test
	void startedTooFastAndFaded() {
		var result = IntervalAccuracy.byPower(SWEET_SPOT, sweetSpotRide(250, 228, 195), FTP).orElseThrow();

		assertThat(result.blocks()).extracting(IntervalAccuracy.Block::verdict)
				.containsExactly(Verdict.HARD, Verdict.ON_TARGET, Verdict.EASY);
		assertThat(result.tip()).contains("Zu schnell angegangen");
	}

	@Test
	void allOutSprintsCanNeverBeTooHard() {
		Workout sprints = LIBRARY.findById(10L).orElseThrow(); // 6 × 15 s bei 170 %
		var result = IntervalAccuracy.byPower(sprints, sprintRide(), FTP).orElseThrow();

		assertThat(result.blocks()).allMatch(block -> block.verdict() == Verdict.ON_TARGET && block.targetHigh() == null);
	}

	private static ActivityStream sprintRide() {
		List<Integer> time = new ArrayList<>();
		List<Integer> watts = new ArrayList<>();
		int second = 0;
		for (int i = 0; i < 30 * 60; i++) {
			time.add(second++);
			watts.add(160);
		}
		for (int sprint = 0; sprint < 6; sprint++) {
			for (int i = 0; i < 15; i++) {
				time.add(second++);
				watts.add(600);
			}
			for (int i = 0; i < 285; i++) {
				time.add(second++);
				watts.add(140);
			}
		}
		return ActivityStream.of(time, watts, null);
	}

	@Test
	void heartRateModeJudgesTheEndOfLongBlocks() {
		Workout threshold = LIBRARY.findById(6L).orElseThrow(); // Schwelle 2 × 20
		HeartRateTargets targets = new HeartRateTargets(List.of(123, 153, 168, 183), 189);
		ActivityStream stream = ride(new int[] { 12, 150, 120 }, new int[] { 20, 240, 175 },
				new int[] { 8, 140, 130 }, new int[] { 20, 240, 176 }, new int[] { 10, 130, 115 });

		var result = IntervalAccuracy.byHeartRate(threshold, stream, targets).orElseThrow();

		assertThat(result.basis()).isEqualTo(IntervalAccuracy.Basis.HEART_RATE);
		assertThat(result.blocks()).hasSize(2).allMatch(block -> block.verdict() == Verdict.ON_TARGET);
	}

	@Test
	void notEvaluableWithoutIntervalsPowerOrEnoughRide() {
		Workout endurance = WorkoutLibrary.endurance(90);
		assertThat(IntervalAccuracy.byPower(endurance, sweetSpotRide(225, 225, 225), FTP)).isEmpty();
		assertThat(IntervalAccuracy.byPower(SWEET_SPOT, ride(new int[] { 20, 225 }), FTP))
				.as("zu kurz für drei Intervalle").isEmpty();
		assertThat(IntervalAccuracy.byPower(SWEET_SPOT, ActivityStream.of(List.of(0, 1), null, List.of(120, 121)),
				FTP)).as("ohne Watt").isEmpty();
	}

}
