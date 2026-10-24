package io.github.jakobfa.webtechproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jakobfa.webtechproject.model.Athlete;
import io.github.jakobfa.webtechproject.model.ExtraRide;
import io.github.jakobfa.webtechproject.model.Phase;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.SkipReason;

class FormServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
	private final WorkoutLibrary library = new WorkoutLibrary();
	private final FormService service = new FormService(null, null, null, library, null);

	/** Sweet Spot 3 × 12 = 67 TSS */
	private PlannedWorkout sweetSpot(LocalDate date) {
		return new PlannedWorkout(date, library.findById(5L).orElseThrow(), null, Phase.BASE);
	}

	@Test
	void fitnessBuildsSlowlyFatigueQuickly() {
		List<PlannedWorkout> done = new ArrayList<>();
		for (int i = 20; i >= 1; i--) {
			PlannedWorkout workout = sweetSpot(TODAY.minusDays(i));
			workout.setCompleted(true);
			done.add(workout);
		}

		var curve = service.curve(done, List.of(), TODAY, TODAY.plusDays(10));

		FormService.Day yesterday = curve.days().get(curve.days().size() - 12);
		assertThat(yesterday.fatigue()).isGreaterThan(yesterday.fitness());
		assertThat(yesterday.form()).isNegative(); // täglich hart trainiert → müde
		assertThat(curve.eventForm()).as("10 Tage Ruhe → frisch").isPositive();
	}

	@Test
	void pastCountsWhatWasRiddenFutureWhatIsPlanned() {
		PlannedWorkout ridden = sweetSpot(TODAY.minusDays(2));
		ridden.setActualTss(120);
		PlannedWorkout missed = sweetSpot(TODAY.minusDays(1)); // nicht gefahren → 0
		PlannedWorkout skippedTomorrow = sweetSpot(TODAY.plusDays(1));
		skippedTomorrow.setSkipReason(SkipReason.PAUSE);
		PlannedWorkout planned = sweetSpot(TODAY.plusDays(2));
		ExtraRide commute = new ExtraRide(new Athlete(), "x");
		commute.setDate(TODAY.minusDays(1));
		commute.setTss(30);

		var curve = service.curve(List.of(ridden, missed, skippedTomorrow, planned), List.of(commute), TODAY,
				TODAY.plusDays(3));

		assertThat(curve.days()).extracting(FormService.Day::tss).containsExactly(120, 30, 0, 0, 67, 0);
		assertThat(curve.days()).extracting(FormService.Day::planned)
				.containsExactly(false, false, false, true, true, true);
		assertThat(curve.today().date()).isEqualTo(TODAY);
	}

}
