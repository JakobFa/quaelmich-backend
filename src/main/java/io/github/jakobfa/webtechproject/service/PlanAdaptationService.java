package io.github.jakobfa.webtechproject.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.github.jakobfa.webtechproject.model.Assessment;
import io.github.jakobfa.webtechproject.model.PlannedWorkout;
import io.github.jakobfa.webtechproject.model.Workout;
import io.github.jakobfa.webtechproject.model.WorkoutType;

/**
 * Passt den Plan an, wenn eine gefahrene Einheit deutlich anstrengender war als geplant:
 * Die nächste harte Einheit in den folgenden zwei Tagen wird leichter –
 * bei "härter" die nächstleichtere Variante, bei "deutlich härter" lockeres Ausrollen.
 *
 * Bewusst wird nichts nachgeholt, wenn eine Einheit leichter war oder ausgefallen ist.
 */
@Service
public class PlanAdaptationService {

	static final int DAYS_AHEAD = 2;
	static final int MAX_RECOVERY_MINUTES = 60;

	private static final Set<WorkoutType> EASY_TYPES = Set.of(WorkoutType.RECOVERY, WorkoutType.ENDURANCE,
			WorkoutType.LONG_RIDE);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN);

	private final WorkoutLibrary library;

	public PlanAdaptationService(WorkoutLibrary library) {
		this.library = library;
	}

	public Assessment assess(PlannedWorkout planned) {
		Workout workout = workoutOf(planned);
		return Assessment.of(workout.getTss(), planned.getActualTss(), workout.getDurationInMinutes(),
				planned.getActualDurationMinutes());
	}

	/**
	 * Prüft eine frisch zugeordnete Fahrt und passt bei Bedarf die folgenden Tage an.
	 *
	 * @param upcoming die geplanten Workouts der nächsten Tage (beliebig sortiert)
	 * @return das angepasste Workout, falls eines geändert wurde
	 */
	public Optional<PlannedWorkout> adaptAfter(PlannedWorkout ridden, List<PlannedWorkout> upcoming) {
		if (ridden.isAdaptationApplied() || ridden.getActivityId() == null) {
			return Optional.empty();
		}
		Assessment assessment = assess(ridden);
		if (!assessment.isHarder()) {
			return Optional.empty();
		}
		ridden.setAdaptationApplied(true);

		LocalDate until = ridden.getDate().plusDays(DAYS_AHEAD);
		return upcoming.stream()
				.filter(next -> next.getDate().isAfter(ridden.getDate()) && !next.getDate().isAfter(until))
				.filter(next -> !next.isCompleted() && next.getAdjustmentNote() == null)
				.filter(next -> !EASY_TYPES.contains(workoutOf(next).getType()))
				.min(Comparator.comparing(PlannedWorkout::getDate))
				.map(next -> {
					replace(next, ridden, assessment);
					return next;
				});
	}

	private void replace(PlannedWorkout next, PlannedWorkout ridden, Assessment assessment) {
		Workout original = workoutOf(next);
		Workout replacement = assessment == Assessment.MUCH_HARDER
				? null
				: library.easierVariant(original).orElse(null);

		next.setOriginalTitle(original.getTitle());
		if (replacement == null) {
			int minutes = Math.min(original.getDurationInMinutes(), MAX_RECOVERY_MINUTES);
			next.setWorkoutId(WorkoutLibrary.recovery(minutes).getId());
			next.setDurationMinutes(minutes);
		} else {
			next.setWorkoutId(replacement.getId());
			next.setDurationMinutes(null);
		}
		next.setAdjustmentNote("Angepasst: Am %s bist du %s gefahren statt %d TSS geplant – mehr Erholung."
				.formatted(DAY.format(ridden.getDate()), describeLoad(ridden), workoutOf(ridden).getTss()));
	}

	private static String describeLoad(PlannedWorkout ridden) {
		return ridden.getActualTss() != null
				? ridden.getActualTss() + " TSS"
				: ridden.getActualDurationMinutes() + " min";
	}

	private Workout workoutOf(PlannedWorkout planned) {
		return library.resolve(planned.getWorkoutId(), planned.getDurationMinutes()).orElseThrow();
	}

}
