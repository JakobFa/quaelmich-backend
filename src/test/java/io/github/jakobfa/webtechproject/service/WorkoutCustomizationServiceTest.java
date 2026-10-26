package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService.Choice;
import io.github.jakobfa.webtechproject.service.WorkoutCustomizationService.Direction;

/** Die Regeln für leichter / härter / kürzer / länger – ohne Datenbank */
class WorkoutCustomizationServiceTest {

	private final WorkoutLibrary library = new WorkoutLibrary();
	private final WorkoutCustomizationService service = new WorkoutCustomizationService(library, null, null, (id, user) -> false);

	@Test
	void intervalWorkoutsMoveToTheNeighbouringVariant() {
		var sweetSpot = library.findById(5L).orElseThrow(); // 3 × 12

		assertThat(service.choiceFor(sweetSpot, Direction.EASIER)).contains(new Choice(14, null)); // 3 × 10
		assertThat(service.choiceFor(sweetSpot, Direction.HARDER)).contains(new Choice(16, null)); // 2 × 20
		assertThat(service.choiceFor(sweetSpot, Direction.SHORTER)).contains(new Choice(14, null));
		assertThat(service.choiceFor(sweetSpot, Direction.LONGER)).isPresent();
	}

	@Test
	void easiestVariantBecomesEnduranceAndHardestCannotGetHarder() {
		var easiestTempo = library.variant(io.github.jakobfa.webtechproject.model.WorkoutType.TEMPO, 0);
		var hardestSweetSpot = library.variant(io.github.jakobfa.webtechproject.model.WorkoutType.SWEET_SPOT, 99);

		assertThat(service.choiceFor(easiestTempo, Direction.EASIER).orElseThrow().workoutId()).isEqualTo(2);
		assertThat(service.choiceFor(hardestSweetSpot, Direction.HARDER)).isEmpty();
	}

	@Test
	void easyRidesGetShorterLongerOrOneStepHarder() {
		var endurance = WorkoutLibrary.endurance(90);

		assertThat(service.choiceFor(endurance, Direction.SHORTER)).contains(new Choice(2, 75));
		assertThat(service.choiceFor(endurance, Direction.LONGER)).contains(new Choice(2, 105));
		assertThat(service.choiceFor(endurance, Direction.EASIER)).contains(new Choice(1, 60));
		assertThat(service.choiceFor(endurance, Direction.HARDER)).contains(new Choice(27, 90));

		var longRide = WorkoutLibrary.longRide(240);
		assertThat(service.choiceFor(longRide, Direction.SHORTER)).contains(new Choice(3, 210));
		assertThat(service.choiceFor(WorkoutLibrary.endurance(60), Direction.SHORTER)).as("Mindestdauer").isEmpty();
	}

	@Test
	void recoveryCannotGetEasierAndFtpTestStaysATest() {
		assertThat(service.choiceFor(WorkoutLibrary.recovery(45), Direction.EASIER)).isEmpty();
		assertThat(service.choiceFor(WorkoutLibrary.recovery(45), Direction.HARDER)).contains(new Choice(2, 60));
		var ftpTest = library.findById(11L).orElseThrow();
		for (Direction direction : Direction.values()) {
			assertThat(service.choiceFor(ftpTest, direction)).isEmpty();
		}
	}

	private List<Workout> resolved(List<Choice> choices) {
		return choices.stream().map(c -> library.resolve(c.workoutId(), c.durationMinutes()).orElseThrow()).toList();
	}

	@Test
	void randomKeepsEasyRidesEasyAndFillsTheAvailableTime() {
		List<Choice> candidates = service.randomCandidates(WorkoutLibrary.endurance(90), 120);

		assertThat(candidates).contains(new Choice(1, 60), new Choice(2, 120), new Choice(3, 120), new Choice(27, 120));
		assertThat(resolved(candidates)).allSatisfy(workout -> {
			assertThat(WorkoutCustomizationService.intensityLevel(workout.getType())).as("locker").isZero();
			assertThat(workout.getDurationInMinutes()).isLessThanOrEqualTo(120);
		});
	}

	@Test
	void randomStaysOnTheSameIntensityAndNeverPicksTheSameOrATest() {
		Workout sweetSpot = library.findById(5L).orElseThrow();

		List<Workout> candidates = resolved(service.randomCandidates(sweetSpot, 75));

		assertThat(candidates).isNotEmpty().allSatisfy(workout -> {
			assertThat(workout.getType()).as("kein VO2max statt Sweet Spot")
					.isIn(WorkoutType.TEMPO, WorkoutType.SWEET_SPOT);
			assertThat(workout.getId()).isNotEqualTo(5L);
			assertThat(workout.getDurationInMinutes()).isBetween(45, 75);
		});
		assertThat(candidates.stream().map(Workout::getType).distinct().count()).as("mehr als nur eine Art")
				.isGreaterThan(1);

		var vo2max = library.findById(8L).orElseThrow();
		assertThat(resolved(service.randomCandidates(vo2max, 90))).isNotEmpty()
				.allSatisfy(workout -> assertThat(workout.getType())
						.isIn(WorkoutType.VO2MAX, WorkoutType.ANAEROBIC, WorkoutType.SPRINT));
	}

	@Test
	void randomMayRunALittleLongerWhenNothingElseFits() {
		Workout tempo = library.findById(12L).orElseThrow(); // 2 × 15, 60 min

		List<Workout> candidates = resolved(service.randomCandidates(tempo, 60));

		assertThat(candidates).isNotEmpty().allSatisfy(workout -> {
			assertThat(workout.getType()).isIn(WorkoutType.TEMPO, WorkoutType.SWEET_SPOT);
			assertThat(workout.getDurationInMinutes()).isLessThanOrEqualTo(66);
		});
	}

	@Test
	void randomFindsNothingWhenThereIsNoTime() {
		assertThat(service.randomCandidates(library.findById(5L).orElseThrow(), 20)).isEmpty();
		assertThat(service.randomCandidates(WorkoutLibrary.recovery(30), 30)).isEmpty();
	}

}
