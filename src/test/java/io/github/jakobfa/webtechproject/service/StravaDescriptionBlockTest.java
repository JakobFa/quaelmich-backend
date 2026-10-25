package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;
import io.github.jakobfa.webtechproject.model.WorkoutType;

class StravaDescriptionBlockTest {

	private static final Workout SWEET_SPOT = new Workout(7L, "Sweet Spot 3 × 12", WorkoutType.SWEET_SPOT, "",
			List.of(WorkoutStep.ramp(StepKind.WARMUP, 900, 50, 75),
					WorkoutStep.steady(StepKind.INTERVAL, 720, 90), WorkoutStep.steady(StepKind.RECOVERY, 240, 55),
					WorkoutStep.steady(StepKind.INTERVAL, 720, 90), WorkoutStep.steady(StepKind.RECOVERY, 240, 55),
					WorkoutStep.steady(StepKind.INTERVAL, 720, 90),
					WorkoutStep.ramp(StepKind.COOLDOWN, 600, 60, 45)));

	private static StravaDescription.Content content(boolean heartRate, Integer accuracy, Integer rating) {
		return new StravaDescription.Content(SWEET_SPOT, Phase.BUILD, 6, heartRate, 78, 70, accuracy, false, rating);
	}

	@Test
	void groupsRepeatedIntervals() {
		assertThat(StravaDescription.sequence(SWEET_SPOT.getSteps(), false)).isEqualTo(
				"15 min einrollen · 3 × 12 min @ 90 % (dazwischen 4 min locker) · 10 min ausrollen");
	}

	@Test
	void blockShowsTrainingPlanVersusRideAccuracyAndRating() {
		String block = StravaDescription.block(content(false, 87, 7));

		assertThat(block).isEqualTo("""
				── quaelmich ──
				Sweet Spot 3 × 12
				Aufbauphase · Woche 6
				Ablauf (%% FTP): 15 min einrollen · 3 × 12 min @ 90 %% (dazwischen 4 min locker) · 10 min ausrollen
				Geplant 69 min / %d TSS · gefahren 78 min / 70 TSS → wie geplant
				Intervalle: 87 %% getroffen
				Gefühlt: 7/10 – hart
				Plan von quaelmich.com""".replace("%d", String.valueOf(SWEET_SPOT.getTss())).replace("%%", "%"));
	}

	@Test
	void heartRateModeShowsZonesAndLeavesOutMissingValues() {
		String block = StravaDescription.block(content(true, null, null));

		assertThat(block).contains("Ablauf: 15 min einrollen · 3 × 12 min @ Z3 (dazwischen 4 min locker)")
				.doesNotContain("Intervalle").doesNotContain("Gefühlt");
	}

	@Test
	void mergeReplacesTheOldBlockAndKeepsOwnText() {
		String first = StravaDescription.merge("Mit Hendrik am Müggelsee", "── quaelmich ──\nalt\nPlan von quaelmich.com");
		String second = StravaDescription.merge(first + "\nPS: Platten", "── quaelmich ──\nneu\nPlan von quaelmich.com");

		assertThat(second).isEqualTo("Mit Hendrik am Müggelsee\n\nPS: Platten\n\n── quaelmich ──\nneu\nPlan von quaelmich.com");
		assertThat(StravaDescription.merge(second, null)).isEqualTo("Mit Hendrik am Müggelsee\n\nPS: Platten");
		assertThat(StravaDescription.merge(null, "B")).isEqualTo("B");
	}

	@Test
	void groupsShortRepeatsWithTheirPause() {
		List<WorkoutStep> steps = List.of(WorkoutStep.steady(StepKind.INTERVAL, 30, 150),
				WorkoutStep.steady(StepKind.RECOVERY, 30, 50), WorkoutStep.steady(StepKind.INTERVAL, 30, 150),
				WorkoutStep.steady(StepKind.RECOVERY, 30, 50), WorkoutStep.steady(StepKind.COOLDOWN, 300, 50));

		assertThat(StravaDescription.sequence(steps, false))
				.isEqualTo("2 × (30 s @ 150 % + 30 s locker) · 5 min ausrollen");
	}

	@Test
	void formatsDurations() {
		assertThat(StravaDescription.duration(30)).isEqualTo("30 s");
		assertThat(StravaDescription.duration(90)).isEqualTo("1:30 min");
		assertThat(StravaDescription.duration(3600)).isEqualTo("60 min");
	}

}
