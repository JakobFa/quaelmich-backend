package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.StepKind;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutStep;

/** Rahmen anpassen, Hauptteil behalten */
class WorkoutFitterTest {

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final Workout sweetSpot = library.findById(5L).orElseThrow(); // 3 × 12, 71 min

	/** Nur die Intervalle – die sollen nie angefasst werden */
	private static List<WorkoutStep> intervals(Workout workout) {
		return workout.getSteps().stream().filter(step -> step.kind() == StepKind.INTERVAL).toList();
	}

	@Test
	void moreTimeBecomesLongerWarmupAndEnduranceBeforeTheCooldown() {
		Workout fitted = WorkoutFitter.fit(sweetSpot, 105).orElseThrow();

		assertThat(fitted.getDurationInMinutes()).isEqualTo(105);
		assertThat(intervals(fitted)).isEqualTo(intervals(sweetSpot));
		assertThat(fitted.getSteps().getFirst().durationSeconds()).isEqualTo(15 * 60);
		WorkoutStep filler = fitted.getSteps().get(fitted.getSteps().size() - 2);
		assertThat(filler).isEqualTo(WorkoutStep.steady(StepKind.STEADY, 29 * 60, 65));
		assertThat(fitted.getSteps().getLast()).isEqualTo(sweetSpot.getSteps().getLast());
		assertThat(fitted.getTitle()).isEqualTo("Sweet Spot 3 × 12");
	}

	@Test
	void aFewMinutesMoreGoIntoWarmupAndCooldown() {
		Workout fitted = WorkoutFitter.fit(sweetSpot, 75).orElseThrow();

		assertThat(fitted.getSteps()).hasSameSizeAs(sweetSpot.getSteps());
		assertThat(fitted.getSteps().getFirst().durationSeconds()).isEqualTo(12 * 60);
		assertThat(fitted.getSteps().getLast().durationSeconds()).isEqualTo(12 * 60);
	}

	@Test
	void lessTimeDropsTheLastPauseThenShortensTheCooldown() {
		Workout fitted = WorkoutFitter.fit(sweetSpot, 63).orElseThrow();

		assertThat(fitted.getDurationInMinutes()).isEqualTo(63);
		assertThat(intervals(fitted)).isEqualTo(intervals(sweetSpot));
		assertThat(fitted.getSteps().get(fitted.getSteps().size() - 2).kind()).as("Intervall direkt vorm Ausfahren")
				.isEqualTo(StepKind.INTERVAL);
		assertThat(fitted.getSteps().getLast().durationSeconds()).isEqualTo(7 * 60);
		assertThat(fitted.getSteps().getFirst()).as("Aufwärmen bleibt").isEqualTo(sweetSpot.getSteps().getFirst());
	}

	@Test
	void cannotCutIntoTheMainSet() {
		assertThat(WorkoutFitter.minimumMinutes(sweetSpot)).isEqualTo(61);
		assertThat(WorkoutFitter.fit(sweetSpot, 61)).isPresent();
		assertThat(WorkoutFitter.fit(sweetSpot, 60)).isEmpty();
	}

	@Test
	void everyAdjustableLibraryWorkoutKeepsItsIntervalsForAnyLength() {
		for (Workout workout : library.findAll()) {
			if (!WorkoutLibrary.isLengthAdjustable(workout)) {
				continue;
			}
			for (int minutes = WorkoutFitter.minimumMinutes(workout); minutes <= 240; minutes += 5) {
				Workout fitted = WorkoutFitter.fit(workout, minutes).orElseThrow();
				assertThat(fitted.getDurationInMinutes()).as(workout.getTitle() + " " + minutes).isEqualTo(minutes);
				assertThat(intervals(fitted)).as(workout.getTitle()).isEqualTo(intervals(workout));
				assertThat(fitted.getSteps()).allSatisfy(step -> assertThat(step.durationSeconds()).isPositive());
			}
		}
	}

	@Test
	void testsOpenerAndEasyRidesAreNotAdjustedThisWay() {
		assertThat(WorkoutLibrary.isLengthAdjustable(library.findById(11L).orElseThrow())).as("FTP-Test").isFalse();
		assertThat(WorkoutLibrary.isLengthAdjustable(library.findById(28L).orElseThrow())).as("Opener").isFalse();
		assertThat(WorkoutLibrary.isLengthAdjustable(library.findById(2L).orElseThrow())).as("Grundlage").isFalse();
		assertThat(WorkoutLibrary.isLengthAdjustable(sweetSpot)).isTrue();
	}

	@Test
	void theLibraryResolvesAPlannedLength() {
		assertThat(library.resolve(5L, 105).orElseThrow().getDurationInMinutes()).isEqualTo(105);
		assertThat(library.resolve(11L, 105).orElseThrow().getDurationInMinutes()).as("FTP-Test bleibt")
				.isEqualTo(library.findById(11L).orElseThrow().getDurationInMinutes());
	}

}
